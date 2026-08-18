package com.umer.taskprocessor.outbox;

import com.umer.taskprocessor.config.TaskProcessorProperties;
import com.umer.taskprocessor.metrics.TaskProcessingMetrics;
import com.umer.taskprocessor.repository.OutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "task-processor.runtime", name = "outbox-enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TaskProcessorProperties properties;
    private final TaskProcessingMetrics metrics;
    private final Clock clock;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            TaskProcessorProperties properties,
            TaskProcessingMetrics metrics,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${task-processor.outbox.publish-delay-millis}")
    @Transactional
    public void publishDueEvents() {
        Instant now = clock.instant();
        for (OutboxRecord event : outboxRepository.findDuePending(properties.outbox().batchSize(), now)) {
            publishOne(event, now);
        }
    }

    private void publishOne(OutboxRecord event, Instant now) {
        MDC.put("taskId", event.taskId().toString());
        MDC.put("outboxId", event.id().toString());
        try {
            Map<String, Object> message = Map.of(
                    "taskId", event.taskId().toString(),
                    "eventType", event.eventType(),
                    "outboxId", event.id().toString());
            rabbitTemplate.convertAndSend(
                    properties.rabbitmq().exchange(),
                    properties.rabbitmq().routingKey(),
                    message,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties().setMessageId(event.id().toString());
                        rabbitMessage.getMessageProperties().setCorrelationId(event.taskId().toString());
                        rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        rabbitMessage.getMessageProperties().setHeader("eventType", event.eventType());
                        return rabbitMessage;
                    });
            outboxRepository.markPublished(event.id(), now);
            metrics.outboxPublished();
            log.info("Published outbox event");
        } catch (RuntimeException ex) {
            Instant nextAttempt = now.plusMillis(retryDelayMillis(event.attemptCount()));
            outboxRepository.markFailed(event.id(), ex.getMessage(), nextAttempt, now);
            metrics.outboxPublishFailed();
            log.warn("Outbox publish failed; event will be retried", ex);
        } finally {
            MDC.remove("taskId");
            MDC.remove("outboxId");
        }
    }

    private long retryDelayMillis(int attemptCount) {
        long delay = 1000L * (attemptCount + 1L);
        return Math.min(delay, 30000L);
    }
}
