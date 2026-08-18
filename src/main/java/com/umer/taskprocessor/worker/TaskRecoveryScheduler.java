package com.umer.taskprocessor.worker;

import com.umer.taskprocessor.config.TaskProcessorProperties;
import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.metrics.TaskProcessingMetrics;
import com.umer.taskprocessor.repository.MaintenanceLockRepository;
import com.umer.taskprocessor.repository.OutboxRepository;
import com.umer.taskprocessor.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "task-processor.runtime", name = "worker-enabled", havingValue = "true")
public class TaskRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryScheduler.class);

    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final MaintenanceLockRepository lockRepository;
    private final RetryPolicy retryPolicy;
    private final TaskProcessorProperties properties;
    private final TaskProcessingMetrics metrics;
    private final Clock clock;

    public TaskRecoveryScheduler(
            TaskRepository taskRepository,
            OutboxRepository outboxRepository,
            MaintenanceLockRepository lockRepository,
            RetryPolicy retryPolicy,
            TaskProcessorProperties properties,
            TaskProcessingMetrics metrics,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.lockRepository = lockRepository;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${task-processor.worker.recovery-delay-millis}")
    @Transactional
    public void recoverExpiredRunningTasks() {
        if (!lockRepository.tryTransactionLock("task-processor:timeout-recovery")) {
            return;
        }
        Instant now = clock.instant();
        for (TaskRecord task : taskRepository.findExpiredRunning(now, properties.worker().recoveryBatchSize())) {
            recoverOne(task, now);
        }
    }

    private void recoverOne(TaskRecord task, Instant now) {
        String message = "Task lock expired for worker " + task.lockOwner();
        if (retryPolicy.shouldRetry(task.attemptCount(), task.maxAttempts(), true)) {
            Instant retryAt = now.plus(retryPolicy.nextDelay(task.attemptCount()));
            taskRepository.markAttemptFailed(task.id(), task.attemptCount(), "TIMED_OUT", "TASK_ATTEMPT_TIMED_OUT", message, now);
            boolean scheduled = taskRepository.markExpiredRetryScheduled(
                    task.id(),
                    "TASK_ATTEMPT_TIMED_OUT",
                    message,
                    retryAt,
                    task.lockOwner(),
                    now);
            if (scheduled) {
                outboxRepository.insertTaskDispatch(
                        UUID.randomUUID(),
                        task.id(),
                        dispatchPayload(task, "RETRY_SCHEDULED", now),
                        retryAt,
                        now);
                metrics.taskRetried();
                log.info("Expired task attempt recovered and scheduled for retry");
            }
            return;
        }

        taskRepository.markAttemptFailed(task.id(), task.attemptCount(), "TIMED_OUT", "TASK_TIMED_OUT", message, now);
        taskRepository.markTimedOut(task.id(), message, now);
        metrics.taskTimedOut();
        log.info("Expired task marked timed out");
    }

    private Map<String, Object> dispatchPayload(TaskRecord task, String status, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.id().toString());
        payload.put("taskType", task.taskType());
        payload.put("status", status);
        payload.put("createdAt", now.toString());
        return payload;
    }
}
