package org.example.secondhandtradingplatform.favorite.service;

import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.favorite.entity.Favorite;
import org.example.secondhandtradingplatform.favorite.mapper.FavoriteMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class FavoriteRedisService {

    private static final String KEY_PREFIX = "favorite:v2:";
    private static final String USER_SET_PREFIX = KEY_PREFIX + "user:";
    private static final String USER_ZSET_PREFIX = KEY_PREFIX + "user:time:";
    private static final String PRODUCT_SET_PREFIX = KEY_PREFIX + "product:";
    private static final String DIRTY_RELATION_ZSET = KEY_PREFIX + "dirty:relations";
    private static final String DIRTY_PRODUCT_ZSET = KEY_PREFIX + "dirty:products";
    private static final String LOADED_USER_PREFIX = KEY_PREFIX + "loaded:user:";
    private static final String LOADED_PRODUCT_PREFIX = KEY_PREFIX + "loaded:product:";

    private static final DefaultRedisScript<Long> CHANGE_SCRIPT = new DefaultRedisScript<>(
            """
            local changed = 0
            if ARGV[5] == '1' then
                if redis.call('SADD', KEYS[1], ARGV[1]) == 1 then
                    redis.call('ZADD', KEYS[2], ARGV[4], ARGV[1])
                    redis.call('SADD', KEYS[3], ARGV[2])
                    redis.call('ZADD', KEYS[4], ARGV[4], ARGV[3])
                    redis.call('ZADD', KEYS[5], ARGV[4], ARGV[1])
                    changed = 1
                end
            else
                if redis.call('SREM', KEYS[1], ARGV[1]) == 1 then
                    redis.call('ZREM', KEYS[2], ARGV[1])
                    redis.call('SREM', KEYS[3], ARGV[2])
                    redis.call('ZADD', KEYS[4], ARGV[4], ARGV[3])
                    redis.call('ZADD', KEYS[5], ARGV[4], ARGV[1])
                    changed = 1
                end
            end
            return changed
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> REMOVE_IF_OLD_SCRIPT = new DefaultRedisScript<>(
            """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) <= tonumber(ARGV[2]) then
                return redis.call('ZREM', KEYS[1], ARGV[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final FavoriteMapper favoriteMapper;
    private final RedisCacheService redisCacheService;

    public FavoriteRedisService(StringRedisTemplate redisTemplate,
                                FavoriteMapper favoriteMapper,
                                RedisCacheService redisCacheService) {
        this.redisTemplate = redisTemplate;
        this.favoriteMapper = favoriteMapper;
        this.redisCacheService = redisCacheService;
    }

    public void ensureUserLoaded(Long userId) {
        String marker = LOADED_USER_PREFIX + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(marker))) {
            return;
        }
        List<Favorite> favorites = favoriteMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Favorite>lambdaQuery()
                        .eq(Favorite::getUserId, userId)
                        .orderByAsc(Favorite::getCreateTime)
                        .orderByAsc(Favorite::getId));
        for (Favorite favorite : favorites) {
            String productId = String.valueOf(favorite.getProductId());
            long timestamp = favorite.getCreateTime() == null
                    ? Instant.now().toEpochMilli()
                    : favorite.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            redisTemplate.opsForSet().add(userSetKey(userId), productId);
            redisTemplate.opsForZSet().add(userZSetKey(userId), productId, timestamp);
        }
        redisTemplate.opsForValue().set(marker, "1");
    }

    public void ensureProductLoaded(Long productId) {
        String marker = LOADED_PRODUCT_PREFIX + productId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(marker))) {
            return;
        }
        List<Favorite> favorites = favoriteMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Favorite>lambdaQuery()
                        .eq(Favorite::getProductId, productId));
        for (Favorite favorite : favorites) {
            redisTemplate.opsForSet().add(productSetKey(productId), String.valueOf(favorite.getUserId()));
        }
        redisTemplate.opsForValue().set(marker, "1");
    }

    public boolean change(Long userId, Long productId, boolean favorite) {
        ensureUserLoaded(userId);
        ensureProductLoaded(productId);
        String relation = userId + ":" + productId;
        Long changed = redisTemplate.execute(
                CHANGE_SCRIPT,
                List.of(userSetKey(userId), userZSetKey(userId), productSetKey(productId),
                        DIRTY_RELATION_ZSET, DIRTY_PRODUCT_ZSET),
                String.valueOf(productId),
                String.valueOf(userId),
                relation,
                String.valueOf(System.currentTimeMillis()),
                favorite ? "1" : "0");
        return Long.valueOf(1L).equals(changed);
    }

    public Set<String> userProductIds(Long userId, int offset, int limit) {
        ensureUserLoaded(userId);
        Set<String> ids = redisTemplate.opsForZSet().reverseRange(userZSetKey(userId), offset, offset + limit - 1);
        return ids == null ? Set.of() : ids;
    }

    public long userFavoriteCount(Long userId) {
        ensureUserLoaded(userId);
        Long count = redisTemplate.opsForZSet().zCard(userZSetKey(userId));
        return count == null ? 0L : count;
    }

    public Set<String> dirtyRelations(long cutoff) {
        Set<String> values = redisTemplate.opsForZSet().rangeByScore(DIRTY_RELATION_ZSET, 0, cutoff);
        return values == null ? Set.of() : values;
    }

    public Set<String> dirtyProducts(long cutoff) {
        Set<String> values = redisTemplate.opsForZSet().rangeByScore(DIRTY_PRODUCT_ZSET, 0, cutoff);
        return values == null ? Set.of() : values;
    }

    public boolean isFavorite(Long userId, Long productId) {
        ensureUserLoaded(userId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(userSetKey(userId), String.valueOf(productId)));
    }

    public long productFavoriteCount(Long productId) {
        ensureProductLoaded(productId);
        Long count = redisTemplate.opsForSet().size(productSetKey(productId));
        return count == null ? 0L : count;
    }

    public void removeDirtyRelation(String relation, long cutoff) {
        redisTemplate.execute(REMOVE_IF_OLD_SCRIPT, List.of(DIRTY_RELATION_ZSET), relation, String.valueOf(cutoff));
    }

    public void removeDirtyProduct(Long productId, long cutoff) {
        redisTemplate.execute(REMOVE_IF_OLD_SCRIPT, List.of(DIRTY_PRODUCT_ZSET),
                String.valueOf(productId), String.valueOf(cutoff));
    }

    public void evictProductCaches(Long productId) {
        redisCacheService.delete("product:detail:" + productId);
        redisCacheService.deleteByPattern("product:hot:*");
    }

    private String userSetKey(Long userId) {
        return USER_SET_PREFIX + userId;
    }

    private String userZSetKey(Long userId) {
        return USER_ZSET_PREFIX + userId;
    }

    private String productSetKey(Long productId) {
        return PRODUCT_SET_PREFIX + productId;
    }
}
