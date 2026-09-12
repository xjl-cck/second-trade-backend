package org.example.secondhandtradingplatform.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

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

    public void deleteByPattern(String pattern) {
        Set<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException exception) {
            log.warn("Redis batch delete failed, pattern={}", pattern, exception);
        }
    }

    public long increment(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            return value == null ? 0L : value;
        } catch (RuntimeException exception) {
            log.warn("Redis increment failed, key={}", key, exception);
            return 0L;
        }
    }

    public long increment(String key, long delta) {
        try {
            Long value = redisTemplate.opsForValue().increment(key, delta);
            return value == null ? 0L : value;
        } catch (RuntimeException exception) {
            log.warn("Redis increment failed, key={}, delta={}", key, delta, exception);
            return 0L;
        }
    }

    public long getLong(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException exception) {
            log.warn("Redis number read failed, key={}", key, exception);
            return 0L;
        }
    }

    public long getAndReset(String key) {
        try {
            String value = redisTemplate.opsForValue().getAndSet(key, "0");
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException exception) {
            log.warn("Redis counter reset failed, key={}", key, exception);
            return 0L;
        }
    }

    public Set<String> scanKeys(String pattern) {
        try {
            return redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Set<String>>) connection -> {
                Set<String> keys = new HashSet<>();
                try (var cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
                    cursor.forEachRemaining(value -> keys.add(new String(value, java.nio.charset.StandardCharsets.UTF_8)));
                }
                return keys;
            });
        } catch (RuntimeException exception) {
            log.warn("Redis key scan failed, pattern={}", pattern, exception);
            return Set.of();
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
