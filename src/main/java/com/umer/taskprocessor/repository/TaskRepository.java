package com.umer.taskprocessor.repository;

import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.domain.TaskStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
