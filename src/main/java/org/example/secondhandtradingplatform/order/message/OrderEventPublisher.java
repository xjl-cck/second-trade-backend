package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.system.message.SystemNotificationEvent;
import org.example.secondhandtradingplatform.system.message.SystemNotificationPublisher;
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
    private final RabbitMqProperties properties;
    private final SystemNotificationPublisher systemNotificationPublisher;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate,
                               RabbitMqProperties properties,
                               SystemNotificationPublisher systemNotificationPublisher) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.systemNotificationPublisher = systemNotificationPublisher;
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
            publishSystemNotifications(event);
        } catch (RuntimeException exception) {
            log.warn("Order event publish failed, orderId={}", event.getOrderId(), exception);
        }
    }

    private void publishSystemNotifications(OrderStatusEvent event) {
        publishSystemNotification(event, event.getBuyerId());
        if (event.getSellerId() != null && !event.getSellerId().equals(event.getBuyerId())) {
            publishSystemNotification(event, event.getSellerId());
        }
    }

    private void publishSystemNotification(OrderStatusEvent event, Long recipientId) {
        if (recipientId == null) {
            return;
        }
        SystemNotificationEvent notification = new SystemNotificationEvent();
        notification.setRecipientId(recipientId);
        notification.setTitle("订单状态更新");
        notification.setContent(event.getStatusText());
        notification.setRelatedType("order");
        notification.setRelatedId(event.getOrderId());
        notification.setEventTime(event.getEventTime());
        systemNotificationPublisher.publishAfterCommit(notification);
    }
}
