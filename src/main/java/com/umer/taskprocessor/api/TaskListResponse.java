package com.umer.taskprocessor.api;

import java.util.List;

public record TaskListResponse(
        List<TaskResponse> items,
        int page,
        int size,
        long total) {
}
