package org.example.secondhandtradingplatform.system.message;

import org.example.secondhandtradingplatform.config.RealtimeNotificationBroadcaster;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SystemNotificationConsumer {

    private final RealtimeNotificationBroadcaster broadcaster;

    public SystemNotificationConsumer(RealtimeNotificationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @RabbitListener(queues = "${app.rabbitmq.system-queue}")
    public void consume(SystemNotificationEvent event) {
        broadcaster.sendSystem(event);
    }
}
