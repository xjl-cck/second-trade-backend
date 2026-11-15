package org.example.secondhandtradingplatform.chat.job;

import org.example.secondhandtradingplatform.chat.service.ChatPresenceService;
import org.example.secondhandtradingplatform.config.RealtimeNotificationBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 聊天连接保活。
 *
 * <p>为什么需要：TCP 连接被 NAT/代理静默断开时，服务端 {@link WebSocketSession#isOpen()} 可能仍然是 true，
 * 连接会一直留在本机广播表里，在线状态也会一直显示“在线”。服务端定时发 WebSocket ping，
 * 浏览器会自动回 pong（不需要前端写代码），handler 收到 pong 后刷新在线状态的时间戳；
 * 发不出去的连接在这里直接清理。</p>
 */
@Component
public class ChatHeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(ChatHeartbeatJob.class);

    private final RealtimeNotificationBroadcaster broadcaster;
    private final ChatPresenceService presenceService;

    public ChatHeartbeatJob(RealtimeNotificationBroadcaster broadcaster,
                            ChatPresenceService presenceService) {
        this.broadcaster = broadcaster;
        this.presenceService = presenceService;
    }

    @Scheduled(
            fixedDelayString = "${app.chat.heartbeat-interval-ms:30000}",
            initialDelayString = "${app.chat.heartbeat-initial-delay-ms:15000}"
    )
    public void pingSessions() {
        for (WebSocketSession session : broadcaster.activeSessions()) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new PingMessage());
                }
            } catch (Exception exception) {
                // 写失败说明这条连接已经不可用，主动清理，避免广播表和在线状态里留下僵尸连接
                log.debug("聊天连接心跳失败，主动清理，sessionId={}", session.getId(), exception);
                cleanup(session);
            }
        }
    }

    private void cleanup(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long id) {
            broadcaster.unregister(session, id);
            presenceService.disconnect(id, session.getId());
        }
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (Exception ignored) {
        }
    }
}
