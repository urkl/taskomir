package net.urosk.taskomir.core.service;

import com.mongodb.DuplicateKeyException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.config.TaskomirProperties;
import net.urosk.taskomir.core.domain.AppLock;
import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.ProgressTask;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.AppLockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Javna fasada (API), prek katere drugi projekti uporabljajo Taskomir funkcionalnost.
 */
@Service
@Slf4j
public class TaskomirService {

    private final TaskLifecycleService taskLifecycleService;
    private final AppLockRepository appLockRepository;

    @Getter
    private final TaskomirProperties taskomirProperties;

    public TaskomirService(TaskLifecycleService taskLifecycleService, TaskomirProperties taskomirProperties, AppLockRepository appLockRepository) {
        this.taskLifecycleService = taskLifecycleService;
        this.taskomirProperties = taskomirProperties;
        this.appLockRepository = appLockRepository;
    }

    /**
     * Ustvari "master" SCHEDULED task, ki se bo prožil glede na podan cronExpression.
     * `skipIfAlreadyRunning` pomeni, da bo skipnil child nalogo, če ta isti "master" že teče.
     */
    public TaskInfo createScheduledTask(String taskName,
                                        ProgressTask progressTask,
                                        String cronExpression,
                                        boolean skipIfAlreadyRunning) {
        // Tu lahko dodaš morebitno lastno validacijo ali logging
        log.info("Creating scheduled task: name={}, cron={}, skipIfRunning={}",
                taskName, cronExpression, skipIfAlreadyRunning);

        // Dejansko delo prepustimo TaskLifecycleService
        return taskLifecycleService.createScheduledTask(taskName, progressTask, cronExpression, skipIfAlreadyRunning);
    }

    /**
     * Enkratno pognati ProgressTask (brez crona).
     */
    public CompletableFuture<TaskInfo> enqueue(String taskName, ProgressTask task) {
        log.info("Enqueuing immediate task: name={}", taskName);
        return taskLifecycleService.enqueue(taskName, task);
    }

    /**
     * Cancel/prekliči nalogo (če je v teku).
     */
    public boolean cancelTask(String taskId) {
        log.info("Cancelling task with id={}", taskId);
        return taskLifecycleService.cancelTask(taskId);
    }

    /**
     * Vrne TaskInfo iz baze (ali null, če ne obstaja).
     */
    public TaskInfo getTaskInfo(String taskId) {
        return taskLifecycleService.getTask(taskId);
    }

    public void deleteTasksByStatus(TaskStatus taskStatus) {
        taskLifecycleService.deleteTasksByStatus(taskStatus);
    }

    public void enqueueNewChildOf(TaskInfo task) {
        taskLifecycleService.enqueueNewChildOf(task);
    }

    public Page<TaskInfo> getTasksByStatus(TaskStatus status, Pageable pageable) {
        return taskLifecycleService.getTasksByStatus(status, pageable);
    }

    public TaskInfo createScheduledTaskIfNotExists(
            String taskName,
            ProgressTask progressTask,
            String cronExpression,
            boolean skipIfAlreadyRunning
    ) {
        // Najprej preverimo, ali obstaja takšna SCHEDULED naloga:
        Optional<TaskInfo> existing = taskLifecycleService.findByNameAndStatus(taskName, TaskStatus.SCHEDULED);

        if (existing.isPresent()) {
            //Preverim še cron ali className, če hočeš popolno enakost
            TaskInfo existingMaster = existing.get();
            if (cronExpression.equals(existingMaster.getCronExpression())
                    && progressTask.getClass().getName().equals(existingMaster.getClassName())) {
                // Naloga je že registrirana, vrnemo kar to
                return existingMaster;
            }
        }

        // Če ni obstajala, jo ustvarimo
        return createScheduledTask(taskName, progressTask, cronExpression, skipIfAlreadyRunning);
    }


    /**
     * After construction, check if we're primary. If yes, attempt to insert the "PRIMARY" lock in Mongo.
     * If a lock already exists, we throw an exception (or shut down).
     */
    @PostConstruct
    public void initInstanceRole() {
        String instanceId =taskomirProperties.getInstanceId();
        if (taskomirProperties.isPrimary()) {
            log.info("Trying to become PRIMARY, instanceId={}", instanceId);

            Optional<AppLock> existing = appLockRepository.findById("PRIMARY");
            if (existing.isPresent()) {
                AppLock lock = existing.get();
                if (lock.getInstanceId().equals(instanceId)) {
                    // To je ista instanca, ki se je ponovno zagnala.
                    // Lahko le posodobimo lockedAt (opcijsko):
                    lock.setLockedAt(System.currentTimeMillis());
                    appLockRepository.save(lock);
                    log.info("This instance was already PRIMARY (same ownerId). Updated the timestamp.");
                } else {
                    // Nek drug owner je zasedel PRIMARY => conflict
                    throw new IllegalStateException("PRIMARY is already taken by another instance: " + lock.getInstanceId());
                }
            } else {
                // Ni ga v bazi => poskusimo vstaviti
                AppLock newLock = new AppLock();
                newLock.setName("PRIMARY");
                newLock.setInstanceId(instanceId);
                newLock.setLockedAt(System.currentTimeMillis());
                newLock.setCleanupIntervalMs(taskomirProperties.getCleanupInterval().toMillis());
                newLock.setSucceededRetentionMs(taskomirProperties.getSucceededRetentionTime().toMillis());
                newLock.setDeletedRetentionMs(taskomirProperties.getDeletedRetentionTime().toMillis());
                newLock.setPoolSize(taskomirProperties.getPoolSize());
                newLock.setQueueCapacity(taskomirProperties.getQueueCapacity());

                try {
                    appLockRepository.insert(newLock);
                    log.info("Successfully inserted PRIMARY lock for this instanceId={}.", instanceId);
                } catch (DuplicateKeyException e) {
                    // Race condition => spet conflict
                    log.error("Another instance took PRIMARY at the same time!", e);
                    throw new IllegalStateException("Multiple primary instances not allowed!", e);
                }
            }
        } else {
            log.info("taskomir.primary=false => This instance is secondary.");
        }
    }



    @PreDestroy
    public void releasePrimaryLock() {
        if (taskomirProperties.isPrimary()) {
            // Naj poizkusi zbrisati "PRIMARY" doc
            appLockRepository.deleteById("PRIMARY");
            log.info("Removed PRIMARY lock from the database on shutdown.");
        }
    }

    public Optional<AppLock> getAppLockByName(String name) {
        return appLockRepository.findById(name);
    }

    public Optional<AppLock> getExistingPrimary() {
        return appLockRepository.findById("PRIMARY");
    }
}