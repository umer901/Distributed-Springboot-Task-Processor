package com.umer.taskprocessor.worker;

import java.util.Map;

public record TaskExecutionResult(Map<String, Object> result) {
}
