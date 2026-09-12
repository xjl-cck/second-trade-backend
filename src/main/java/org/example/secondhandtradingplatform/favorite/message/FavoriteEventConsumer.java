package org.example.secondhandtradingplatform.favorite.message;

import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FavoriteEventConsumer {

    private final ProductMapper productMapper;
    private final RedisCacheService redisCacheService;

    public FavoriteEventConsumer(ProductMapper productMapper, RedisCacheService redisCacheService) {
        this.productMapper = productMapper;
        this.redisCacheService = redisCacheService;
    }

    @RabbitListener(queues = "${app.rabbitmq.favorite-queue}")
    public void consume(FavoriteCountEvent event) {
        productMapper.adjustFavoriteCount(event.getProductId(), event.getDelta());
        redisCacheService.delete("product:detail:" + event.getProductId());
        redisCacheService.deleteByPattern("product:hot:*");
    }
}
