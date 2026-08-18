package com.umer.taskprocessor.idempotency;

import java.util.Map;

public record NormalizedTaskRequest(
        String taskType,
        Map<String, Object> payload,
        int priority,
        int maxAttempts,
        int timeoutSeconds) {
}
