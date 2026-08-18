# Distributed Spring Boot Task Processor

A backend-focused distributed task processing system built with Java 21, Spring Boot,
PostgreSQL, and RabbitMQ.

Current implementation includes:

- Maven/Spring Boot project scaffolding.
- PostgreSQL schema managed by Flyway.
- REST APIs for creating, reading, listing, and cancelling tasks.
- Transactional idempotent task creation.
- Transactional outbox publishing to RabbitMQ.
- RabbitMQ workers that claim and execute tasks safely.
- Retry scheduling, timeout recovery, and duplicate-message tolerance.
- Docker Compose services for API, worker, PostgreSQL, and RabbitMQ.
- Testcontainers integration tests.
- Robot Framework end-to-end API tests.
- GitLab CI/CD pipeline.
- Actuator and Prometheus endpoints.

## Requirements

- Java 21
- Maven
- Docker or Docker Desktop with WSL integration

## Quick Start

For a step-by-step developer walkthrough of how this project is assembled, see
[Developer Build Guide](docs/DEVELOPER_BUILD_GUIDE.md).

Start local infrastructure:

```bash
./scripts/dev-up.sh
```

Run the app:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Or run the composed API/worker stack:

```bash
./scripts/stack-up.sh
```

Create a task:

```bash
curl -i -X POST http://localhost:8080/api/v1/tasks \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-key-1' \
  -d '{"taskType":"CHECKSUM","payload":{"text":"hello"},"priority":0,"maxAttempts":3,"timeoutSeconds":30}'
```

List tasks:

```bash
curl http://localhost:8080/api/v1/tasks
```

OpenAPI UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

Prometheus metrics are available at:

```text
http://localhost:8080/actuator/prometheus
```

Run a small local load sample:

```bash
./scripts/load-generator.py --count 20
```

## Local Services

- PostgreSQL: `localhost:5432`, database `tasks`, user `task_user`, password `task_password`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ management UI: `http://localhost:15672`, user `task_user`, password `task_password`
- API service: `http://localhost:8080`
- Worker service Actuator health: `http://localhost:8081/actuator/health`

## Current Architecture

The system is designed as a small microservice-style architecture in Docker Compose:

- `api` runs the Spring Boot REST API with worker and outbox loops disabled.
- `worker` runs the same application image with the public task API disabled and
  worker/outbox loops enabled.
- `postgres` stores authoritative task lifecycle state.
- `rabbitmq` distributes task messages to worker processes.

The API and worker share one codebase and image, but they run as separate services
with different environment flags. This is a practical microservice deployment
pattern: scale API replicas for request traffic, scale worker replicas for
background throughput, and keep PostgreSQL as the source of truth.
The runtime flags also include `TASK_PROCESSOR_SCHEDULING_ENABLED`, which is
useful for tests and controlled maintenance modes where scheduled loops should
not run automatically.

Creating a task is transactional:

1. Validate the request and `Idempotency-Key`.
2. Hash the normalized request body.
3. Take a PostgreSQL advisory transaction lock for the idempotency key.
4. Return the existing task if the same key and request hash were already used.
5. Reject the request with `409 Conflict` if the same key is reused for different input.
6. Insert the task, idempotency key, and outbox event in one transaction.

Processing a task is also database-led:

1. The outbox publisher locks due `task_outbox` rows with `FOR UPDATE SKIP LOCKED`.
2. It publishes persistent RabbitMQ messages containing the task ID.
3. A worker consumes the message with manual acknowledgement.
4. The worker atomically claims the task in PostgreSQL.
5. Duplicate or stale RabbitMQ messages are acknowledged but ignored if the task
   is no longer claimable.
6. The handler executes and the worker marks the task succeeded, failed, retried,
   timed out, or cancelled.
7. Timeout recovery scans expired running task locks and either schedules retry or
   marks the task `TIMED_OUT`.

Supported demo task types:

- `CHECKSUM`: requires `payload.text` and returns a SHA-256 checksum.
- `DELAY`: accepts `payload.millis` and waits before completing.

## Test Commands

Run fast unit tests:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

Run unit and Testcontainers integration tests:

```bash
./scripts/run-tests.sh
```

Run Robot Framework E2E tests against a local stack:

```bash
./scripts/stack-up.sh
./scripts/run-e2e.sh
```

Or run Robot inside the Compose network:

```bash
docker compose --profile test run --rm robot-tests
```

The suite covers idempotency hashing, retry backoff, task handlers,
PostgreSQL/RabbitMQ integration, full asynchronous task completion, API problem
responses, metrics exposure, and black-box Robot API flows.

## CI/CD

`.gitlab-ci.yml` defines four stages:

- `test`: fast Maven unit tests.
- `integration`: `mvn verify` with Testcontainers and Docker-in-Docker.
- `e2e`: starts the Compose API/worker stack and runs Robot Framework tests.
- `package`: builds the Spring Boot jar and Docker image.
