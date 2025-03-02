package net.urosk.taskomir.core.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskExecutorConfig {

    @Value("${taskomir.cleanupIntervalMs:60000}")
    public long cleanupIntervalMs; // Interval, v katerem kličemo checkScheduledTasks()

    @Value("${taskomir.succeededRetentionTimeMs:86400000}")
    public long succeededRetentionTimeMs; // Po kolikšnem času SUCCEEDED postane DELETED

    @Value("${taskomir.deletedRetentionTimeMs:604800000}")
    public long deletedRetentionTimeMs; // Po kolikšnem času od DELETED se zadeva izbriše iz DB

    @Value("${taskomir.poolSize:2}")
    public int poolSize; // Nastavljivo število vzporednih taskov

    @Value("${taskomir.queueCapacity:100}")
    public int queueCapacity; // Kapaciteta čakalne vrste
}
