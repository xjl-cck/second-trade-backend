package org.example.secondhandtradingplatform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxServiceTest {

    private OutboxEventMapper outboxEventMapper;
    private OutboxRelay outboxRelay;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxEventMapper = mock(OutboxEventMapper.class);
        outboxRelay = mock(OutboxRelay.class);
        outboxService = new OutboxService(outboxEventMapper, new ObjectMapper(), outboxRelay);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事件落 outbox 时带上交换机、路由键、类型和 JSON 内容")
    void recordsPendingEvent() {
        outboxService.record("order.events", "order.status", new TestEvent("hello"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventMapper).insert(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("order.events", saved.getExchangeName());
        assertEquals("order.status", saved.getRoutingKey());
        assertEquals(TestEvent.class.getName(), saved.getEventType());
        assertEquals(OutboxEvent.STATUS_PENDING, saved.getStatus());
        assertEquals(0, saved.getAttempts());
        assertNotNull(saved.getEventId());
        assertNotNull(saved.getNextAttemptAt());
        assertTrue(saved.getPayload().contains("hello"));
    }

    @Test
    @DisplayName("事务内只落库，提交之后才触发投递")
    void deliversOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        outboxService.record("order.events", "order.status", new TestEvent("hello"));

        verifyNoInteractions(outboxRelay);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(outboxRelay).deliverAsync();
    }

    @Test
    @DisplayName("没有事务时落库后立即触发投递")
    void deliversImmediatelyWithoutTransaction() {
        outboxService.record("order.events", "order.status", new TestEvent("hello"));

        verify(outboxEventMapper).insert(any(OutboxEvent.class));
        verify(outboxRelay).deliverAsync();
    }

    @Test
    @DisplayName("事件无法序列化时抛出异常，让业务事务一起回滚")
    void failsWhenPayloadCannotBeSerialized() {
        assertThrows(IllegalStateException.class,
                () -> outboxService.record("order.events", "order.status", new UnserializableEvent()));

        verify(outboxEventMapper, never()).insert(any(OutboxEvent.class));
        verifyNoInteractions(outboxRelay);
    }

    public static class TestEvent {

        public String value;

        public TestEvent() {
        }

        public TestEvent(String value) {
            this.value = value;
        }
    }

    public static class UnserializableEvent {

        public String getValue() {
            throw new IllegalStateException("serialization failure");
        }
    }
}
