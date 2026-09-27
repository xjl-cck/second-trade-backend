package org.example.secondhandtradingplatform.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class ChatPresenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPresenceService.class);
    private static final String ONLINE_CONNECTIONS_PREFIX = "chat:online:connections:";

    private final StringRedisTemplate redisTemplate;

    public ChatPresenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void connect(Long userId, String connectionId) {
        update(userId, connectionId, true);
    }

    public void disconnect(Long userId, String connectionId) {
        update(userId, connectionId, false);
    }

    public boolean isOnline(Long userId) {
        try {
            Long size = redisTemplate.opsForSet().size(key(userId));
            return size != null && size > 0;
        } catch (RuntimeException exception) {
            log.warn("Failed to read chat presence, userId={}", userId, exception);
            return false;
        }
    }

    private void update(Long userId, String connectionId, boolean connected) {
        if (userId == null || connectionId == null || connectionId.isBlank()) {
            return;
        }
        try {
            if (connected) {
                redisTemplate.opsForSet().add(key(userId), connectionId);
            } else {
                redisTemplate.opsForSet().remove(key(userId), connectionId);
                Long size = redisTemplate.opsForSet().size(key(userId));
                if (size != null && size == 0) {
                    redisTemplate.delete(Collections.singleton(key(userId)));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to update chat presence, userId={}, connected={}", userId, connected, exception);
        }
    }

    private String key(Long userId) {
        return ONLINE_CONNECTIONS_PREFIX + userId;
    }
}
