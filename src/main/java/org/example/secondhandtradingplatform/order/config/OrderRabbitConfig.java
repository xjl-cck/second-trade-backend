package org.example.secondhandtradingplatform.order.config;

import org.example.secondhandtradingplatform.order.message.OrderStatusEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitConfig {

    private final OrderRabbitProperties properties;

    public OrderRabbitConfig(OrderRabbitProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(properties.getOrderExchange(), true, false);
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(properties.getOrderQueue(), true);
    }

    @Bean
    public Binding orderBinding(DirectExchange orderExchange, Queue orderQueue) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(properties.getOrderRoutingKey());
    }

    @Bean
    public DirectExchange favoriteExchange() {
        return new DirectExchange(properties.getFavoriteExchange(), true, false);
    }

    @Bean
    public Queue favoriteQueue() {
        return new Queue(properties.getFavoriteQueue(), true);
    }

    @Bean
    public Binding favoriteBinding(DirectExchange favoriteExchange, Queue favoriteQueue) {
        return BindingBuilder.bind(favoriteQueue).to(favoriteExchange).with(properties.getFavoriteRoutingKey());
    }

    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(properties.getChatExchange(), true, false);
    }

    @Bean
    public Queue chatQueue() {
        return new Queue(properties.getChatQueue(), true);
    }

    @Bean
    public Binding chatBinding(DirectExchange chatExchange, Queue chatQueue) {
        return BindingBuilder.bind(chatQueue).to(chatExchange).with(properties.getChatRoutingKey());
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
