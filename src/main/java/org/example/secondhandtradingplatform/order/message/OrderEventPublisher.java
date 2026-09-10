package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.order.config.OrderRabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderRabbitProperties properties;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, OrderRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publishAfterCommit(OrderStatusEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            return;
        }
        publish(event);
    }

    private void publish(OrderStatusEvent event) {
        try {
            rabbitTemplate.convertAndSend(properties.getOrderExchange(), properties.getOrderRoutingKey(), event);
        } catch (RuntimeException exception) {
            log.warn("Order event publish failed, orderId={}", event.getOrderId(), exception);
        }
    }
}
