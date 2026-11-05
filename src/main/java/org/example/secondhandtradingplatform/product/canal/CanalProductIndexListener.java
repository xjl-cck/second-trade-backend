package org.example.secondhandtradingplatform.product.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import com.alibaba.otter.canal.protocol.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import jakarta.annotation.PreDestroy;
import org.example.secondhandtradingplatform.product.service.impl.ProductSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 订阅 MySQL binlog（经 canal-server），把 product 表的行变更同步到 Elasticsearch。
 *
 * <p>链路：MySQL binlog → canal-server（伪装从库）→ 本类的 TCP 客户端 → {@link ProductSearchIndex}。
 * 和业务代码里的同步写入、以及定时对账任务并存：这里负责实时和"所有写路径"，
 * 定时任务负责兜底（canal 挂了、位点丢了、ES 抖了，最终都能被窗口重放纠正）。</p>
 *
 * <p>处理完一批就 ack，即使个别文档写 ES 失败也不阻塞——搜索只是派生数据，
 * 丢的那条由 {@code ProductSearchSyncJob} 在窗口内补回来，比卡住 binlog 消费更划算。</p>
 */
@Component
public class CanalProductIndexListener {

    private static final Logger log = LoggerFactory.getLogger(CanalProductIndexListener.class);
    private static final long IDLE_MILLIS = 200L;
    private static final long MAX_BACKOFF_SECONDS = 30L;

    private final ProductSearchIndex productSearchIndex;
    private final CanalProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread worker;

    public CanalProductIndexListener(ProductSearchIndex productSearchIndex, CanalProperties properties) {
        this.productSearchIndex = productSearchIndex;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Canal listener disabled, product index sync relies on the scheduled job only");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::run, "canal-product-index");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void run() {
        long backoffSeconds = 1L;
        while (running.get()) {
            CanalConnector connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(properties.getHost(), properties.getPort()),
                    properties.getDestination(), "", "");
            try {
                connector.connect();
                connector.subscribe(properties.getFilter());
                // 从上次 ack 的位置继续，避免把历史 binlog 全量重放一遍
                connector.rollback();
                backoffSeconds = 1L;
                log.info("Canal connected, {}:{}, destination={}, filter={}",
                        properties.getHost(), properties.getPort(),
                        properties.getDestination(), properties.getFilter());
                consume(connector);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                if (running.get()) {
                    log.warn("Canal listener disconnected, retry in {}s: {}",
                            backoffSeconds, exception.getMessage());
                }
            } finally {
                disconnectQuietly(connector);
            }
            if (!sleepSeconds(backoffSeconds)) {
                return;
            }
            backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_SECONDS);
        }
    }

    private void consume(CanalConnector connector) throws InterruptedException {
        while (running.get()) {
            Message message = connector.getWithoutAck(properties.getBatchSize());
            long batchId = message.getId();
            if (batchId == -1 || message.getEntries().isEmpty()) {
                Thread.sleep(IDLE_MILLIS);
                continue;
            }
            Counts counts = new Counts();
            for (Entry entry : message.getEntries()) {
                apply(entry, counts);
            }
            connector.ack(batchId);
            if (counts.failed > 0) {
                log.warn("Canal batch applied with failures, indexed={}, removed={}, failed={}",
                        counts.indexed, counts.removed, counts.failed);
            } else if (counts.indexed + counts.removed > 0) {
                log.info("Canal batch applied, indexed={}, removed={}", counts.indexed, counts.removed);
            }
        }
    }

    /**
     * 处理单条 binlog entry，测试里直接调用。
     */
    void apply(Entry entry, Counts counts) {
        if (entry.getEntryType() != EntryType.ROWDATA) {
            return;
        }
        RowChange rowChange;
        try {
            rowChange = RowChange.parseFrom(entry.getStoreValue());
        } catch (InvalidProtocolBufferException exception) {
            log.warn("Canal row change parse failed, table={}", entry.getHeader().getTableName());
            counts.failed++;
            return;
        }
        boolean delete = rowChange.getEventType() == EventType.DELETE;
        for (RowData rowData : rowChange.getRowDatasList()) {
            Map<String, String> columns = toMap(delete
                    ? rowData.getBeforeColumnsList()
                    : rowData.getAfterColumnsList());
            String id = columns.get("id");
            if (id == null) {
                counts.failed++;
                continue;
            }
            try {
                Long productId = Long.valueOf(id);
                if (delete || "1".equals(columns.get("is_deleted"))) {
                    if (productSearchIndex.delete(productId)) {
                        counts.removed++;
                    } else {
                        counts.failed++;
                    }
                    continue;
                }
                if (productSearchIndex.index(productId,
                        columns.get("title"), columns.get("description"), columns.get("location"))) {
                    counts.indexed++;
                } else {
                    counts.failed++;
                }
            } catch (NumberFormatException exception) {
                log.warn("Canal row has invalid product id={}", id);
                counts.failed++;
            }
        }
    }

    private Map<String, String> toMap(List<Column> columns) {
        Map<String, String> values = new HashMap<>(columns.size());
        for (Column column : columns) {
            values.put(column.getName(), column.getIsNull() ? null : column.getValue());
        }
        return values;
    }

    private boolean sleepSeconds(long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void disconnectQuietly(CanalConnector connector) {
        try {
            connector.disconnect();
        } catch (RuntimeException ignored) {
            // 断线时 disconnect 本身也可能抛，忽略即可
        }
    }

    /**
     * 一批 binlog 的处理结果，用于日志和测试断言。
     */
    static class Counts {
        int indexed;
        int removed;
        int failed;
    }
}
