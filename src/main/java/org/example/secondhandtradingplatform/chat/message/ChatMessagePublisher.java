package org.example.secondhandtradingplatform.chat.message;

import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.outbox.OutboxService;
import org.springframework.stereotype.Component;

/**
 * 聊天消息事件发布器：事件先落 outbox，由 OutboxRelay 负责投递。
 */
@Component
public class ChatMessagePublisher {

    private final OutboxService outboxService;
    private final RabbitMqProperties properties;

    public ChatMessagePublisher(OutboxService outboxService, RabbitMqProperties properties) {
        this.outboxService = outboxService;
        this.properties = properties;
    }

    public void publish(ChatMessageResponse message) {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setMessage(message);
        outboxService.record(properties.getChatExchange(), properties.getChatRoutingKey(), event);
    }
}
