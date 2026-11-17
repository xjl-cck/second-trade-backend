package org.example.secondhandtradingplatform.product.message;

import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductCacheEvictConsumerTest {

    private RedisCacheService redisCacheService;
    private ProductCacheEvictConsumer consumer;

    @BeforeEach
    void setUp() {
        redisCacheService = mock(RedisCacheService.class);
        consumer = new ProductCacheEvictConsumer(redisCacheService);
    }

    @Test
    @DisplayName("收到商品变更事件后清理详情缓存和榜单缓存")
    void evictsCaches() {
        consumer.consume(ProductChangedEvent.of(7L, ProductChangedEvent.REASON_STOCK_DEDUCTED));

        verify(redisCacheService).delete("product:detail:7");
        verify(redisCacheService).deleteByPattern("product:hot:*");
    }

    @Test
    @DisplayName("空事件或缺少商品 ID 时不做任何缓存操作")
    void ignoresInvalidEvent() {
        consumer.consume(null);
        consumer.consume(new ProductChangedEvent());

        verifyNoInteractions(redisCacheService);
        verify(redisCacheService, never()).delete("product:detail:null");
    }
}
