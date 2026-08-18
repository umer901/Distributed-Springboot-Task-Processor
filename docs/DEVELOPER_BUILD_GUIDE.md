# Developer Build Guide

This guide explains how to build the project step by step as a developer. It is
written to show the purpose of each command and file, not just the final result.

## 1. Create The Spring Boot Project

Start from the repository root:

```bash
cd /home/umer/Distributed-Springboot-Task-Processor
```

Create `pom.xml`.

Purpose:

- Defines the project as a Maven Java 21 application.
- Uses Spring Boot as the parent build.
- Adds backend dependencies: Web, JDBC, Validation, AMQP, Actuator, Flyway,
  PostgreSQL, Prometheus metrics, OpenAPI, JUnit, Mockito, and Testcontainers.
- Configures the Spring Boot Maven plugin for running and packaging the app.
- Configures Failsafe so future integration tests named `*IT.java` run during
  `mvn verify`.

Validate Maven can read the project:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

The `-Dmaven.repo.local=.m2/repository` option stores downloaded dependencies in
the project workspace. This is useful in restricted environments where Maven
cannot write to `~/.m2`.

## 2. Add Local Infrastructure

Create `docker-compose.yml`.

Purpose:

- Starts PostgreSQL for durable task state.
- Starts RabbitMQ for distributed task delivery.
- Exposes RabbitMQ management UI for local debugging.
- Adds health checks so scripts and CI can tell when services are ready.

Create `.env.example`.

Purpose:

- Documents local infrastructure credentials.
- Gives developers a template if they want to create their own `.env`.

Validate the Compose file:

```bash
docker compose config
```

Start local infrastructure:

```bash
./scripts/dev-up.sh
```

Stop local infrastructure:

```bash
./scripts/dev-down.sh
```

## 3. Add Application Configuration

Create `src/main/resources/application.yml`.

Purpose:

- Defines the Spring application name.
- Configures PostgreSQL connection properties.
- Enables Flyway database migrations.
- Configures RabbitMQ connection properties.
- Enables graceful shutdown.
- Exposes Actuator endpoints: health, info, metrics, and Prometheus.
- Defines project-specific settings under `task-processor`.

Create `src/main/resources/logback-spring.xml`.

Purpose:

- Switches logs to structured JSON.
- Adds application metadata to every log entry.
- Makes logs easier to search in production tools later.

## 4. Add The Database Schema

Create `src/main/resources/db/migration/V1__create_task_processing_schema.sql`.

Purpose:

- `tasks` stores the authoritative lifecycle state for every task.
- `task_attempts` records worker attempts and future retry/failure history.
- `idempotency_keys` maps an `Idempotency-Key` to the task created by that key.
- `task_outbox` stores messages that must later be published to RabbitMQ.

Important design choice:

- Task creation writes to PostgreSQL first.
- RabbitMQ messages are derived from the outbox later.
- This prevents losing a task if the API creates a row but RabbitMQ is
  temporarily unavailable.

## 5. Add The Spring Boot Entry Point

Create `src/main/java/com/umer/taskprocessor/TaskProcessorApplication.java`.

Purpose:

- Starts the Spring Boot application.
- Enables component scanning for controllers, services, repositories, and config.

Create `src/main/java/com/umer/taskprocessor/config/AppConfig.java`.

Purpose:

- Provides a `Clock` bean.
- Makes time easier to control in tests.

Create `src/main/java/com/umer/taskprocessor/config/TaskProcessorProperties.java`.

Purpose:

- Binds `task-processor.*` settings from `application.yml` into typed Java
  configuration.

## 6. Add Domain Types

Create `src/main/java/com/umer/taskprocessor/domain/TaskStatus.java`.

Purpose:

- Defines task lifecycle states such as `QUEUED`, `RUNNING`, `SUCCEEDED`,
  `FAILED`, `CANCELLED`, and `TIMED_OUT`.
- Provides helper behavior such as `isTerminal()`.

Create `src/main/java/com/umer/taskprocessor/domain/TaskRecord.java`.

Purpose:

- Represents a row from the `tasks` table.
- Keeps database-backed task state explicit and type-safe.

## 7. Add API DTOs

Create files under `src/main/java/com/umer/taskprocessor/api/`.

Purpose:

- `CreateTaskRequest` validates incoming `POST /tasks` requests.
- `TaskResponse` shapes one task response returned to API clients.
- `TaskListResponse` shapes paginated list responses.

Design note:

- DTOs are separate from database records so API shape can evolve without forcing
  database structure to leak everywhere.

## 8. Add Idempotency Helpers

Create files under `src/main/java/com/umer/taskprocessor/idempotency/`.

Purpose:

- `NormalizedTaskRequest` stores the canonical form of a create request.
- `IdempotencyRecord` represents one row in `idempotency_keys`.
- `RequestHasher` normalizes requests and creates a stable SHA-256 hash.

Why hashing matters:

- If a client repeats the same request with the same `Idempotency-Key`, return
  the original task.
- If a client reuses the same key for different input, return `409 Conflict`.

## 9. Add Repository Classes

Create files under `src/main/java/com/umer/taskprocessor/repository/`.

Purpose:

- `JsonbMapper` converts Java maps to/from PostgreSQL JSONB columns.
- `TaskRepository` owns SQL for inserting, reading, listing, and cancelling tasks.
- `IdempotencyRepository` owns SQL for idempotency lookup and advisory locks.
- `OutboxRepository` owns SQL for inserting task outbox events.

Important concurrency detail:

- `IdempotencyRepository.lockKeyForTransaction()` uses
  `pg_advisory_xact_lock(hashtext(?))`.
- That means two concurrent requests with the same idempotency key cannot create
  duplicate tasks.

## 10. Add The Service Layer

Create files under `src/main/java/com/umer/taskprocessor/service/`.

Purpose:

- `TaskService` owns transaction boundaries and lifecycle decisions.
- `TaskCreationResult` tells the controller whether a create request was new or
  an idempotent replay.
- `TaskPage` carries paginated list results.

Create task flow:

1. Normalize the request.
2. Hash the normalized request.
3. Lock the idempotency key for the current transaction.
4. Check if the key already exists.
5. Return the existing task if the hash matches.
6. Reject with conflict if the hash differs.
7. Insert the task.
8. Insert the idempotency record.
9. Insert the outbox event.
10. Commit all three inserts together.

## 11. Add The REST Layer

Create files under `src/main/java/com/umer/taskprocessor/web/`.

Purpose:

- `TaskController` exposes REST endpoints.
- `GlobalExceptionHandler` converts exceptions into `ProblemDetail` responses.
- `CorrelationIdFilter` adds `X-Correlation-ID` to logs and responses.
- `TaskNotFoundException` and `IdempotencyConflictException` describe expected
  API failures.

Current endpoints:

```text
POST /api/v1/tasks
GET  /api/v1/tasks/{id}
GET  /api/v1/tasks
POST /api/v1/tasks/{id}/cancel
```

## 12. Add Developer Scripts

Create files under `scripts/`.

Purpose:

- `dev-up.sh` starts PostgreSQL and RabbitMQ.
- `dev-down.sh` stops local infrastructure.
- `run-tests.sh` runs the Maven verification suite.
- `submit-task.sh` sends a sample task creation request with `curl`.

Make scripts executable:

```bash
chmod +x scripts/dev-up.sh scripts/dev-down.sh scripts/run-tests.sh scripts/submit-task.sh
```

## 13. Add Tests

Create `src/test/java/com/umer/taskprocessor/idempotency/RequestHasherTest.java`.

Purpose:

- Proves equivalent JSON payload ordering creates the same idempotency hash.
- Proves different task input creates a different hash.
- Proves server defaults are included during normalization.

Create support classes under `src/test/java/com/umer/taskprocessor/support/`.

Purpose:

- `PostgresTestContainerSupport` provides a reusable PostgreSQL container.
- `RabbitMqTestContainerSupport` provides a reusable RabbitMQ container.
- Future integration tests can share these instead of duplicating setup.

Run tests:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

Expected result for the current slice:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 14. Run The Current Application

Start infrastructure:

```bash
./scripts/dev-up.sh
```

Start the Spring Boot app:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Create a task:

```bash
./scripts/submit-task.sh
```

List tasks:

```bash
curl http://localhost:8080/api/v1/tasks
```

Check health:

```bash
curl http://localhost:8080/actuator/health
```

Check Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Open API documentation:

```text
http://localhost:8080/swagger-ui.html
```

## 15. Add Runtime Roles

Update `src/main/resources/application.yml`.

Purpose:

- Add `task-processor.runtime.api-enabled`.
- Add `task-processor.runtime.worker-enabled`.
- Add `task-processor.runtime.outbox-enabled`.
- Let the same Spring Boot image run as API-only, worker-only, or all-in-one.

Update `src/main/java/com/umer/taskprocessor/config/TaskProcessorProperties.java`.

Purpose:

- Bind runtime, RabbitMQ, outbox, worker, and retry settings into typed Java
  records.
- Keep configuration discoverable and compile-time checked.

Update `TaskController`.

Purpose:

- Add `@ConditionalOnProperty` so the public task API only loads when
  `api-enabled=true`.

## 16. Add RabbitMQ Topology

Create `src/main/java/com/umer/taskprocessor/config/RabbitMqConfig.java`.

Purpose:

- Declares the durable direct exchange.
- Declares the durable task queue.
- Declares a dead-letter queue for rejected malformed messages.
- Binds the task queue to the exchange with the configured routing key.
- Configures JSON message conversion.
- Configures manual acknowledgement and worker prefetch.

Why manual acknowledgement matters:

- The worker only acknowledges after it has safely handled the message.
- If the message is malformed, it is rejected without requeue.
- If the message is duplicate or stale, the worker acknowledges it because
  PostgreSQL already proves there is no work to do.

## 17. Add Outbox Publishing

Create `src/main/java/com/umer/taskprocessor/outbox/OutboxRecord.java`.

Purpose:

- Represents a pending row from `task_outbox`.

Update `src/main/java/com/umer/taskprocessor/repository/OutboxRepository.java`.

Purpose:

- Find due pending rows with `FOR UPDATE SKIP LOCKED`.
- Mark rows as published after RabbitMQ accepts the message.
- Reschedule publish attempts after transient failures.
- Insert retry dispatch events when a task needs another attempt.

Create `src/main/java/com/umer/taskprocessor/outbox/OutboxPublisher.java`.

Purpose:

- Runs on a schedule.
- Reads due outbox rows.
- Publishes persistent RabbitMQ messages.
- Marks outbox rows as published in PostgreSQL.

Design note:

- RabbitMQ messages contain the task ID, not the full task payload.
- PostgreSQL remains the source of truth.

## 18. Add Worker Task Execution

Create files under `src/main/java/com/umer/taskprocessor/worker/`.

Purpose:

- `TaskHandler` defines the contract for executable task types.
- `TaskExecutionResult` wraps handler output.
- `TaskExecutionException` carries failure code and retryability.
- `TaskHandlerRegistry` resolves a task type to a handler.
- `ChecksumTaskHandler` implements the `CHECKSUM` demo task.
- `DelayTaskHandler` implements the `DELAY` demo task.
- `RetryPolicy` calculates exponential backoff.
- `TaskWorkerService` owns worker execution flow.
- `TaskMessageListener` consumes RabbitMQ messages with manual ack.
- `TaskRecoveryScheduler` recovers expired running tasks.

Worker execution flow:

1. RabbitMQ delivers a message containing `taskId`.
2. The worker tries to claim the task in PostgreSQL.
3. If the task is not claimable, the message is acknowledged and ignored.
4. If claimed, the worker inserts a running attempt row.
5. The matching handler executes.
6. Success stores `result` and marks the task `SUCCEEDED`.
7. Retryable failure schedules `RETRY_SCHEDULED` and inserts a future outbox row.
8. Non-retryable or exhausted failure marks the task `FAILED`.
9. Expired locks are recovered by the scheduler and either retried or timed out.

Important SQL idea:

- Claiming uses a conditional `UPDATE ... RETURNING`.
- This makes only one worker able to move a task from `QUEUED` or
  `RETRY_SCHEDULED` to `RUNNING`.

## 19. Add Metrics

Create `src/main/java/com/umer/taskprocessor/metrics/TaskProcessingMetrics.java`.

Purpose:

- Counts published outbox events.
- Counts publish failures.
- Counts succeeded, failed, retried, and timed-out tasks.
- Exposes a gauge for pending outbox rows.

Check metrics locally:

```bash
curl http://localhost:8080/actuator/prometheus
```

## 20. Add Dockerized Service Roles

Create `Dockerfile`.

Purpose:

- Builds the Spring Boot jar with Maven.
- Runs it on a Java 21 runtime image.

Create `.dockerignore`.

Purpose:

- Keeps build output, local Maven cache, Git metadata, and IDE files out of the
  Docker build context.

Update `docker-compose.yml`.

Purpose:

- Add `api`, `worker`, `postgres`, and `rabbitmq` services.
- Run API and worker as separate services using the same image.
- Set environment variables so API and worker have different runtime roles.

Build and run the full stack:

```bash
./scripts/stack-up.sh
```

The Compose architecture is:

```text
client -> api -> postgres -> task_outbox -> worker publisher -> rabbitmq -> worker consumer -> postgres
```

## 21. Add Worker Tests

Create:

- `src/test/java/com/umer/taskprocessor/worker/RetryPolicyTest.java`
- `src/test/java/com/umer/taskprocessor/worker/ChecksumTaskHandlerTest.java`

Purpose:

- Verify exponential backoff behavior.
- Verify retry limits.
- Verify `CHECKSUM` produces the expected SHA-256 output.
- Verify invalid handler input fails as non-retryable.

Run tests:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

Expected result after this slice:

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 22. What To Build Next

The current project now accepts, publishes, and executes tasks. The next
development slice should add:

- Testcontainers integration tests for PostgreSQL and RabbitMQ behavior.
- Robot Framework end-to-end API tests.
- GitLab CI pipeline.
- More operational documentation and troubleshooting examples.
