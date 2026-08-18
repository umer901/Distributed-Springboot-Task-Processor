package com.umer.taskprocessor.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxRecord(
        UUID id,
        UUID taskId,
        String eventType,
        Map<String, Object> payload,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt) {
}
