package org.example.secondhandtradingplatform.chat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPresenceServiceTest {

    private static final String KEY = "chat:online:connections:1";

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private ChatPresenceService presenceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        presenceService = new ChatPresenceService(redisTemplate, 90);
    }

    @Test
    @DisplayName("连接写入 ZSET，并给 key 设置兜底过期时间")
    void connectStoresConnectionWithScore() {
        presenceService.connect(1L, "session-1");

        verify(zSetOperations).add(eq(KEY), eq("session-1"), anyDouble());
        verify(redisTemplate).expire(KEY, Duration.ofSeconds(270));
    }

    @Test
    @DisplayName("只统计时间窗口内的连接，过期的僵尸连接不算在线")
    void onlineOnlyCountsConnectionsWithinTtl() {
        when(zSetOperations.count(eq(KEY), anyDouble(), eq(Double.POSITIVE_INFINITY))).thenReturn(1L);

        assertTrue(presenceService.isOnline(1L));

        verify(zSetOperations).count(eq(KEY), anyDouble(), eq(Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("窗口内没有连接就是离线")
    void offlineWhenNoConnectionInWindow() {
        when(zSetOperations.count(eq(KEY), anyDouble(), anyDouble())).thenReturn(0L);

        assertFalse(presenceService.isOnline(1L));
    }

    @Test
    @DisplayName("Redis 异常时按离线处理，不把异常抛给调用方")
    void redisFailureFallsBackToOffline() {
        when(zSetOperations.count(eq(KEY), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("redis down"));

        assertFalse(presenceService.isOnline(1L));
    }

    @Test
    @DisplayName("断开时移除成员，空了就把 key 删掉")
    void disconnectRemovesMemberAndDeletesEmptyKey() {
        when(zSetOperations.zCard(KEY)).thenReturn(0L);

        presenceService.disconnect(1L, "session-1");

        verify(zSetOperations).remove(KEY, "session-1");
        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("断开后还有其它连接时保留 key")
    void disconnectKeepsKeyWhenOtherConnectionsRemain() {
        when(zSetOperations.zCard(KEY)).thenReturn(1L);

        presenceService.disconnect(1L, "session-1");

        verify(zSetOperations).remove(KEY, "session-1");
        verify(redisTemplate, org.mockito.Mockito.never()).delete(anyString());
    }
}
