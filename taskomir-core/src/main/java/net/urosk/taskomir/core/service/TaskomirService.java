package net.urosk.taskomir.core.service;

import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.lib.ProgressTask;
import net.urosk.taskomir.core.lib.TaskInfo;
import net.urosk.taskomir.core.lib.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Javna fasada (API), prek katere drugi projekti uporabljajo Taskomir funkcionalnost.
 */
@Service
@Slf4j
public class TaskomirService {

    private final TaskLifecycleService taskLifecycleService;

    public TaskomirService(TaskLifecycleService taskLifecycleService) {
        this.taskLifecycleService = taskLifecycleService;
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
    public TaskInfo enqueue(String taskName, ProgressTask task) {
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

}