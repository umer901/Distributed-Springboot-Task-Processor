package com.umer.taskprocessor.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.umer.taskprocessor.api.CreateTaskRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RequestHasher {

    public static final int DEFAULT_PRIORITY = 0;
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final ObjectMapper canonicalMapper;

    public RequestHasher(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public NormalizedTaskRequest normalize(CreateTaskRequest request) {
        return new NormalizedTaskRequest(
                request.taskType().trim().toUpperCase(Locale.ROOT),
                request.payload(),
                request.priority() == null ? DEFAULT_PRIORITY : request.priority(),
                request.maxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : request.maxAttempts(),
                request.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : request.timeoutSeconds());
    }

    public String hash(NormalizedTaskRequest request) {
        try {
            byte[] canonicalJson = canonicalMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Task request payload cannot be serialized", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String canonicalJson(NormalizedTaskRequest request) {
        try {
            return canonicalMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Task request payload cannot be serialized", ex);
        }
    }
}
