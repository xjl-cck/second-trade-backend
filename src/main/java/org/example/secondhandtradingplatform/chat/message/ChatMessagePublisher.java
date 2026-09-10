package org.example.secondhandtradingplatform.chat.message;

import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.order.config.OrderRabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ChatMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderRabbitProperties properties;

    public ChatMessagePublisher(RabbitTemplate rabbitTemplate, OrderRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publishAfterCommit(ChatMessageResponse message) {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setMessage(message);
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

    private void publish(ChatMessageEvent event) {
        try {
            rabbitTemplate.convertAndSend(properties.getChatExchange(), properties.getChatRoutingKey(), event);
        } catch (RuntimeException exception) {
            Long messageId = event.getMessage() == null ? null : event.getMessage().getId();
            log.warn("Chat message event publish failed, messageId={}", messageId, exception);
        }
    }
}
