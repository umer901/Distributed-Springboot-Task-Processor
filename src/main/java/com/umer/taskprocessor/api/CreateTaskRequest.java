package com.umer.taskprocessor.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateTaskRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.:-]*$")
        String taskType,

        @NotNull
        Map<String, Object> payload,

        @Min(-100)
        @Max(100)
        Integer priority,

        @Min(1)
        @Max(10)
        Integer maxAttempts,

        @Min(1)
        @Max(86400)
        Integer timeoutSeconds) {
}
