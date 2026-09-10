package org.example.secondhandtradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final RealtimeNotificationBroadcaster broadcaster;

    public ChatWebSocketHandler(ObjectMapper objectMapper,
                                ChatService chatService,
                                RealtimeNotificationBroadcaster broadcaster) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        broadcaster.register(session, getUserId(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            ChatMessageSendRequest request = objectMapper.readValue(textMessage.getPayload(), ChatMessageSendRequest.class);
            chatService.send(getUserId(session), request);
        } catch (Exception exception) {
            sendError(session, exception.getMessage() == null ? "消息发送失败" : exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session, getUserId(session));
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("type", "error", "message", message));
            session.sendMessage(new TextMessage(payload));
        } catch (Exception ignored) {
        }
    }

    private Long getUserId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
}
