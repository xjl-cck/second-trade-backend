package org.example.secondhandtradingplatform.favorite.job;

import org.example.secondhandtradingplatform.favorite.entity.Favorite;
import org.example.secondhandtradingplatform.favorite.mapper.FavoriteMapper;
import org.example.secondhandtradingplatform.favorite.service.FavoriteRedisService;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FavoriteFlushJob {

    private static final Logger log = LoggerFactory.getLogger(FavoriteFlushJob.class);

    private final FavoriteRedisService favoriteRedisService;
    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FavoriteFlushJob(FavoriteRedisService favoriteRedisService,
                            FavoriteMapper favoriteMapper,
                            ProductMapper productMapper) {
        this.favoriteRedisService = favoriteRedisService;
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    @Scheduled(
            fixedDelayString = "${app.favorite.flush-interval-ms:30000}",
            initialDelayString = "${app.favorite.initial-delay-ms:10000}"
    )
    public void flush() {
        long cutoff = System.currentTimeMillis();
        flushRelations(cutoff);
        flushCounts(cutoff);
    }

    private void flushRelations(long cutoff) {
        for (String relation : favoriteRedisService.dirtyRelations(cutoff)) {
            String[] parts = relation.split(":", 2);
            if (parts.length != 2) {
                favoriteRedisService.removeDirtyRelation(relation, cutoff);
                continue;
            }
            try {
                Long userId = Long.valueOf(parts[0]);
                Long productId = Long.valueOf(parts[1]);
                Favorite existing = favoriteMapper.selectIncludingDeleted(userId, productId);
                if (favoriteRedisService.isFavorite(userId, productId)) {
                    if (existing == null) {
                        Favorite favorite = new Favorite();
                        favorite.setUserId(userId);
                        favorite.setProductId(productId);
                        favorite.setIsDeleted(0);
                        favoriteMapper.insert(favorite);
                    } else if (Integer.valueOf(1).equals(existing.getIsDeleted())) {
                        favoriteMapper.updateDeleted(existing.getId(), 0);
                    }
                } else if (existing != null && Integer.valueOf(0).equals(existing.getIsDeleted())) {
                    favoriteMapper.updateDeleted(existing.getId(), 1);
                }
                favoriteRedisService.removeDirtyRelation(relation, cutoff);
            } catch (RuntimeException exception) {
                log.error("Favorite relation flush failed, relation={}", relation, exception);
            }
        }
    }

    private void flushCounts(long cutoff) {
        for (String value : favoriteRedisService.dirtyProducts(cutoff)) {
            try {
                Long productId = Long.valueOf(value);
                long count = favoriteRedisService.productFavoriteCount(productId);
                productMapper.syncFavoriteCount(productId, count);
                favoriteRedisService.evictProductCaches(productId);
                favoriteRedisService.removeDirtyProduct(productId, cutoff);
            } catch (RuntimeException exception) {
                log.error("Favorite count flush failed, productId={}", value, exception);
            }
        }
    }
}
