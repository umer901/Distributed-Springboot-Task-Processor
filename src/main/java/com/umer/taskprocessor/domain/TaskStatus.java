package com.umer.taskprocessor.domain;

import java.util.Locale;

public enum TaskStatus {
    QUEUED,
    RUNNING,
    RETRY_SCHEDULED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }

    public static TaskStatus parse(String value) {
        return TaskStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
