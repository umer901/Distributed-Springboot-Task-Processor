package com.umer.taskprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task-processor")
public record TaskProcessorProperties(
        Runtime runtime,
        Idempotency idempotency,
        Rabbitmq rabbitmq,
        Outbox outbox,
        Worker worker,
        Retry retry) {

    public record Runtime(boolean apiEnabled, boolean workerEnabled, boolean outboxEnabled, boolean schedulingEnabled) {
    }

    public record Idempotency(int ttlDays) {
    }

    public record Rabbitmq(String exchange, String queue, String routingKey, String deadLetterQueue) {
    }

    public record Outbox(int batchSize, long publishDelayMillis) {
    }

    public record Worker(String id, int prefetch, int recoveryBatchSize, long recoveryDelayMillis) {
    }

    public record Retry(long initialBackoffMillis, long maxBackoffMillis, double multiplier) {
    }
}
