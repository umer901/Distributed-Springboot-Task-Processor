package com.umer.taskprocessor.web;

import com.umer.taskprocessor.api.CreateTaskRequest;
import com.umer.taskprocessor.api.TaskListResponse;
import com.umer.taskprocessor.api.TaskResponse;
import com.umer.taskprocessor.domain.TaskStatus;
import com.umer.taskprocessor.service.TaskCreationResult;
import com.umer.taskprocessor.service.TaskPage;
import com.umer.taskprocessor.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "Create an asynchronous task")
    @PostMapping
    ResponseEntity<TaskResponse> createTask(
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
            String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskCreationResult result = taskService.createTask(idempotencyKey, request);
        TaskResponse response = TaskResponse.from(result.task(), result.idempotentReplay());
        if (result.idempotentReplay()) {
            return ResponseEntity.ok(response);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.task().id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get task state")
    @GetMapping("/{id}")
    TaskResponse getTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.getTask(id), false);
    }

    @Operation(summary = "List tasks")
    @GetMapping
    TaskListResponse listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        TaskPage result = taskService.listTasks(status, taskType, page, size);
        return new TaskListResponse(
                result.tasks().stream().map(task -> TaskResponse.from(task, false)).toList(),
                result.page(),
                result.size(),
                result.total());
    }

    @Operation(summary = "Cancel a task if it has not reached a terminal state")
    @PostMapping("/{id}/cancel")
    TaskResponse cancelTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.cancelTask(id), false);
    }
}
