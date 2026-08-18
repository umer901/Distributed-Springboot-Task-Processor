package com.umer.taskprocessor.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.umer.taskprocessor.outbox.OutboxRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JsonbMapper jsonbMapper;

    public OutboxRepository(JdbcTemplate jdbcTemplate, JsonbMapper jsonbMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonbMapper = jsonbMapper;
    }

    public void insertTaskCreated(UUID outboxId, UUID taskId, Map<String, Object> payload, Instant now) {
        insert(outboxId, taskId, "TASK_CREATED", payload, now, now);
    }

    public void insertTaskDispatch(UUID outboxId, UUID taskId, Map<String, Object> payload, Instant nextAttemptAt, Instant now) {
        insert(outboxId, taskId, "TASK_DISPATCH", payload, nextAttemptAt, now);
    }

    public List<OutboxRecord> findDuePending(int limit, Instant now) {
        return jdbcTemplate.query(
                """
                SELECT id, task_id, event_type, payload, attempt_count, next_attempt_at, created_at
                FROM task_outbox
                WHERE status = 'PENDING'
                  AND next_attempt_at <= ?
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new OutboxRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getString("event_type"),
                        jsonbMapper.readMap(rs, "payload"),
                        rs.getInt("attempt_count"),
                        rs.getTimestamp("next_attempt_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()),
                Timestamp.from(now),
                limit);
    }

    public void markPublished(UUID outboxId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE task_outbox
                SET status = 'PUBLISHED',
                    published_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                outboxId);
    }

    public void markFailed(UUID outboxId, String error, Instant nextAttemptAt, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE task_outbox
                SET attempt_count = attempt_count + 1,
                    next_attempt_at = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(nextAttemptAt),
                error,
                Timestamp.from(now),
                outboxId);
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM task_outbox WHERE status = 'PENDING'",
                Long.class);
        return count == null ? 0 : count;
    }

    private void insert(
            UUID outboxId,
            UUID taskId,
            String eventType,
            Map<String, Object> payload,
            Instant nextAttemptAt,
            Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO task_outbox
                    (id, task_id, event_type, payload, status, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING', ?, ?, ?)
                """,
                outboxId,
                taskId,
                eventType,
                jsonbMapper.toJson(payload),
                Timestamp.from(nextAttemptAt),
                Timestamp.from(now),
                Timestamp.from(now));
    }
}
