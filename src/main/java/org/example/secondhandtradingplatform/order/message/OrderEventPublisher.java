package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.outbox.OutboxService;
import org.example.secondhandtradingplatform.system.message.SystemNotificationEvent;
import org.example.secondhandtradingplatform.system.message.SystemNotificationPublisher;
import org.springframework.stereotype.Component;

/**
 * 订单事件发布器：只负责把事件写进 outbox（与订单数据同事务），实际投递、确认和重试由 OutboxRelay 负责。
 */
@Component
public class OrderEventPublisher {

    private final OutboxService outboxService;
    private final RabbitMqProperties properties;
    private final SystemNotificationPublisher systemNotificationPublisher;

    public OrderEventPublisher(OutboxService outboxService,
                               RabbitMqProperties properties,
                               SystemNotificationPublisher systemNotificationPublisher) {
        this.outboxService = outboxService;
        this.properties = properties;
        this.systemNotificationPublisher = systemNotificationPublisher;
    }

    public void publish(OrderStatusEvent event) {
        outboxService.record(properties.getOrderExchange(), properties.getOrderRoutingKey(), event);
        publishSystemNotifications(event);
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
        systemNotificationPublisher.publish(notification);
    }
}
