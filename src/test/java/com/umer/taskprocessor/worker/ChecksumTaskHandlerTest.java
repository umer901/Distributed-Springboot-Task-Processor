package com.umer.taskprocessor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umer.taskprocessor.domain.TaskRecord;
import com.umer.taskprocessor.domain.TaskStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChecksumTaskHandlerTest {

    private final ChecksumTaskHandler handler = new ChecksumTaskHandler();

    @Test
    void returnsSha256ChecksumForTextPayload() {
        TaskRecord task = task(Map.of("text", "hello"));

        TaskExecutionResult result = handler.execute(task);

        assertThat(result.result())
                .containsEntry("algorithm", "SHA-256")
                .containsEntry("checksum", "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void rejectsMissingTextPayloadAsNonRetryable() {
        TaskRecord task = task(Map.of());

        assertThatThrownBy(() -> handler.execute(task))
                .isInstanceOf(TaskExecutionException.class)
                .extracting("retryable")
                .isEqualTo(false);
    }

    private TaskRecord task(Map<String, Object> payload) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new TaskRecord(
                UUID.randomUUID(),
                TaskStatus.RUNNING,
                "CHECKSUM",
                payload,
                null,
                null,
                null,
                0,
                3,
                1,
                60,
                now,
                now,
                null,
                "worker-1",
                now.plusSeconds(60),
                1,
                now,
                now);
    }
}
