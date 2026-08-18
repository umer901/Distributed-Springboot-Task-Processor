package com.umer.taskprocessor.web;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID taskId) {
        super("Task '" + taskId + "' was not found");
    }
}
