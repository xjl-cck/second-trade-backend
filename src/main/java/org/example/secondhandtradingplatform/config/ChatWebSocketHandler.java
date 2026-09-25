package org.example.secondhandtradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageResponse;
import org.example.secondhandtradingplatform.chat.service.ChatService;
import org.example.secondhandtradingplatform.chat.service.ChatPresenceService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.List;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatPresenceService presenceService;
    private final RealtimeNotificationBroadcaster broadcaster;

    public ChatWebSocketHandler(ObjectMapper objectMapper,
                                ChatService chatService,
                                ChatPresenceService presenceService,
                                RealtimeNotificationBroadcaster broadcaster) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.presenceService = presenceService;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        broadcaster.register(session, userId);
        presenceService.connect(userId, session.getId());
        sendUnreadMessages(session, userId);
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
        Long userId = getUserId(session);
        broadcaster.unregister(session, userId);
        presenceService.disconnect(userId, session.getId());
    }

    private void sendUnreadMessages(WebSocketSession session, Long userId) {
        List<ChatMessageResponse> messages = chatService.listUnreadMessages(userId);
        if (messages.isEmpty() || !session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("type", "message-replay", "data", messages));
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception ignored) {
        }
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
