package com.umer.taskprocessor.worker;

public class TaskExecutionException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public TaskExecutionException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
