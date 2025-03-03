package net.urosk.taskomir.core.service;

import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.config.TaskExecutorConfig;
import net.urosk.taskomir.core.lib.TaskInfo;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ločen servis za čiščenje (brisanje) starih nalog:
 * - SUCCEEDED -> DELETED po X času
 * - DELETED -> fizično izbris iz baze po Y času
 */
@Service
@Slf4j
public class TaskCleanupService {

    private final TaskInfoRepository repository;
    private final TaskExecutorConfig taskExecutorConfig;

    public TaskCleanupService(TaskInfoRepository repository,
                              TaskExecutorConfig taskExecutorConfig) {
        this.repository = repository;
        this.taskExecutorConfig = taskExecutorConfig;
    }

    /**
     * Metoda se npr. proži vsakih 30 sekund (lahko poljubno),
     * in iz baze odstrani stare, nepotrebne naloge.
     */
    @Scheduled(fixedDelayString = "${taskomir.cleanupIntervalMs:30000}")
    public void cleanupOldTasks() {
        long now = System.currentTimeMillis();

        // 1) Avtomatsko prepis SUCCEEDED => DELETED po X ms
        long dayAgo = now - taskExecutorConfig.succeededRetentionTimeMs;
        List<TaskInfo> succeeded = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.SUCCEEDED);
        for (TaskInfo task : succeeded) {
            if (task.getEndedAt() != null && task.getEndedAt() < dayAgo) {
                task.setStatus(TaskStatus.DELETED);
                task.setDeletedAt(System.currentTimeMillis());
                repository.save(task);
                log.info("Auto-deleted SUCCEEDED task {}", task.getId());
            }
        }

        // 2) Popolni izbris DELETED nalog, starejših od Y ms
        long weekAgo = now - taskExecutorConfig.deletedRetentionTimeMs;
        List<TaskInfo> deleted = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.DELETED);
        for (TaskInfo task : deleted) {
            if (task.getDeletedAt() != null && task.getDeletedAt() < weekAgo) {
                repository.delete(task);
                log.info("Physically removed old DELETED task {}", task.getId());
            }
        }
    }
}