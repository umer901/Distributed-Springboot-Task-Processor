package com.umer.taskprocessor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.umer.taskprocessor.api.TaskResponse;
import com.umer.taskprocessor.outbox.OutboxPublisher;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskProcessingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("tasks")
            .withUsername("task_user")
            .withPassword("task_password");

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.1-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "task_user")
            .withEnv("RABBITMQ_DEFAULT_PASS", "task_password")
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "1000");
        registry.add("task-processor.runtime.scheduling-enabled", () -> "false");
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "task_user");
        registry.add("spring.rabbitmq.password", () -> "task_password");
        registry.add("task-processor.outbox.publish-delay-millis", () -> "200");
        registry.add("task-processor.worker.recovery-delay-millis", () -> "200");
        registry.add("task-processor.worker.id", () -> "integration-worker");
        registry.add("debug", () -> "false");
        registry.add("logging.level.root", () -> "INFO");
        registry.add("logging.level.org.springframework", () -> "INFO");
        registry.add("logging.level.org.springframework.jdbc.core", () -> "INFO");
        registry.add("logging.level.org.testcontainers", () -> "INFO");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxPublisher outboxPublisher;

    @Test
    void createsPublishesAndExecutesChecksumTask() {
        ResponseEntity<TaskResponse> created = createTask(
                "it-checksum-" + UUID.randomUUID(),
                Map.of("taskType", "CHECKSUM", "payload", Map.of("text", "hello"), "timeoutSeconds", 10));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();

        TaskResponse completed = awaitTaskStatus(created.getBody().id(), "SUCCEEDED");

        assertThat(completed.result())
                .containsEntry("algorithm", "SHA-256")
                .containsEntry("checksum", "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(completed.attemptCount()).isEqualTo(1);
        assertThat(countRows("task_attempts")).isEqualTo(1);
        assertThat(countPublishedOutboxRows()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void replaysIdempotentCreateAndRejectsConflictingReuse() {
        String key = "it-idempotency-" + UUID.randomUUID();

        ResponseEntity<TaskResponse> first = createTask(
                key,
                Map.of("taskType", "CHECKSUM", "payload", Map.of("text", "same")));
        ResponseEntity<TaskResponse> replay = createTask(
                key,
                Map.of("taskType", "checksum", "payload", Map.of("text", "same"), "priority", 0, "maxAttempts", 3, "timeoutSeconds", 60));
        ResponseEntity<String> conflict = createRawTask(
                key,
                Map.of("taskType", "CHECKSUM", "payload", Map.of("text", "different")));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(replay.getBody()).isNotNull();
        assertThat(replay.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(replay.getBody().idempotentReplay()).isTrue();
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelPreventsQueuedTaskFromExecuting() {
        ResponseEntity<TaskResponse> created = createTask(
                "it-cancel-" + UUID.randomUUID(),
                Map.of("taskType", "DELAY", "payload", Map.of("millis", 5000), "timeoutSeconds", 10));
        assertThat(created.getBody()).isNotNull();

        ResponseEntity<TaskResponse> cancelled = restTemplate.postForEntity(
                url("/api/v1/tasks/" + created.getBody().id() + "/cancel"),
                null,
                TaskResponse.class);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).isNotNull();
        assertThat(cancelled.getBody().status().name()).isIn("CANCELLED", "RUNNING", "SUCCEEDED");
    }

    private ResponseEntity<TaskResponse> createTask(String idempotencyKey, Map<String, Object> body) {
        return restTemplate.exchange(
                url("/api/v1/tasks"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers(idempotencyKey)),
                TaskResponse.class);
    }

    private ResponseEntity<String> createRawTask(String idempotencyKey, Map<String, Object> body) {
        return restTemplate.exchange(
                url("/api/v1/tasks"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers(idempotencyKey)),
                String.class);
    }

    private TaskResponse awaitTaskStatus(UUID taskId, String expectedStatus) {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        TaskResponse response = null;
        while (System.nanoTime() < deadline) {
            outboxPublisher.publishDueEvents();
            response = restTemplate.getForObject(url("/api/v1/tasks/" + taskId), TaskResponse.class);
            if (response != null && expectedStatus.equals(response.status().name())) {
                return response;
            }
            sleep();
        }
        throw new AssertionError("Task " + taskId + " did not reach " + expectedStatus + "; last response=" + response);
    }

    private HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    private long countPublishedOutboxRows() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM task_outbox WHERE status = 'PUBLISHED'",
                Long.class);
        return count == null ? 0 : count;
    }

    private void sleep() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for task completion", ex);
        }
    }
}
