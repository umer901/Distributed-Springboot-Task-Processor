# Distributed Spring Boot Task Processor

A backend-focused distributed task processing system built with Java 21, Spring Boot,
PostgreSQL, and RabbitMQ.

This first implementation slice includes:

- Maven/Spring Boot project scaffolding.
- PostgreSQL schema managed by Flyway.
- REST APIs for creating, reading, listing, and cancelling tasks.
- Transactional idempotent task creation.
- Transactional outbox rows ready for RabbitMQ publishing in the worker slice.
- Docker Compose infrastructure for local PostgreSQL and RabbitMQ.
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
mvn spring-boot:run
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

## Local Services

- PostgreSQL: `localhost:5432`, database `tasks`, user `task_user`, password `task_password`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ management UI: `http://localhost:15672`, user `task_user`, password `task_password`

## Current Architecture

The REST API stores task state in PostgreSQL as the source of truth. Creating a task is
transactional:

1. Validate the request and `Idempotency-Key`.
2. Hash the normalized request body.
3. Take a PostgreSQL advisory transaction lock for the idempotency key.
4. Return the existing task if the same key and request hash were already used.
5. Reject the request with `409 Conflict` if the same key is reused for different input.
6. Insert the task, idempotency key, and outbox event in one transaction.

The next implementation slice will publish pending outbox events to RabbitMQ and add
workers that claim and execute tasks safely.

## Test Commands

Run the automated suite:

```bash
./scripts/run-tests.sh
```

At this stage, the suite covers idempotency hashing and the Spring context. The next
slices will add Testcontainers-backed PostgreSQL/RabbitMQ integration tests and Robot
Framework API tests.
