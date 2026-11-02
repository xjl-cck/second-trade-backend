package org.example.secondhandtradingplatform.system.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.outbox.OutboxService;
import org.springframework.stereotype.Component;

/**
 * 系统通知发布器：事件先落 outbox，由 OutboxRelay 负责投递。
 */
@Component
public class SystemNotificationPublisher {

    private final OutboxService outboxService;
    private final RabbitMqProperties properties;

    public SystemNotificationPublisher(OutboxService outboxService, RabbitMqProperties properties) {
        this.outboxService = outboxService;
        this.properties = properties;
    }

    public void publish(SystemNotificationEvent event) {
        outboxService.record(properties.getSystemExchange(), properties.getSystemRoutingKey(), event);
    }
}
