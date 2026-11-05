package org.example.secondhandtradingplatform.product.job;

import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.product.service.impl.ProductSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品索引定时对账：按 update_time 滑动窗口把 MySQL 里的变更补写进 Elasticsearch。
 *
 * <p>为什么需要：业务代码里"写完库顺手写 ES"的同步双写不参与 MySQL 事务，
 * ES 抖动、事务回滚、进程崩溃、逻辑删除这些情况都会让索引和库对不上，而且失败是静默的。
 * 定时任务不追求实时（业务侧仍然会立即写一次），只负责把窗口内所有变更重放一遍，让最终一致有兜底。</p>
 *
 * <p>窗口取"最近 N 分钟"而不是水位线，是为了躲开长事务：一个 update 语句执行时 update_time 就已经定下，
 * 但它可能几十秒后才提交，按水位线推进就会漏掉；固定回看一段窗口则每次都会重新扫到。
 * 代价是窗口内的商品每次都会被重复写一遍，对当前数据量可以接受，量大时调小 window-seconds 即可。</p>
 */
@Component
public class ProductSearchSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchSyncJob.class);
    private static final int MAX_ROUNDS = 20;

    private final ProductSearchIndex productSearchIndex;
    private final ProductMapper productMapper;
    private final boolean enabled;
    private final long windowSeconds;
    private final int batchSize;

    public ProductSearchSyncJob(ProductSearchIndex productSearchIndex,
                                ProductMapper productMapper,
                                @Value("${app.search.sync.enabled:true}") boolean enabled,
                                @Value("${app.search.sync.window-seconds:600}") long windowSeconds,
                                @Value("${app.search.sync.batch-size:500}") int batchSize) {
        this.productSearchIndex = productSearchIndex;
        this.productMapper = productMapper;
        this.enabled = enabled;
        this.windowSeconds = windowSeconds;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(
            fixedDelayString = "${app.search.sync.interval-ms:60000}",
            initialDelayString = "${app.search.sync.initial-delay-ms:45000}"
    )
    public void sync() {
        if (!enabled || !productSearchIndex.isAvailable()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusSeconds(windowSeconds);
        try {
            SyncResult changed = syncChanged(since, now);
            SyncResult removed = syncDeleted(since, now);
            int succeeded = changed.succeeded() + removed.succeeded();
            int failed = changed.failed() + removed.failed();
            if (failed > 0) {
                // 写失败必须留下痕迹，否则 ES 挂掉时同步会静默停摆，和没有这个任务没区别
                log.warn("Product search index sync partially failed, succeeded={}, failed={}, windowSeconds={}",
                        succeeded, failed, windowSeconds);
            } else if (succeeded > 0) {
                log.info("Product search index synced, upserted={}, removed={}, windowSeconds={}",
                        changed.succeeded(), removed.succeeded(), windowSeconds);
            }
        } catch (RuntimeException exception) {
            // 单轮失败不影响下一轮，窗口是重叠的，下一次自然会把没写进去的补上
            log.error("Product search index sync failed, windowSeconds={}", windowSeconds, exception);
        }
    }

    private SyncResult syncChanged(LocalDateTime since, LocalDateTime until) {
        LocalDateTime cursorTime = since.minusSeconds(1);
        long cursorId = 0L;
        int succeeded = 0;
        int failed = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Product> batch = productMapper.selectChangedAfter(since, until, cursorTime, cursorId, batchSize);
            if (batch.isEmpty()) {
                return new SyncResult(succeeded, failed);
            }
            for (Product product : batch) {
                if (productSearchIndex.index(product)) {
                    succeeded++;
                } else {
                    failed++;
                }
            }
            Product last = batch.get(batch.size() - 1);
            cursorTime = last.getUpdateTime();
            cursorId = last.getId();
            if (batch.size() < batchSize) {
                return new SyncResult(succeeded, failed);
            }
        }
        log.warn("Product search sync hit the round limit, windowSeconds={}, batchSize={}, "
                + "consider lowering window-seconds or interval-ms", windowSeconds, batchSize);
        return new SyncResult(succeeded, failed);
    }

    private SyncResult syncDeleted(LocalDateTime since, LocalDateTime until) {
        LocalDateTime cursorTime = since.minusSeconds(1);
        long cursorId = 0L;
        int succeeded = 0;
        int failed = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Product> batch = productMapper.selectDeletedAfter(since, until, cursorTime, cursorId, batchSize);
            if (batch.isEmpty()) {
                return new SyncResult(succeeded, failed);
            }
            for (Product product : batch) {
                if (productSearchIndex.delete(product.getId())) {
                    succeeded++;
                } else {
                    failed++;
                }
            }
            Product last = batch.get(batch.size() - 1);
            cursorTime = last.getUpdateTime();
            cursorId = last.getId();
            if (batch.size() < batchSize) {
                return new SyncResult(succeeded, failed);
            }
        }
        log.warn("Product search delete sync hit the round limit, windowSeconds={}, batchSize={}",
                windowSeconds, batchSize);
        return new SyncResult(succeeded, failed);
    }

    private record SyncResult(int succeeded, int failed) {
    }
}
