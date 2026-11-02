package org.example.secondhandtradingplatform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 事务消息出站服务。
 *
 * <p>事件和业务数据在同一个本地事务里写入 {@code outbox_event}：业务提交则事件一定在表里，
 * 业务回滚则事件一起回滚。提交完成后再异步触发投递（{@link OutboxRelay}），
 * 所以“事务提交成功但消息没发出去”的窗口不会丢事件，最坏情况只是延迟投递。</p>
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final OutboxRelay outboxRelay;

    public OutboxService(OutboxEventMapper outboxEventMapper,
                         ObjectMapper objectMapper,
                         OutboxRelay outboxRelay) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        this.outboxRelay = outboxRelay;
    }

    /**
     * 记录一条待投递事件，必须在业务事务内调用，才能与业务数据同时提交。
     */
    public void record(String exchange, String routingKey, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(payload.getClass().getName());
        event.setExchangeName(exchange);
        event.setRoutingKey(routingKey);
        event.setPayload(serialize(payload));
        event.setStatus(OutboxEvent.STATUS_PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now());
        outboxEventMapper.insert(event);
        triggerAfterCommit();
        log.debug("Outbox event recorded, eventId={}, type={}", event.getEventId(), event.getEventType());
    }

    private void triggerAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 只做“尽快投递”的优化，真正兜底靠 OutboxRelay 的定时扫描
                    outboxRelay.deliverAsync();
                }
            });
            return;
        }
        outboxRelay.deliverAsync();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            // 序列化失败说明事件无法被可靠投递，让业务事务一起回滚，避免出现“业务成功、事件丢失”
            throw new IllegalStateException("事件序列化失败，已回滚当前事务", exception);
        }
    }
}
