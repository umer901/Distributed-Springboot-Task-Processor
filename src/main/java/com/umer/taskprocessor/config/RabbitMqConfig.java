package com.umer.taskprocessor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    DirectExchange taskExchange(TaskProcessorProperties properties) {
        return new DirectExchange(properties.rabbitmq().exchange(), true, false);
    }

    @Bean
    Queue taskQueue(TaskProcessorProperties properties) {
        return new Queue(properties.rabbitmq().queue(), true);
    }

    @Bean
    Queue deadLetterQueue(TaskProcessorProperties properties) {
        return new Queue(properties.rabbitmq().deadLetterQueue(), true);
    }

    @Bean
    Binding taskBinding(Queue taskQueue, DirectExchange taskExchange, TaskProcessorProperties properties) {
        return BindingBuilder.bind(taskQueue).to(taskExchange).with(properties.rabbitmq().routingKey());
    }

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            TaskProcessorProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(properties.worker().prefetch());
        return factory;
    }
}
