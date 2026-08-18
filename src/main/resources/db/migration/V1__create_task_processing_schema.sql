CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    task_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    result JSONB,
    failure_code VARCHAR(120),
    failure_message TEXT,
    priority INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    timeout_seconds INTEGER NOT NULL,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    lock_owner VARCHAR(120),
    lock_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tasks_status_check CHECK (
        status IN ('QUEUED', 'RUNNING', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT')
    ),
    CONSTRAINT tasks_priority_check CHECK (priority BETWEEN -100 AND 100),
    CONSTRAINT tasks_attempts_check CHECK (max_attempts BETWEEN 1 AND 10),
    CONSTRAINT tasks_timeout_check CHECK (timeout_seconds BETWEEN 1 AND 86400),
    CONSTRAINT tasks_attempt_count_check CHECK (attempt_count >= 0)
);

CREATE INDEX idx_tasks_status_available_priority
    ON tasks (status, available_at, priority DESC, created_at);

CREATE INDEX idx_tasks_type_created
    ON tasks (task_type, created_at DESC);

CREATE TABLE task_attempts (
    id BIGSERIAL PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    worker_id VARCHAR(120),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    error_code VARCHAR(120),
    error_message TEXT,
    CONSTRAINT task_attempts_status_check CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED')
    ),
    CONSTRAINT task_attempts_unique_attempt UNIQUE (task_id, attempt_number)
);

CREATE INDEX idx_task_attempts_task_id
    ON task_attempts (task_id, attempt_number DESC);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    task_id UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_keys_expires_at
    ON idempotency_keys (expires_at);

CREATE TABLE task_outbox (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT task_outbox_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_task_outbox_pending
    ON task_outbox (status, next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_task_outbox_task_id
    ON task_outbox (task_id);
