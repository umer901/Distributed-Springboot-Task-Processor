package com.umer.taskprocessor.repository;

import com.umer.taskprocessor.idempotency.IdempotencyRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockKeyForTransaction(String idempotencyKey) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                statement.setString(1, idempotencyKey);
                statement.execute();
            }
            return null;
        });
    }

    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        List<IdempotencyRecord> rows = jdbcTemplate.query(
                """
                SELECT idempotency_key, request_hash, task_id, created_at, expires_at
                FROM idempotency_keys
                WHERE idempotency_key = ?
                """,
                (rs, rowNum) -> new IdempotencyRecord(
                        rs.getString("idempotency_key"),
                        rs.getString("request_hash"),
                        rs.getObject("task_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant()),
                idempotencyKey);
        return rows.stream().findFirst();
    }

    public void insert(String idempotencyKey, String requestHash, UUID taskId, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO idempotency_keys (idempotency_key, request_hash, task_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                idempotencyKey,
                requestHash,
                taskId,
                Timestamp.from(createdAt),
                Timestamp.from(expiresAt));
    }

    public void deleteExpiredKey(String idempotencyKey, Instant now) {
        jdbcTemplate.update(
                "DELETE FROM idempotency_keys WHERE idempotency_key = ? AND expires_at <= ?",
                idempotencyKey,
                Timestamp.from(now));
    }
}
