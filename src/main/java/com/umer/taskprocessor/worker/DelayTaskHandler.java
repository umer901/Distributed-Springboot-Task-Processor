package com.umer.taskprocessor.worker;

import com.umer.taskprocessor.domain.TaskRecord;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DelayTaskHandler implements TaskHandler {

    @Override
    public String taskType() {
        return "DELAY";
    }

    @Override
    public TaskExecutionResult execute(TaskRecord task) {
        long millis = readMillis(task);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TaskExecutionException("WORKER_INTERRUPTED", "Worker interrupted while delaying", true);
        }
        return new TaskExecutionResult(Map.of("delayedMillis", millis));
    }

    private long readMillis(TaskRecord task) {
        Object raw = task.payload().getOrDefault("millis", 1000);
        if (raw instanceof Number number) {
            long millis = number.longValue();
            if (millis >= 0 && millis <= 300000) {
                return millis;
            }
        }
        throw new TaskExecutionException("INVALID_PAYLOAD", "DELAY requires payload.millis between 0 and 300000", false);
    }
}
