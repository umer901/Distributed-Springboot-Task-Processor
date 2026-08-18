package com.umer.taskprocessor.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
        jdbcTemplate.update(
                """
                INSERT INTO task_outbox
                    (id, task_id, event_type, payload, status, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, 'TASK_CREATED', ?::jsonb, 'PENDING', ?, ?, ?)
                """,
                outboxId,
                taskId,
                jsonbMapper.toJson(payload),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }
}
