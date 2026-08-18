package com.umer.taskprocessor.repository;

import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.domain.TaskStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JsonbMapper jsonbMapper;
    private final RowMapper<TaskRecord> taskRowMapper = this::mapTask;

    public TaskRepository(JdbcTemplate jdbcTemplate, JsonbMapper jsonbMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonbMapper = jsonbMapper;
    }

    public void insert(TaskRecord task) {
        jdbcTemplate.update(
                """
                INSERT INTO tasks
                    (id, status, task_type, payload, priority, max_attempts, attempt_count, timeout_seconds,
                     available_at, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                """,
                task.id(),
                task.status().name(),
                task.taskType(),
                jsonbMapper.toJson(task.payload()),
                task.priority(),
                task.maxAttempts(),
                task.attemptCount(),
                task.timeoutSeconds(),
                Timestamp.from(task.availableAt()),
                Timestamp.from(task.createdAt()),
                Timestamp.from(task.updatedAt()));
    }

    public Optional<TaskRecord> findById(UUID id) {
        List<TaskRecord> rows = jdbcTemplate.query(baseSelect() + " WHERE id = ?", taskRowMapper, id);
        return rows.stream().findFirst();
    }

    public List<TaskRecord> list(TaskStatus status, String taskType, int limit, int offset) {
        QueryParts query = buildFilteredQuery(status, taskType, false);
        query.sql().append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        query.params().add(limit);
        query.params().add(offset);
        return jdbcTemplate.query(query.sql().toString(), taskRowMapper, query.params().toArray());
    }

    public long count(TaskStatus status, String taskType) {
        QueryParts query = buildFilteredQuery(status, taskType, true);
        Long count = jdbcTemplate.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return count == null ? 0 : count;
    }

    public boolean cancelIfActive(UUID id, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tasks
                SET status = 'CANCELLED',
                    completed_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status IN ('QUEUED', 'RUNNING', 'RETRY_SCHEDULED')
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                id);
        return updated == 1;
    }

    public Optional<TaskRecord> claimForExecution(UUID id, String workerId, Instant now) {
        List<TaskRecord> rows = jdbcTemplate.query(
                """
                UPDATE tasks
                SET status = 'RUNNING',
                    attempt_count = attempt_count + 1,
                    started_at = ?,
                    lock_owner = ?,
                    lock_expires_at = ?::timestamptz + make_interval(secs => timeout_seconds),
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status IN ('QUEUED', 'RETRY_SCHEDULED')
                  AND available_at <= ?
                RETURNING id, status, task_type, payload, result, failure_code, failure_message, priority,
                          max_attempts, attempt_count, timeout_seconds, available_at, started_at, completed_at,
                          lock_owner, lock_expires_at, version, created_at, updated_at
                """,
                taskRowMapper,
                Timestamp.from(now),
                workerId,
                Timestamp.from(now),
                Timestamp.from(now),
                id,
                Timestamp.from(now));
        return rows.stream().findFirst();
    }

    public void insertAttemptRunning(UUID taskId, int attemptNumber, String workerId, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO task_attempts (task_id, attempt_number, status, worker_id, started_at)
                VALUES (?, ?, 'RUNNING', ?, ?)
                ON CONFLICT (task_id, attempt_number) DO NOTHING
                """,
                taskId,
                attemptNumber,
                workerId,
                Timestamp.from(now));
    }

    public boolean markSucceeded(UUID id, Map<String, Object> result, String workerId, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tasks
                SET status = 'SUCCEEDED',
                    result = ?::jsonb,
                    completed_at = ?,
                    lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lock_owner = ?
                  AND lock_expires_at > ?
                """,
                jsonbMapper.toJson(result),
                Timestamp.from(now),
                Timestamp.from(now),
                id,
                workerId,
                Timestamp.from(now));
        return updated == 1;
    }

    public void markAttemptSucceeded(UUID taskId, int attemptNumber, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE task_attempts
                SET status = 'SUCCEEDED',
                    finished_at = ?
                WHERE task_id = ?
                  AND attempt_number = ?
                """,
                Timestamp.from(now),
                taskId,
                attemptNumber);
    }

    public boolean markRetryScheduled(
            UUID id,
            String failureCode,
            String failureMessage,
            Instant retryAt,
            String workerId,
            Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tasks
                SET status = 'RETRY_SCHEDULED',
                    failure_code = ?,
                    failure_message = ?,
                    available_at = ?,
                    lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lock_owner = ?
                  AND lock_expires_at > ?
                """,
                failureCode,
                failureMessage,
                Timestamp.from(retryAt),
                Timestamp.from(now),
                id,
                workerId,
                Timestamp.from(now));
        return updated == 1;
    }

    public boolean markFailed(UUID id, String failureCode, String failureMessage, String workerId, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tasks
                SET status = 'FAILED',
                    failure_code = ?,
                    failure_message = ?,
                    completed_at = ?,
                    lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lock_owner = ?
                  AND lock_expires_at > ?
                """,
                failureCode,
                failureMessage,
                Timestamp.from(now),
                Timestamp.from(now),
                id,
                workerId,
                Timestamp.from(now));
        return updated == 1;
    }

    public void markTimedOut(UUID id, String failureMessage, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE tasks
                SET status = 'TIMED_OUT',
                    failure_code = 'TASK_TIMED_OUT',
                    failure_message = ?,
                    completed_at = ?,
                    lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                """,
                failureMessage,
                Timestamp.from(now),
                Timestamp.from(now),
                id);
    }

    public boolean markExpiredRetryScheduled(
            UUID id,
            String failureCode,
            String failureMessage,
            Instant retryAt,
            String previousWorkerId,
            Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tasks
                SET status = 'RETRY_SCHEDULED',
                    failure_code = ?,
                    failure_message = ?,
                    available_at = ?,
                    lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lock_owner = ?
                  AND lock_expires_at <= ?
                """,
                failureCode,
                failureMessage,
                Timestamp.from(retryAt),
                Timestamp.from(now),
                id,
                previousWorkerId,
                Timestamp.from(now));
        return updated == 1;
    }

    public void markAttemptFailed(
            UUID taskId,
            int attemptNumber,
            String status,
            String errorCode,
            String errorMessage,
            Instant now) {
        jdbcTemplate.update(
                """
                UPDATE task_attempts
                SET status = ?,
                    finished_at = ?,
                    error_code = ?,
                    error_message = ?
                WHERE task_id = ?
                  AND attempt_number = ?
                """,
                status,
                Timestamp.from(now),
                errorCode,
                errorMessage,
                taskId,
                attemptNumber);
    }

    public List<TaskRecord> findExpiredRunning(Instant now, int limit) {
        return jdbcTemplate.query(
                baseSelect()
                        + """
                         WHERE status = 'RUNNING'
                           AND lock_expires_at <= ?
                         ORDER BY lock_expires_at
                         LIMIT ?
                         FOR UPDATE SKIP LOCKED
                        """,
                taskRowMapper,
                Timestamp.from(now),
                limit);
    }

    private QueryParts buildFilteredQuery(TaskStatus status, String taskType, boolean count) {
        StringBuilder sql = new StringBuilder(count ? "SELECT count(*) FROM tasks" : baseSelect());
        List<Object> params = new ArrayList<>();
        List<String> filters = new ArrayList<>();
        if (status != null) {
            filters.add("status = ?");
            params.add(status.name());
        }
        if (taskType != null && !taskType.isBlank()) {
            filters.add("task_type = ?");
            params.add(taskType.trim().toUpperCase());
        }
        if (!filters.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", filters));
        }
        return new QueryParts(sql, params);
    }

    private String baseSelect() {
        return """
                SELECT id, status, task_type, payload, result, failure_code, failure_message, priority,
                       max_attempts, attempt_count, timeout_seconds, available_at, started_at, completed_at,
                       lock_owner, lock_expires_at, version, created_at, updated_at
                FROM tasks
                """;
    }

    private TaskRecord mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskRecord(
                rs.getObject("id", UUID.class),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getString("task_type"),
                jsonbMapper.readMap(rs, "payload"),
                jsonbMapper.readMap(rs, "result"),
                rs.getString("failure_code"),
                rs.getString("failure_message"),
                rs.getInt("priority"),
                rs.getInt("max_attempts"),
                rs.getInt("attempt_count"),
                rs.getInt("timeout_seconds"),
                instantOrNull(rs, "available_at"),
                instantOrNull(rs, "started_at"),
                instantOrNull(rs, "completed_at"),
                rs.getString("lock_owner"),
                instantOrNull(rs, "lock_expires_at"),
                rs.getLong("version"),
                instantOrNull(rs, "created_at"),
                instantOrNull(rs, "updated_at"));
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QueryParts(StringBuilder sql, List<Object> params) {
    }
}
