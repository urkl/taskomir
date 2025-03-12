package net.urosk.taskomir.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * We need to enable scheduling only on the primary instance.

 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "taskomir.primary", havingValue = "true")
public class TaskomirSchedulingConfig {
}
