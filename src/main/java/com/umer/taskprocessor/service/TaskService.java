package com.umer.taskprocessor.service;

import com.umer.taskprocessor.api.CreateTaskRequest;
import com.umer.taskprocessor.config.TaskProcessorProperties;
import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.domain.TaskStatus;
import com.umer.taskprocessor.idempotency.IdempotencyRecord;
import com.umer.taskprocessor.idempotency.NormalizedTaskRequest;
import com.umer.taskprocessor.idempotency.RequestHasher;
import com.umer.taskprocessor.repository.IdempotencyRepository;
import com.umer.taskprocessor.repository.OutboxRepository;
import com.umer.taskprocessor.repository.TaskRepository;
import com.umer.taskprocessor.web.IdempotencyConflictException;
import com.umer.taskprocessor.web.TaskNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final OutboxRepository outboxRepository;
    private final RequestHasher requestHasher;
    private final TaskProcessorProperties properties;
    private final Clock clock;

    public TaskService(
            TaskRepository taskRepository,
            IdempotencyRepository idempotencyRepository,
            OutboxRepository outboxRepository,
            RequestHasher requestHasher,
            TaskProcessorProperties properties,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.requestHasher = requestHasher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TaskCreationResult createTask(String idempotencyKey, CreateTaskRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        NormalizedTaskRequest normalized = requestHasher.normalize(request);
        String requestHash = requestHasher.hash(normalized);

        idempotencyRepository.lockKeyForTransaction(idempotencyKey);
        var existing = idempotencyRepository.findByKey(idempotencyKey);
        if (existing.isPresent() && existing.get().expiresAt().isAfter(now)) {
            return handleExistingIdempotencyKey(idempotencyKey, requestHash, existing.get());
        }
        existing.ifPresent(ignored -> idempotencyRepository.deleteExpiredKey(idempotencyKey, now));

        UUID taskId = UUID.randomUUID();
        TaskRecord task = new TaskRecord(
                taskId,
                TaskStatus.QUEUED,
                normalized.taskType(),
                normalized.payload(),
                null,
                null,
                null,
                normalized.priority(),
                normalized.maxAttempts(),
                0,
                normalized.timeoutSeconds(),
                now,
                null,
                null,
                null,
                null,
                0,
                now,
                now);

        taskRepository.insert(task);
        idempotencyRepository.insert(
                idempotencyKey,
                requestHash,
                taskId,
                now,
                now.plus(properties.idempotency().ttlDays(), ChronoUnit.DAYS));
        outboxRepository.insertTaskCreated(UUID.randomUUID(), taskId, outboxPayload(task, now), now);

        MDC.put("taskId", taskId.toString());
        try {
            log.info("Task accepted");
        } finally {
            MDC.remove("taskId");
        }
        return new TaskCreationResult(task, false);
    }

    @Transactional(readOnly = true)
    public TaskRecord getTask(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public TaskPage listTasks(TaskStatus status, String taskType, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;
        String normalizedType = taskType == null || taskType.isBlank()
                ? null
                : taskType.trim().toUpperCase(Locale.ROOT);
        return new TaskPage(
                taskRepository.list(status, normalizedType, safeSize, offset),
                safePage,
                safeSize,
                taskRepository.count(status, normalizedType));
    }

    @Transactional
    public TaskRecord cancelTask(UUID id) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        TaskRecord task = getTask(id);
        if (!task.status().isTerminal()) {
            taskRepository.cancelIfActive(id, now);
        }
        return getTask(id);
    }

    private TaskCreationResult handleExistingIdempotencyKey(
            String idempotencyKey,
            String requestHash,
            IdempotencyRecord existing) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        TaskRecord task = taskRepository.findById(existing.taskId())
                .orElseThrow(() -> new TaskNotFoundException(existing.taskId()));
        return new TaskCreationResult(task, true);
    }

    private Map<String, Object> outboxPayload(TaskRecord task, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.id().toString());
        payload.put("taskType", task.taskType());
        payload.put("status", task.status().name());
        payload.put("createdAt", now.toString());
        return payload;
    }
}
