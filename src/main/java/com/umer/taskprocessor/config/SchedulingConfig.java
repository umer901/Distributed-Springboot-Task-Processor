package com.umer.taskprocessor.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "task-processor.runtime",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {
}
