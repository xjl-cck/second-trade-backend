package org.example.secondhandtradingplatform.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, Class<T> type) {
        String value = getValue(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            log.warn("Redis cache deserialization failed, key={}", key, exception);
            delete(key);
            return null;
        }
    }

    public <T> T get(String key, JavaType type) {
        String value = getValue(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            log.warn("Redis cache deserialization failed, key={}", key, exception);
            delete(key);
            return null;
        }
    }

    public <T> List<T> getList(String key, Class<T> elementType) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return get(key, listType);
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Redis cache write failed, key={}", key, exception);
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Redis cache delete failed, key={}", key, exception);
        }
    }

    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(locked) ? token : null;
        } catch (RuntimeException exception) {
            log.warn("Redis lock acquire failed, key={}", key, exception);
            return "";
        }
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            String current = redisTemplate.opsForValue().get(key);
            if (token.equals(current)) {
                redisTemplate.delete(key);
            }
        } catch (RuntimeException exception) {
            log.warn("Redis lock release failed, key={}", key, exception);
        }
    }

    private String getValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            log.warn("Redis cache read failed, key={}", key, exception);
            return null;
        }
    }
}
