package com.umer.taskprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task-processor")
public record TaskProcessorProperties(Idempotency idempotency, Rabbitmq rabbitmq) {

    public record Idempotency(int ttlDays) {
    }

    public record Rabbitmq(String exchange, String queue, String routingKey) {
    }
}
