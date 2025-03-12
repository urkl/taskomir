package net.urosk.taskomir.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


@Configuration
@ConditionalOnProperty(name = "taskomir.primary", havingValue = "true")
public class ExecutorConfig {

    @Bean
    public ThreadPoolExecutor executorService(TaskomirProperties taskomirProperties) {
        int poolSize = taskomirProperties.getPoolSize();
        int queueCapacity = taskomirProperties.getQueueCapacity();
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity)
        );
    }
}