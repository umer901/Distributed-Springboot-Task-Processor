package com.umer.taskprocessor.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umer.taskprocessor.api.CreateTaskRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

    private final RequestHasher requestHasher = new RequestHasher(new ObjectMapper());

    @Test
    void hashIsStableForEquivalentPayloadOrdering() {
        Map<String, Object> firstPayload = new LinkedHashMap<>();
        firstPayload.put("b", 2);
        firstPayload.put("a", 1);

        Map<String, Object> secondPayload = new LinkedHashMap<>();
        secondPayload.put("a", 1);
        secondPayload.put("b", 2);

        CreateTaskRequest first = new CreateTaskRequest("checksum", firstPayload, null, null, null);
        CreateTaskRequest second = new CreateTaskRequest("CHECKSUM", secondPayload, 0, 3, 60);

        String firstHash = requestHasher.hash(requestHasher.normalize(first));
        String secondHash = requestHasher.hash(requestHasher.normalize(second));

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void hashChangesWhenTaskInputChanges() {
        CreateTaskRequest first = new CreateTaskRequest("CHECKSUM", Map.of("text", "hello"), 0, 3, 60);
        CreateTaskRequest second = new CreateTaskRequest("CHECKSUM", Map.of("text", "goodbye"), 0, 3, 60);

        String firstHash = requestHasher.hash(requestHasher.normalize(first));
        String secondHash = requestHasher.hash(requestHasher.normalize(second));

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void normalizeAppliesServerDefaults() {
        CreateTaskRequest request = new CreateTaskRequest("checksum", Map.of("text", "hello"), null, null, null);

        NormalizedTaskRequest normalized = requestHasher.normalize(request);

        assertThat(normalized.taskType()).isEqualTo("CHECKSUM");
        assertThat(normalized.priority()).isZero();
        assertThat(normalized.maxAttempts()).isEqualTo(3);
        assertThat(normalized.timeoutSeconds()).isEqualTo(60);
    }
}
