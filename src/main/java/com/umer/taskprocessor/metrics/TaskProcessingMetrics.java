package com.umer.taskprocessor.metrics;

import com.umer.taskprocessor.repository.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TaskProcessingMetrics {

    private final Counter outboxPublished;
    private final Counter outboxPublishFailed;
    private final Counter taskSucceeded;
    private final Counter taskFailed;
    private final Counter taskRetried;
    private final Counter taskTimedOut;
    private final Counter duplicateOrStaleMessage;

    public TaskProcessingMetrics(MeterRegistry meterRegistry, OutboxRepository outboxRepository) {
        this.outboxPublished = Counter.builder("task_processor_outbox_published_total")
                .description("Outbox events published to RabbitMQ")
                .register(meterRegistry);
        this.outboxPublishFailed = Counter.builder("task_processor_outbox_publish_failed_total")
                .description("Outbox publish failures")
                .register(meterRegistry);
        this.taskSucceeded = Counter.builder("task_processor_tasks_succeeded_total")
                .description("Tasks completed successfully")
                .register(meterRegistry);
        this.taskFailed = Counter.builder("task_processor_tasks_failed_total")
                .description("Tasks that reached FAILED")
                .register(meterRegistry);
        this.taskRetried = Counter.builder("task_processor_tasks_retried_total")
                .description("Task attempts scheduled for retry")
                .register(meterRegistry);
        this.taskTimedOut = Counter.builder("task_processor_tasks_timed_out_total")
                .description("Tasks that reached TIMED_OUT")
                .register(meterRegistry);
        this.duplicateOrStaleMessage = Counter.builder("task_processor_messages_stale_total")
                .description("RabbitMQ messages ignored because the task was not claimable")
                .register(meterRegistry);
        Gauge.builder("task_processor_outbox_pending", outboxRepository, OutboxRepository::countPending)
                .description("Pending outbox rows waiting to be published")
                .register(meterRegistry);
    }

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void outboxPublishFailed() {
        outboxPublishFailed.increment();
    }

    public void taskSucceeded() {
        taskSucceeded.increment();
    }

    public void taskFailed() {
        taskFailed.increment();
    }

    public void taskRetried() {
        taskRetried.increment();
    }

    public void taskTimedOut() {
        taskTimedOut.increment();
    }

    public void duplicateOrStaleMessage() {
        duplicateOrStaleMessage.increment();
    }
}
