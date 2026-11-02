package org.example.secondhandtradingplatform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * outbox 投递器：把 outbox_event 里的待投递事件发到 RabbitMQ，并处理确认与重试。
 *
 * <p>投递不是“发出去就算成功”，而是等 broker 的 publisher confirm：
 * ack 才置为已投递；nack、确认超时、路由失败（mandatory + returns）或连接异常都会退避重试，
 * 超过最大次数后置为投递失败，保留在表里可供人工排查和重放。</p>
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String EVENT_TYPE_PREFIX = "org.example.secondhandtradingplatform.";
    private static final int LAST_ERROR_MAX_LENGTH = 480;

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final Map<String, Class<?>> eventTypeCache = new ConcurrentHashMap<>();
    private final AtomicBoolean delivering = new AtomicBoolean(false);

    public OutboxRelay(OutboxEventMapper outboxEventMapper,
                       RabbitTemplate rabbitTemplate,
                       OutboxProperties properties,
                       ObjectMapper objectMapper,
                       @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        this.outboxEventMapper = outboxEventMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.relay-interval-ms:2000}",
            initialDelayString = "${app.outbox.initial-delay-ms:5000}"
    )
    public void scheduledDeliver() {
        deliverSafely();
    }

    /**
     * 事务提交后立即触发一次投递，降低事件延迟；定时扫描仍是兜底。
     */
    public void deliverAsync() {
        try {
            taskExecutor.execute(this::deliverSafely);
        } catch (RejectedExecutionException exception) {
            log.warn("Outbox deliver task rejected, waiting for scheduled retry");
        }
    }

    public void deliverPending() {
        // 定时扫描和提交后触发可能同时进来，同一时刻只跑一轮，避免重复扫描和重复抢占
        if (!delivering.compareAndSet(false, true)) {
            return;
        }
        try {
            recoverStuckEvents();
            List<OutboxEvent> events = outboxEventMapper.selectPending(LocalDateTime.now(), properties.getBatchSize());
            for (OutboxEvent event : events) {
                if (!deliver(event)) {
                    // 连接不可用这类问题对整批事件都成立，本轮提前结束，避免每条都等一次确认超时
                    break;
                }
            }
        } finally {
            delivering.set(false);
        }
    }

    private void deliverSafely() {
        try {
            deliverPending();
        } catch (RuntimeException exception) {
            log.error("Outbox relay round failed", exception);
        }
    }

    private void recoverStuckEvents() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(properties.getStuckTimeoutSeconds());
        int recovered = outboxEventMapper.recoverStuck(deadline);
        if (recovered > 0) {
            log.warn("Recovered {} outbox events stuck in sending state", recovered);
        }
    }

    /**
     * 投递单条事件，返回 false 表示本轮应当中止（broker 连不上，后面的也发不出去）。
     */
    private boolean deliver(OutboxEvent event) {
        if (outboxEventMapper.claim(event.getId()) == 0) {
            // 已被其它实例或其它线程抢走
            return true;
        }
        try {
            publishWithConfirm(event);
            outboxEventMapper.markSent(event.getId());
            log.debug("Outbox event delivered, eventId={}", event.getEventId());
            return true;
        } catch (Exception exception) {
            handleFailure(event, exception);
            return !(exception instanceof AmqpException) && !(exception instanceof TimeoutException);
        }
    }

    /**
     * 发送并等待 broker 确认：只有 ack 且没有被退回（路由成功）才算投递成功。
     */
    private void publishWithConfirm(OutboxEvent event) throws Exception {
        Object payload = readPayload(event);
        CorrelationData correlationData = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend(
                event.getExchangeName(),
                event.getRoutingKey(),
                payload,
                message -> {
                    message.getMessageProperties().setMessageId(event.getEventId());
                    return message;
                },
                correlationData
        );
        CorrelationData.Confirm confirm = correlationData.getFuture()
                .get(properties.getConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("broker nack: "
                    + (confirm == null ? "no confirm" : confirm.getReason()));
        }
        ReturnedMessage returned = correlationData.getReturned();
        if (returned != null) {
            throw new IllegalStateException("message unroutable: " + returned.getReplyText());
        }
    }

    private Object readPayload(OutboxEvent event) {
        Class<?> type = eventTypeCache.computeIfAbsent(event.getEventType(), this::resolveEventType);
        try {
            return objectMapper.readValue(event.getPayload(), type);
        } catch (Exception exception) {
            throw new IllegalStateException("事件内容反序列化失败，type=" + event.getEventType(), exception);
        }
    }

    private Class<?> resolveEventType(String typeName) {
        if (!typeName.startsWith(EVENT_TYPE_PREFIX)) {
            throw new IllegalArgumentException("不支持的事件类型: " + typeName);
        }
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("事件类型不存在: " + typeName, exception);
        }
    }

    private void handleFailure(OutboxEvent event, Exception exception) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        String error = describe(exception);
        if (attempts >= properties.getMaxAttempts()) {
            outboxEventMapper.markFailed(event.getId(), error);
            log.error("Outbox event failed permanently, eventId={}, type={}, attempts={}, error={}",
                    event.getEventId(), event.getEventType(), attempts, error);
            return;
        }
        LocalDateTime nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds(attempts));
        outboxEventMapper.markRetry(event.getId(), nextAttemptAt, error);
        log.warn("Outbox event deliver failed, eventId={}, attempts={}, nextAttemptAt={}, error={}",
                event.getEventId(), attempts, nextAttemptAt, error);
    }

    private long backoffSeconds(int attempts) {
        long seconds = properties.getInitialBackoffSeconds() * (1L << Math.min(attempts - 1, 10));
        return Math.min(seconds, properties.getMaxBackoffSeconds());
    }

    private String describe(Exception exception) {
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return message.length() <= LAST_ERROR_MAX_LENGTH ? message : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
