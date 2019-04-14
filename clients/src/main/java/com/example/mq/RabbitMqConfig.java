package com.example.mq;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.example.mq")
public class RabbitMqConfig {
    private static final String SIMPLE_MESSAGE_QUEUE = "cryptoleaguecorda.outbound";

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");
        connectionFactory.setUsername("clcorda");
        connectionFactory.setPassword("clcorda");
        return connectionFactory;
    }

    @Bean
    public Queue simpleQueue() {
        return new Queue(SIMPLE_MESSAGE_QUEUE);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory());
        template.setRoutingKey(SIMPLE_MESSAGE_QUEUE);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
