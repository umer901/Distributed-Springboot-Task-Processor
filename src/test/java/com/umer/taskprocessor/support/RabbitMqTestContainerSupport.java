package com.umer.taskprocessor.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

public interface RabbitMqTestContainerSupport {

    @Container
    RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine")
            .withUser("task_user", "task_password");

    static void registerRabbitMqProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "task_user");
        registry.add("spring.rabbitmq.password", () -> "task_password");
    }
}
