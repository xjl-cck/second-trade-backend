package org.example.secondhandtradingplatform.config.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    private final RabbitMqProperties properties;

    public RabbitMqConfig(RabbitMqProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(properties.getOrderExchange(), true, false);
    }

    @Bean
    public Queue orderQueue() {
        return durableQueueWithDeadLetter(properties.getOrderQueue());
    }

    @Bean
    public Binding orderBinding(@Qualifier("orderExchange") DirectExchange orderExchange,
                                @Qualifier("orderQueue") Queue orderQueue) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(properties.getOrderRoutingKey());
    }

    @Bean
    public DirectExchange favoriteExchange() {
        return new DirectExchange(properties.getFavoriteExchange(), true, false);
    }

    @Bean
    public Queue favoriteQueue() {
        return durableQueueWithDeadLetter(properties.getFavoriteQueue());
    }

    @Bean
    public Binding favoriteBinding(@Qualifier("favoriteExchange") DirectExchange favoriteExchange,
                                   @Qualifier("favoriteQueue") Queue favoriteQueue) {
        return BindingBuilder.bind(favoriteQueue).to(favoriteExchange).with(properties.getFavoriteRoutingKey());
    }

    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(properties.getChatExchange(), true, false);
    }

    @Bean
    public Queue chatQueue() {
        return durableQueueWithDeadLetter(properties.getChatQueue());
    }

    @Bean
    public Binding chatBinding(@Qualifier("chatExchange") DirectExchange chatExchange,
                               @Qualifier("chatQueue") Queue chatQueue) {
        return BindingBuilder.bind(chatQueue).to(chatExchange).with(properties.getChatRoutingKey());
    }

    @Bean
    public DirectExchange systemExchange() {
        return new DirectExchange(properties.getSystemExchange(), true, false);
    }

    @Bean
    public Queue systemQueue() {
        return durableQueueWithDeadLetter(properties.getSystemQueue());
    }

    @Bean
    public Binding systemBinding(@Qualifier("systemExchange") DirectExchange systemExchange,
                                 @Qualifier("systemQueue") Queue systemQueue) {
        return BindingBuilder.bind(systemQueue).to(systemExchange).with(properties.getSystemRoutingKey());
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding deadLetterBinding(@Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                                    @Qualifier("deadLetterQueue") Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public RepublishMessageRecoverer rabbitMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(
                rabbitTemplate,
                properties.getDeadLetterExchange(),
                properties.getDeadLetterRoutingKey());
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        // 路由不到队列时让 broker 退回消息，outbox 才能把这种“发出去但没人收”的情况判为失败并重试
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    private Queue durableQueueWithDeadLetter(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", properties.getDeadLetterExchange(),
                        "x-dead-letter-routing-key", properties.getDeadLetterRoutingKey()))
                .build();
    }
}
