package org.example.secondhandtradingplatform.product.message;

import org.example.secondhandtradingplatform.config.rabbitmq.RabbitMqProperties;
import org.example.secondhandtradingplatform.outbox.OutboxService;
import org.springframework.stereotype.Component;

/**
 * 商品变更事件发布器。
 * <p>
 * 只把事件写进 outbox（与业务数据同事务），实际投递、确认和重试由 OutboxRelay 负责，
 * 所以订单扣库存/回补库存的事务提交成功，缓存失效事件一定不会丢。
 */
@Component
public class ProductEventPublisher {

    private final OutboxService outboxService;
    private final RabbitMqProperties properties;

    public ProductEventPublisher(OutboxService outboxService, RabbitMqProperties properties) {
        this.outboxService = outboxService;
        this.properties = properties;
    }

    /**
     * 必须在业务事务内调用，事件才能与库存变更一起提交。
     */
    public void publish(Long productId, String reason) {
        if (productId == null) {
            return;
        }
        outboxService.record(
                properties.getProductExchange(),
                properties.getProductRoutingKey(),
                ProductChangedEvent.of(productId, reason));
    }
}
