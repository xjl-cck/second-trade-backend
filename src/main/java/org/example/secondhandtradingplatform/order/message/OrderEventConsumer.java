package org.example.secondhandtradingplatform.order.message;

import org.example.secondhandtradingplatform.config.RealtimeNotificationBroadcaster;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private final RealtimeNotificationBroadcaster broadcaster;

    public OrderEventConsumer(RealtimeNotificationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @RabbitListener(queues = "${app.rabbitmq.order-queue}")
    public void consume(OrderStatusEvent event) {
        broadcaster.sendOrder(event);
    }
}
