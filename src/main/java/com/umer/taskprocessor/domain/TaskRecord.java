package com.umer.taskprocessor.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskRecord(
        UUID id,
        TaskStatus status,
        String taskType,
        Map<String, Object> payload,
        Map<String, Object> result,
        String failureCode,
        String failureMessage,
        int priority,
        int maxAttempts,
        int attemptCount,
        int timeoutSeconds,
        Instant availableAt,
        Instant startedAt,
        Instant completedAt,
        String lockOwner,
        Instant lockExpiresAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
