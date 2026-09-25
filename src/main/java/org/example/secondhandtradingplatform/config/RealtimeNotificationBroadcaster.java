package org.example.secondhandtradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.order.message.OrderStatusEvent;
import org.example.secondhandtradingplatform.system.message.SystemNotificationEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeNotificationBroadcaster {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public RealtimeNotificationBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session, Long userId) {
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(WebSocketSession session, Long userId) {
        Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessionsByUser.remove(userId, userSessions);
            }
        }
    }

    public void sendChat(ChatMessageResponse message) {
        send(message.getSenderId(), "message", message);
        send(message.getReceiverId(), "message", message);
    }

    public void sendOrder(OrderStatusEvent event) {
        send(event.getBuyerId(), "order-status", event);
        send(event.getSellerId(), "order-status", event);
    }

    public void sendSystem(SystemNotificationEvent event) {
        send(event.getRecipientId(), "system-notification", event);
    }

    private void send(Long userId, String type, Object data) {
        Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions == null) {
            return;
        }
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception exception) {
            return;
        }
        for (WebSocketSession userSession : userSessions) {
            if (!userSession.isOpen()) {
                continue;
            }
            try {
                synchronized (userSession) {
                    userSession.sendMessage(new TextMessage(payload));
                }
            } catch (IOException ignored) {
            }
        }
    }
}
