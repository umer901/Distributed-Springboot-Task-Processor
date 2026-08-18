package com.umer.taskprocessor.worker;

import com.umer.taskprocessor.domain.TaskRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ChecksumTaskHandler implements TaskHandler {

    @Override
    public String taskType() {
        return "CHECKSUM";
    }

    @Override
    public TaskExecutionResult execute(TaskRecord task) {
        Object text = task.payload().get("text");
        if (!(text instanceof String value) || value.isBlank()) {
            throw new TaskExecutionException("INVALID_PAYLOAD", "CHECKSUM requires non-empty payload.text", false);
        }
        return new TaskExecutionResult(Map.of(
                "algorithm", "SHA-256",
                "checksum", sha256(value)));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
