package org.example.secondhandtradingplatform.system.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SystemNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SystemNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;

    public SystemNotificationPublisher(RabbitTemplate rabbitTemplate, RabbitMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publishAfterCommit(SystemNotificationEvent event) {
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

    private void publish(SystemNotificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    properties.getSystemExchange(),
                    properties.getSystemRoutingKey(),
                    event
            );
        } catch (RuntimeException exception) {
            log.warn("System notification publish failed, recipientId={}", event.getRecipientId(), exception);
        }
    }
}
