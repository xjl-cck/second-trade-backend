package org.example.secondhandtradingplatform.chat.job;

import org.example.secondhandtradingplatform.chat.service.ChatPresenceService;
import org.example.secondhandtradingplatform.config.RealtimeNotificationBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatHeartbeatJobTest {

    private RealtimeNotificationBroadcaster broadcaster;
    private ChatPresenceService presenceService;
    private ChatHeartbeatJob job;

    @BeforeEach
    void setUp() {
        broadcaster = mock(RealtimeNotificationBroadcaster.class);
        presenceService = mock(ChatPresenceService.class);
        job = new ChatHeartbeatJob(broadcaster, presenceService);
    }

    @Test
    @DisplayName("给所有打开的连接发 ping 帧")
    void pingsOpenSessions() throws IOException {
        WebSocketSession session = session("session-1", 7L);
        when(session.isOpen()).thenReturn(true);
        when(broadcaster.activeSessions()).thenReturn(List.of(session));

        job.pingSessions();

        verify(session).sendMessage(any(PingMessage.class));
        verifyNoInteractions(presenceService);
    }

    @Test
    @DisplayName("已经关闭的连接不重复发送")
    void skipsClosedSessions() throws IOException {
        WebSocketSession session = session("session-1", 7L);
        when(session.isOpen()).thenReturn(false);
        when(broadcaster.activeSessions()).thenReturn(List.of(session));

        job.pingSessions();

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("ping 写失败说明连接已死，直接清理连接和在线状态")
    void cleansUpBrokenSession() throws IOException {
        WebSocketSession session = session("session-1", 7L);
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("broken pipe")).when(session).sendMessage(any(PingMessage.class));
        when(broadcaster.activeSessions()).thenReturn(List.of(session));

        job.pingSessions();

        verify(broadcaster).unregister(session, 7L);
        verify(presenceService).disconnect(7L, "session-1");
        verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    private WebSocketSession session(String sessionId, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", userId);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
