package org.example.secondhandtradingplatform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private OutboxEventMapper outboxEventMapper;
    private RabbitTemplate rabbitTemplate;
    private OutboxProperties properties;
    private OutboxRelay outboxRelay;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        outboxEventMapper = mock(OutboxEventMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        properties = new OutboxProperties();
        properties.setBatchSize(10);
        properties.setMaxAttempts(3);
        properties.setInitialBackoffSeconds(2);
        properties.setMaxBackoffSeconds(60);
        properties.setConfirmTimeoutMillis(1000);
        properties.setStuckTimeoutSeconds(120);
        outboxRelay = new OutboxRelay(
                outboxEventMapper,
                rabbitTemplate,
                properties,
                objectMapper,
                Runnable::run
        );
    }

    @Test
    @DisplayName("broker ack 后标记为已投递")
    void marksSentWhenBrokerAcks() throws Exception {
        OutboxEvent event = pendingEvent(1L, 0, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(1L)).thenReturn(1);
        completeConfirm(true, null, null);

        outboxRelay.deliverPending();

        verify(outboxEventMapper).markSent(1L);
        verify(outboxEventMapper, never()).markRetry(anyLong(), any(), anyString());
        verify(outboxEventMapper, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("broker nack 时退避重试，不标记已投递")
    void retriesWhenBrokerNacks() throws Exception {
        OutboxEvent event = pendingEvent(2L, 0, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(2L)).thenReturn(1);
        completeConfirm(false, "broker rejected", null);

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper).markRetry(eq(2L), any(LocalDateTime.class), contains("broker rejected"));
    }

    @Test
    @DisplayName("发送抛异常（连接不可用）时退避重试")
    void retriesWhenSendThrows() throws Exception {
        OutboxEvent event = pendingEvent(3L, 0, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(3L)).thenReturn(1);
        doThrow(new AmqpException("connection refused")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper).markRetry(eq(3L), any(LocalDateTime.class), contains("connection refused"));
    }

    @Test
    @DisplayName("消息路由不到队列时同样视为失败并重试")
    void retriesWhenMessageIsReturned() throws Exception {
        OutboxEvent event = pendingEvent(4L, 0, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(4L)).thenReturn(1);
        completeConfirm(true, null, new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
                "order.events", "order.status"));

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper).markRetry(eq(4L), any(LocalDateTime.class), contains("NO_ROUTE"));
    }

    @Test
    @DisplayName("超过最大尝试次数后置为投递失败")
    void marksFailedWhenAttemptsExhausted() throws Exception {
        OutboxEvent event = pendingEvent(5L, 2, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(5L)).thenReturn(1);
        completeConfirm(false, "still failing", null);

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper, never()).markRetry(anyLong(), any(), anyString());
        verify(outboxEventMapper).markFailed(eq(5L), contains("still failing"));
    }

    @Test
    @DisplayName("抢不到事件（别的实例在投递）时不重复发送")
    void skipsWhenClaimFails() throws Exception {
        OutboxEvent event = pendingEvent(6L, 0, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(6L)).thenReturn(0);

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper, never()).markRetry(anyLong(), any(), anyString());
        verify(outboxEventMapper, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("事件类型无法还原时按失败重试并记录原因")
    void retriesWhenEventTypeIsUnknown() throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setId(7L);
        event.setEventId("evt-7");
        event.setEventType("org.example.secondhandtradingplatform.NotExistsEvent");
        event.setExchangeName("order.events");
        event.setRoutingKey("order.status");
        event.setPayload("{}");
        event.setStatus(OutboxEvent.STATUS_PENDING);
        event.setAttempts(0);
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(7L)).thenReturn(1);

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper).markRetry(eq(7L), any(LocalDateTime.class), contains("事件类型不存在"));
    }

    @Test
    @DisplayName("重试时间按指数退避推后")
    void backoffGrowsWithAttempts() throws Exception {
        OutboxEvent event = pendingEvent(8L, 1, new TestEvent("hello"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(event));
        when(outboxEventMapper.claim(8L)).thenReturn(1);
        completeConfirm(false, "nack", null);

        outboxRelay.deliverPending();

        org.mockito.ArgumentCaptor<LocalDateTime> captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventMapper).markRetry(eq(8L), captor.capture(), anyString());
        LocalDateTime nextAttemptAt = captor.getValue();
        assertNotNull(nextAttemptAt);
        // 第 2 次尝试：2 * 2^1 = 4 秒后
        assertTrue(nextAttemptAt.isAfter(LocalDateTime.now().plusSeconds(2)));
    }

    @Test
    @DisplayName("连接不可用时本轮中止，不逐条等确认超时")
    void abortsRoundWhenBrokerIsUnreachable() throws Exception {
        OutboxEvent first = pendingEvent(9L, 0, new TestEvent("first"));
        OutboxEvent second = pendingEvent(10L, 0, new TestEvent("second"));
        when(outboxEventMapper.selectPending(any(LocalDateTime.class), eq(10))).thenReturn(List.of(first, second));
        when(outboxEventMapper.claim(9L)).thenReturn(1);
        doThrow(new AmqpException("connection refused")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        outboxRelay.deliverPending();

        verify(outboxEventMapper, never()).claim(10L);
    }

    private void completeConfirm(boolean ack, String reason, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(returned);
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private OutboxEvent pendingEvent(long id, int attempts, Object payload) throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setEventId("evt-" + id);
        event.setEventType(payload.getClass().getName());
        event.setExchangeName("order.events");
        event.setRoutingKey("order.status");
        event.setPayload(objectMapper.writeValueAsString(payload));
        event.setStatus(OutboxEvent.STATUS_PENDING);
        event.setAttempts(attempts);
        event.setNextAttemptAt(LocalDateTime.now());
        return event;
    }

    public static class TestEvent {

        public String value;

        public TestEvent() {
        }

        public TestEvent(String value) {
            this.value = value;
        }
    }
}
