package com.umer.taskprocessor.service;

import com.umer.taskprocessor.domain.TaskRecord;

public record TaskCreationResult(TaskRecord task, boolean idempotentReplay) {
}
