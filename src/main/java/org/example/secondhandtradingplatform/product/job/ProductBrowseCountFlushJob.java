package org.example.secondhandtradingplatform.product.job;

import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.product.dto.BrowseCountIncrement;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductBrowseCountFlushJob {

    private static final Logger log = LoggerFactory.getLogger(ProductBrowseCountFlushJob.class);
    private static final String PRODUCT_BROWSE_COUNT_PREFIX = "product:browse:count:";

    private final RedisCacheService redisCacheService;
    private final ProductMapper productMapper;

    public ProductBrowseCountFlushJob(RedisCacheService redisCacheService, ProductMapper productMapper) {
        this.redisCacheService = redisCacheService;
        this.productMapper = productMapper;
    }

    @Scheduled(
            fixedDelayString = "${app.browse-count.flush-interval-ms:60000}",
            initialDelayString = "${app.browse-count.initial-delay-ms:10000}"
    )
    public void flush() {
        Set<String> keys = redisCacheService.scanKeys(PRODUCT_BROWSE_COUNT_PREFIX + "*");
        List<BrowseCountIncrement> increments = new ArrayList<>();
        for (String key : keys) {
            Long productId = parseProductId(key);
            if (productId == null) {
                continue;
            }

            long delta = redisCacheService.getAndReset(key);
            if (delta <= 0) {
                continue;
            }
            increments.add(new BrowseCountIncrement(productId, delta));
        }
        if (increments.isEmpty()) {
            return;
        }

        try {
            productMapper.batchIncrementBrowseCount(increments);
            for (BrowseCountIncrement increment : increments) {
                redisCacheService.delete("product:detail:" + increment.getProductId());
            }
            redisCacheService.deleteByPattern("product:hot:*");
        } catch (RuntimeException exception) {
            for (BrowseCountIncrement increment : increments) {
                redisCacheService.increment(PRODUCT_BROWSE_COUNT_PREFIX + increment.getProductId(), increment.getDelta());
            }
            log.error("Product browse count batch flush failed, count={}", increments.size(), exception);
        }
    }

    private Long parseProductId(String key) {
        try {
            return Long.valueOf(key.substring(PRODUCT_BROWSE_COUNT_PREFIX.length()));
        } catch (RuntimeException exception) {
            log.warn("Invalid product browse count key={}", key);
            return null;
        }
    }
}
