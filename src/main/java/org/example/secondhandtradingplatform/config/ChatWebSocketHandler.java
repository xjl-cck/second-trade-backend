package org.example.secondhandtradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper objectMapper, ChatService chatService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            ChatMessageSendRequest request = objectMapper.readValue(textMessage.getPayload(), ChatMessageSendRequest.class);
            ChatMessageResponse message = chatService.send(getUserId(session), request);
            sendToUser(message.getSenderId(), message);
            sendToUser(message.getReceiverId(), message);
        } catch (Exception exception) {
            sendError(session, exception.getMessage() == null ? "消息发送失败" : exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessionsByUser.remove(userId, userSessions);
            }
        }
    }

    private void sendToUser(Long userId, ChatMessageResponse message) {
        Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("type", "message", "data", message));
        } catch (Exception exception) {
            return;
        }
        for (WebSocketSession userSession : userSessions) {
            if (userSession.isOpen()) {
                try {
                    synchronized (userSession) {
                        userSession.sendMessage(new TextMessage(payload));
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("type", "error", "message", message));
            session.sendMessage(new TextMessage(payload));
        } catch (IOException ignored) {
        }
    }

    private Long getUserId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
}
