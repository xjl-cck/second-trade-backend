package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.outbox.OutboxService;
import org.springframework.stereotype.Component;

@Component
public class OrderClosePublisher {

    private final OutboxService outboxService;
    private final RabbitMqProperties properties;

    public OrderClosePublisher(OutboxService outboxService, RabbitMqProperties properties) {
        this.outboxService = outboxService;
        this.properties = properties;
    }

    public void publish(Long orderId) {
        outboxService.record(
                properties.getOrderExchange(),
                properties.getOrderCloseRoutingKey(),
                new OrderCloseRequestedEvent(orderId));
    }
}
