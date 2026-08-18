package com.umer.taskprocessor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaintenanceLockRepository {

    private final JdbcTemplate jdbcTemplate;

    public MaintenanceLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryTransactionLock(String lockName) {
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext(?))",
                Boolean.class,
                lockName);
        return Boolean.TRUE.equals(locked);
    }
}
