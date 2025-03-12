package net.urosk.taskomir.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "taskomir.primary", havingValue = "true")
public class TaskomirSchedulingConfig {
}
