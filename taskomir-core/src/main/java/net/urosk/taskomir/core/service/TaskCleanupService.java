package net.urosk.taskomir.core.service;

import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.config.TaskomirProperties;
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
    private final TaskomirProperties taskomirProperties;

    public TaskCleanupService(TaskInfoRepository repository,
                              TaskomirProperties taskomirProperties) {
        this.repository = repository;
        this.taskomirProperties = taskomirProperties;
    }

    /**
     * Metoda se proži na podlagi konfiguracije (`cleanupInterval`).
     */
    @Scheduled(fixedDelayString = "#{@taskomirProperties.cleanupInterval.toMillis()}")
    public void cleanupOldTasks() {
        long now = System.currentTimeMillis();

        // 1) Avtomatsko prepis SUCCEEDED => DELETED po X sekundah
        long succeededThreshold = now - taskomirProperties.getSucceededRetentionTime().toMillis();
        List<TaskInfo> succeeded = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.SUCCEEDED);
        for (TaskInfo task : succeeded) {
            if (task.getEndedAt() != null && task.getEndedAt() < succeededThreshold) {
                task.setStatus(TaskStatus.DELETED);
                task.setDeletedAt(System.currentTimeMillis());
                repository.save(task);
                log.info("Auto-deleted SUCCEEDED task {}", task.getId());
            }
        }

        // 2) Popolni izbris DELETED nalog, starejših od Y sekund
        long deletedThreshold = now - taskomirProperties.getDeletedRetentionTime().toMillis();
        List<TaskInfo> deleted = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.DELETED);
        for (TaskInfo task : deleted) {
            if (task.getDeletedAt() != null && task.getDeletedAt() < deletedThreshold) {
                repository.delete(task);
                log.info("Physically removed old DELETED task {}", task.getId());
            }
        }
    }
}
