package org.example.secondhandtradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.secondhandtradingplatform.chat.dto.ChatMessageSendRequest;
import org.example.secondhandtradingplatform.chat.dto.ChatReplayResponse;
import org.example.secondhandtradingplatform.chat.service.ChatPresenceService;
import org.example.secondhandtradingplatform.chat.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatPresenceService presenceService;
    private final RealtimeNotificationBroadcaster broadcaster;
    private final int replayMaxMessages;

    public ChatWebSocketHandler(ObjectMapper objectMapper,
                                ChatService chatService,
                                ChatPresenceService presenceService,
                                RealtimeNotificationBroadcaster broadcaster,
                                @Value("${app.chat.replay-max-messages:100}") int replayMaxMessages) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.presenceService = presenceService;
        this.broadcaster = broadcaster;
        this.replayMaxMessages = Math.max(1, replayMaxMessages);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId == null) {
            // 理论上握手拦截器已经挡掉，这里兜底，避免空 userId 进到广播表里
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        broadcaster.register(session, userId);
        presenceService.connect(userId, session.getId());
        sendUnreadMessages(session, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            Long userId = getUserId(session);
            // 有业务消息说明连接还活着，顺手续一次心跳
            presenceService.touch(userId, session.getId());
            ChatMessageSendRequest request = objectMapper.readValue(textMessage.getPayload(), ChatMessageSendRequest.class);
            chatService.send(userId, request);
        } catch (Exception exception) {
            sendError(session, exception.getMessage() == null ? "消息发送失败" : exception.getMessage());
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        // 浏览器收到服务端 ping 会自动回 pong，不需要前端写任何代码
        presenceService.touch(getUserId(session), session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        broadcaster.unregister(session, userId);
        presenceService.disconnect(userId, session.getId());
    }

    private void sendUnreadMessages(WebSocketSession session, Long userId) {
        ChatReplayResponse replay = chatService.replayUnreadMessages(userId, replayMaxMessages);
        if (replay.messages().isEmpty() || !session.isOpen()) {
            return;
        }
        try {
            // truncated=true 表示未读数超过上限，只推了最近一批，更早的由客户端打开会话时拉历史
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "message-replay",
                    "data", replay.messages(),
                    "truncated", replay.truncated()));
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
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }

    private Long getUserId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
}
