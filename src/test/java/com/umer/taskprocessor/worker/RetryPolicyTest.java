package com.umer.taskprocessor.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.umer.taskprocessor.config.TaskProcessorProperties;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy(new TaskProcessorProperties(
            new TaskProcessorProperties.Runtime(true, true, true, true),
            new TaskProcessorProperties.Idempotency(7),
            new TaskProcessorProperties.Rabbitmq("exchange", "queue", "key", "dead"),
            new TaskProcessorProperties.Outbox(25, 1000),
            new TaskProcessorProperties.Worker("worker-1", 4, 25, 5000),
            new TaskProcessorProperties.Retry(1000, 5000, 2.0)));

    @Test
    void nextDelayUsesExponentialBackoffWithCap() {
        assertThat(retryPolicy.nextDelay(1)).hasMillis(1000);
        assertThat(retryPolicy.nextDelay(2)).hasMillis(2000);
        assertThat(retryPolicy.nextDelay(3)).hasMillis(4000);
        assertThat(retryPolicy.nextDelay(4)).hasMillis(5000);
    }

    @Test
    void shouldRetryOnlyWhenRetryableAndAttemptsRemain() {
        assertThat(retryPolicy.shouldRetry(1, 3, true)).isTrue();
        assertThat(retryPolicy.shouldRetry(3, 3, true)).isFalse();
        assertThat(retryPolicy.shouldRetry(1, 3, false)).isFalse();
    }
}
