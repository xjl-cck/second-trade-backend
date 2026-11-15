package org.example.secondhandtradingplatform.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 在线状态。
 *
 * <p>用 ZSET 而不是 SET：score 存“最后一次心跳时间”，判断在线时只看时间窗口内的连接。
 * 这样即使节点崩溃、连接来不及从集合里删除，过期条目也会自动被判为离线，
 * 不需要额外的清理任务就能保证状态正确（给 key 设 TTL 只是兜底内存）。</p>
 */
@Service
public class ChatPresenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPresenceService.class);
    private static final String ONLINE_CONNECTIONS_PREFIX = "chat:online:connections:";

    private final StringRedisTemplate redisTemplate;
    private final Duration connectionTtl;

    public ChatPresenceService(StringRedisTemplate redisTemplate,
                               @Value("${app.chat.presence-ttl-seconds:90}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.connectionTtl = Duration.ofSeconds(Math.max(5, ttlSeconds));
    }

    public void connect(Long userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            String key = key(userId);
            // 顺手清掉已经超时的成员，避免崩溃节点留下的连接 id 越积越多
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - connectionTtl.toMillis() * 2);
            redisTemplate.opsForZSet().add(key, connectionId, now);
            redisTemplate.expire(key, connectionTtl.multipliedBy(3));
        } catch (RuntimeException exception) {
            log.warn("Failed to mark chat presence online, userId={}", userId, exception);
        }
    }

    /**
     * 心跳续期。服务端定时 ping，浏览器自动回 pong，这里刷新该连接的最后活跃时间。
     */
    public void touch(Long userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return;
        }
        try {
            String key = key(userId);
            redisTemplate.opsForZSet().add(key, connectionId, System.currentTimeMillis());
            redisTemplate.expire(key, connectionTtl.multipliedBy(3));
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh chat presence, userId={}", userId, exception);
        }
    }

    public void disconnect(Long userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return;
        }
        try {
            String key = key(userId);
            redisTemplate.opsForZSet().remove(key, connectionId);
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size == 0) {
                redisTemplate.delete(key);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to mark chat presence offline, userId={}", userId, exception);
        }
    }

    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            double offlineBefore = System.currentTimeMillis() - connectionTtl.toMillis();
            Long alive = redisTemplate.opsForZSet().count(key(userId), offlineBefore, Double.POSITIVE_INFINITY);
            return alive != null && alive > 0;
        } catch (RuntimeException exception) {
            log.warn("Failed to read chat presence, userId={}", userId, exception);
            return false;
        }
    }

    private String key(Long userId) {
        return ONLINE_CONNECTIONS_PREFIX + userId;
    }
}
