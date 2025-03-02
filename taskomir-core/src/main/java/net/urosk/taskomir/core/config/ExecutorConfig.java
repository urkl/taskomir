package net.urosk.taskomir.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


@Configuration
public class ExecutorConfig {

    @Bean
    public ThreadPoolExecutor executorService(TaskExecutorConfig taskExecutorConfig) {
        int poolSize = taskExecutorConfig.poolSize;       // Nastavljivo število background taskov
        int queueCapacity = taskExecutorConfig.queueCapacity; // Kapaciteta čakalne vrste
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity)
        );
    }
}