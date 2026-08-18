package com.umer.taskprocessor.worker;

import com.umer.taskprocessor.domain.TaskRecord;

public interface TaskHandler {

    String taskType();

    TaskExecutionResult execute(TaskRecord task);
}
