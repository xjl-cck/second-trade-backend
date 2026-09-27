package org.example.secondhandtradingplatform.config.rabbitmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMqProperties {

    private String orderExchange;
    private String orderQueue;
    private String orderRoutingKey;
    private String favoriteExchange;
    private String favoriteQueue;
    private String favoriteRoutingKey;
    private String chatExchange;
    private String chatQueue;
    private String chatRoutingKey;
    private String systemExchange;
    private String systemQueue;
    private String systemRoutingKey;
    private String deadLetterExchange;
    private String deadLetterQueue;
    private String deadLetterRoutingKey;
}
