package org.example.secondhandtradingplatform.favorite.message;

import org.example.secondhandtradingplatform.order.config.OrderRabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class FavoriteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FavoriteEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderRabbitProperties properties;

    public FavoriteEventPublisher(RabbitTemplate rabbitTemplate, OrderRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publishAfterCommit(Long productId, int delta) {
        FavoriteCountEvent event = new FavoriteCountEvent();
        event.setProductId(productId);
        event.setDelta(delta);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            return;
        }
        publish(event);
    }

    private void publish(FavoriteCountEvent event) {
        try {
            rabbitTemplate.convertAndSend(properties.getFavoriteExchange(), properties.getFavoriteRoutingKey(), event);
        } catch (RuntimeException exception) {
            log.warn("Favorite event publish failed, productId={}", event.getProductId(), exception);
        }
    }
}
