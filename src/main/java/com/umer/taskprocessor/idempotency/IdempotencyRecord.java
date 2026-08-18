package com.umer.taskprocessor.idempotency;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
        String idempotencyKey,
        String requestHash,
        UUID taskId,
        Instant createdAt,
        Instant expiresAt) {
}
