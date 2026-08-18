package com.umer.taskprocessor.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "task-processor.runtime", name = "worker-enabled", havingValue = "true")
public class TaskMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TaskMessageListener.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final TaskWorkerService taskWorkerService;
    private final ObjectMapper objectMapper;

    public TaskMessageListener(TaskWorkerService taskWorkerService, ObjectMapper objectMapper) {
        this.taskWorkerService = taskWorkerService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${task-processor.rabbitmq.queue}", containerFactory = "rabbitListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            UUID taskId = readTaskId(message);
            taskWorkerService.process(taskId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.warn("Task message could not be processed; rejecting without requeue", ex);
            channel.basicReject(deliveryTag, false);
        }
    }

    private UUID readTaskId(Message message) throws IOException {
        Map<String, Object> body = objectMapper.readValue(message.getBody(), MAP_TYPE);
        Object rawTaskId = body.get("taskId");
        if (!(rawTaskId instanceof String taskId)) {
            throw new IllegalArgumentException("RabbitMQ message is missing taskId");
        }
        return UUID.fromString(taskId);
    }
}
