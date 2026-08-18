package com.umer.taskprocessor.worker;

import com.umer.taskprocessor.config.TaskProcessorProperties;
import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.metrics.TaskProcessingMetrics;
import com.umer.taskprocessor.repository.OutboxRepository;
import com.umer.taskprocessor.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskWorkerService {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkerService.class);

    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final TaskHandlerRegistry handlerRegistry;
    private final RetryPolicy retryPolicy;
    private final TaskProcessorProperties properties;
    private final TaskProcessingMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public TaskWorkerService(
            TaskRepository taskRepository,
            OutboxRepository outboxRepository,
            TaskHandlerRegistry handlerRegistry,
            RetryPolicy retryPolicy,
            TaskProcessorProperties properties,
            TaskProcessingMetrics metrics,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.handlerRegistry = handlerRegistry;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public void process(UUID taskId) {
        String workerId = properties.worker().id();
        try (MdcScope ignored = withTaskMdc(taskId)) {
            Optional<TaskRecord> claimed = claim(taskId, workerId);
            if (claimed.isEmpty()) {
                metrics.duplicateOrStaleMessage();
                log.info("Task message ignored because task is not claimable");
                return;
            }

            TaskRecord task = claimed.get();
            try {
                TaskExecutionResult result = handlerRegistry.get(task.taskType()).execute(task);
                complete(task, result, workerId);
            } catch (TaskExecutionException ex) {
                fail(task, workerId, ex.code(), ex.getMessage(), ex.retryable());
            } catch (RuntimeException ex) {
                fail(task, workerId, "TASK_HANDLER_ERROR", ex.getMessage(), true);
            }
        }
    }

    private Optional<TaskRecord> claim(UUID taskId, String workerId) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            Optional<TaskRecord> claimed = taskRepository.claimForExecution(taskId, workerId, now);
            claimed.ifPresent(task -> taskRepository.insertAttemptRunning(
                    task.id(),
                    task.attemptCount(),
                    workerId,
                    now));
            return claimed;
        });
    }

    private void complete(TaskRecord task, TaskExecutionResult result, String workerId) {
        Boolean updated = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            boolean completed = taskRepository.markSucceeded(task.id(), result.result(), workerId, now);
            if (completed) {
                taskRepository.markAttemptSucceeded(task.id(), task.attemptCount(), now);
            }
            return completed;
        });
        if (Boolean.TRUE.equals(updated)) {
            metrics.taskSucceeded();
            log.info("Task succeeded");
        } else {
            metrics.duplicateOrStaleMessage();
            log.info("Task completion ignored because ownership expired");
        }
    }

    private void fail(TaskRecord task, String workerId, String code, String message, boolean retryable) {
        Boolean updated = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            boolean shouldRetry = retryPolicy.shouldRetry(task.attemptCount(), task.maxAttempts(), retryable);
            if (shouldRetry) {
                Instant retryAt = now.plus(retryPolicy.nextDelay(task.attemptCount()));
                boolean scheduled = taskRepository.markRetryScheduled(
                        task.id(),
                        code,
                        message,
                        retryAt,
                        workerId,
                        now);
                if (scheduled) {
                    taskRepository.markAttemptFailed(task.id(), task.attemptCount(), "FAILED", code, message, now);
                    outboxRepository.insertTaskDispatch(
                            UUID.randomUUID(),
                            task.id(),
                            dispatchPayload(task.id(), task.taskType(), "RETRY_SCHEDULED", now),
                            retryAt,
                            now);
                }
                return scheduled;
            }

            boolean failed = taskRepository.markFailed(task.id(), code, message, workerId, now);
            if (failed) {
                taskRepository.markAttemptFailed(task.id(), task.attemptCount(), "FAILED", code, message, now);
            }
            return failed;
        });

        if (Boolean.TRUE.equals(updated)) {
            if (retryPolicy.shouldRetry(task.attemptCount(), task.maxAttempts(), retryable)) {
                metrics.taskRetried();
                log.info("Task retry scheduled");
            } else {
                metrics.taskFailed();
                log.info("Task failed");
            }
        } else {
            metrics.duplicateOrStaleMessage();
            log.info("Task failure handling ignored because ownership expired");
        }
    }

    private Map<String, Object> dispatchPayload(UUID taskId, String taskType, String status, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId.toString());
        payload.put("taskType", taskType);
        payload.put("status", status);
        payload.put("createdAt", now.toString());
        return payload;
    }

    private MdcScope withTaskMdc(UUID taskId) {
        MDC.put("taskId", taskId.toString());
        MDC.put("workerId", properties.worker().id());
        return new MdcScope();
    }

    private static final class MdcScope implements AutoCloseable {

        @Override
        public void close() {
            MDC.remove("taskId");
            MDC.remove("workerId");
        }
    }
}
