package com.umer.taskprocessor.worker;

import com.umer.taskprocessor.config.TaskProcessorProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private final TaskProcessorProperties properties;

    public RetryPolicy(TaskProcessorProperties properties) {
        this.properties = properties;
    }

    public Duration nextDelay(int completedAttemptCount) {
        long initial = properties.retry().initialBackoffMillis();
        long max = properties.retry().maxBackoffMillis();
        double multiplier = properties.retry().multiplier();
        double raw = initial * Math.pow(multiplier, Math.max(0, completedAttemptCount - 1));
        return Duration.ofMillis(Math.min((long) raw, max));
    }

    public boolean shouldRetry(int completedAttemptCount, int maxAttempts, boolean retryable) {
        return retryable && completedAttemptCount < maxAttempts;
    }
}
