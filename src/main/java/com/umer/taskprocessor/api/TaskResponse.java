package com.umer.taskprocessor.api;

import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.domain.TaskStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskResponse(
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
        Instant createdAt,
        Instant updatedAt,
        boolean idempotentReplay) {

    public static TaskResponse from(TaskRecord task, boolean idempotentReplay) {
        return new TaskResponse(
                task.id(),
                task.status(),
                task.taskType(),
                task.payload(),
                task.result(),
                task.failureCode(),
                task.failureMessage(),
                task.priority(),
                task.maxAttempts(),
                task.attemptCount(),
                task.timeoutSeconds(),
                task.availableAt(),
                task.startedAt(),
                task.completedAt(),
                task.createdAt(),
                task.updatedAt(),
                idempotentReplay);
    }
}
