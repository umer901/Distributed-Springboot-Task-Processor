package com.umer.taskprocessor.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public interface RabbitMqTestContainerSupport {

    @Container
    GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "task_user")
            .withEnv("RABBITMQ_DEFAULT_PASS", "task_password")
            .withExposedPorts(5672, 15672);

    static void registerRabbitMqProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "task_user");
        registry.add("spring.rabbitmq.password", () -> "task_password");
    }
}
