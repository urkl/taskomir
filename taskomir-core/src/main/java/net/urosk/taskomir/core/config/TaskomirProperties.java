package net.urosk.taskomir.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "taskomir")
@Data
public class TaskomirProperties {

    private Duration cleanupInterval = Duration.ofSeconds(60); // 60 seconds
    private Duration succeededRetentionTime = Duration.ofHours(24); // 24 hours
    private Duration deletedRetentionTime = Duration.ofDays(70); // 70 days
    private Duration scheduledCheckInterval= Duration.ofSeconds(15); // 15 seconds
    private int poolSize = 2; // Number of parallel tasks
    private int queueCapacity = 100_000; //Number of tasks in the queue
    private boolean primary=true;
    private String instanceId;

    public long getCleanupIntervalSeconds() {
        return cleanupInterval.toSeconds();
    }

    public long getSucceededRetentionTimeSeconds() {
        return succeededRetentionTime.toSeconds();
    }

    public long getDeletedRetentionTimeSeconds() {
        return deletedRetentionTime.toSeconds();
    }
}