package com.umer.taskprocessor.worker;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        handler -> handler.taskType().toUpperCase(Locale.ROOT),
                        Function.identity()));
    }

    public TaskHandler get(String taskType) {
        TaskHandler handler = handlers.get(taskType.toUpperCase(Locale.ROOT));
        if (handler == null) {
            throw new TaskExecutionException(
                    "UNKNOWN_TASK_TYPE",
                    "No task handler registered for type " + taskType,
                    false);
        }
        return handler;
    }
}
