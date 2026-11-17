package org.example.secondhandtradingplatform.product.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductEventPublisherTest {

    private OutboxService outboxService;
    private ProductEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxService = mock(OutboxService.class);
        RabbitMqProperties properties = new RabbitMqProperties();
        properties.setProductExchange("product.events");
        properties.setProductRoutingKey("product.changed");
        publisher = new ProductEventPublisher(outboxService, properties);
    }

    @Test
    @DisplayName("商品变更事件写入 outbox，由 OutboxRelay 负责可靠投递")
    void recordsEventIntoOutbox() {
        publisher.publish(9L, ProductChangedEvent.REASON_STOCK_DEDUCTED);

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(outboxService).record(eq("product.events"), eq("product.changed"), captor.capture());
        assertEquals(9L, captor.getValue().getProductId());
        assertEquals(ProductChangedEvent.REASON_STOCK_DEDUCTED, captor.getValue().getReason());
    }

    @Test
    @DisplayName("商品 ID 为空时不产生事件")
    void ignoresNullProductId() {
        publisher.publish(null, ProductChangedEvent.REASON_STOCK_RESTORED);

        verifyNoInteractions(outboxService);
    }
}
