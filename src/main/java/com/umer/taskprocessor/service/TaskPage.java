package com.umer.taskprocessor.service;

import com.umer.taskprocessor.domain.TaskRecord;
import java.util.List;

public record TaskPage(List<TaskRecord> tasks, int page, int size, long total) {
}
