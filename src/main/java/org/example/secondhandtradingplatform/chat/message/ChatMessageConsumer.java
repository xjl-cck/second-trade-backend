package org.example.secondhandtradingplatform.chat.message;

import org.example.secondhandtradingplatform.config.RealtimeNotificationBroadcaster;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageConsumer {

    private final RealtimeNotificationBroadcaster broadcaster;

    public ChatMessageConsumer(RealtimeNotificationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @RabbitListener(queues = "${app.rabbitmq.chat-queue}")
    public void consume(ChatMessageEvent event) {
        broadcaster.sendChat(event.getMessage());
    }
}
