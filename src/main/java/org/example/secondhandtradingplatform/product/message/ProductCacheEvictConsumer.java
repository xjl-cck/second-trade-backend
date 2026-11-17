package org.example.secondhandtradingplatform.product.message;

import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 商品缓存失效消费者：收到商品变更事件后统一清理详情缓存和榜单缓存。
 * <p>
 * 库存扣减/回补、商品状态变化都会走到这里，业务代码不再各自删缓存。
 * Redis 不可用时 RedisCacheService 内部只记日志不抛异常，不会让消息反复重投。
 */
@Component
public class ProductCacheEvictConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheEvictConsumer.class);

    private static final String PRODUCT_DETAIL_CACHE_PREFIX = "product:detail:";
    private static final String PRODUCT_HOT_CACHE_PREFIX = "product:hot:";

    private final RedisCacheService redisCacheService;

    public ProductCacheEvictConsumer(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    @RabbitListener(queues = "${app.rabbitmq.product-queue}")
    public void consume(ProductChangedEvent event) {
        if (event == null || event.getProductId() == null) {
            return;
        }
        redisCacheService.delete(PRODUCT_DETAIL_CACHE_PREFIX + event.getProductId());
        redisCacheService.deleteByPattern(PRODUCT_HOT_CACHE_PREFIX + "*");
        log.debug("Product cache evicted, productId={}, reason={}", event.getProductId(), event.getReason());
    }
}
