package net.urosk.taskomir.core.service;

import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.lib.*;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@Slf4j
public class TaskLifecycleService {

    private final TaskInfoRepository repository;
    private final ThreadPoolExecutor executorService;
    private final MessageSource messageSource;
    private final ApplicationContext applicationContext;

    // Beležimo vse, ki so trenutno v teku (ENQUEUED ali PROCESSING)
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public TaskLifecycleService(TaskInfoRepository repository,
                                ThreadPoolExecutor executorService,
                                MessageSource messageSource,
                                ApplicationContext applicationContext) {
        this.repository = repository;
        this.executorService = executorService;
        this.messageSource = messageSource;
        this.applicationContext = applicationContext;
    }

    /**
     * Ustvari "master" SCHEDULED nalogo z danim cronExpression.
     * Ko pride čas, bo ScheduledTaskChecker to nalogo sprožil (enqueueNewChildOf).
     */
    public TaskInfo createScheduledTask(String taskName,
                                        ProgressTask progressTask,
                                        String cronExpression,
                                        boolean skipIfAlreadyRunning) {
        // Preveri, če je veljaven cron (vrže izjemo, če ne)
        CronExpression.parse(cronExpression);

        String masterId = UUID.randomUUID().toString();
        TaskInfo masterTask = new TaskInfo(masterId, taskName);
        masterTask.setStatus(TaskStatus.SCHEDULED);
        masterTask.setCronExpression(cronExpression);
        // className == klasa implementacije (lahko Spring bean ali plain)
        masterTask.setClassName(progressTask.getClass().getName());
        masterTask.setSkipIfAlreadyRunning(skipIfAlreadyRunning);

        repository.save(masterTask);

        log.info("Master SCHEDULED task {} created with cron {}", masterId, cronExpression);
        return masterTask;
    }

    /**
     * Za enkraten zagon brez crona.
     */
    public CompletableFuture<TaskInfo> enqueue(String taskName, ProgressTask task) {
        String taskId = UUID.randomUUID().toString();

        TaskInfo taskInfo = new TaskInfo(taskId, taskName);
        taskInfo.setClassName(task.getClass().getName());
        taskInfo.setStatus(TaskStatus.ENQUEUED);
        repository.save(taskInfo);


        CompletableFuture<TaskInfo> future = CompletableFuture.supplyAsync(() -> {
            updateTask(taskInfo, TaskStatus.PROCESSING, true);
            try {
                ProgressUpdater updater = new ProgressUpdater(taskInfo, this);
                task.execute(updater);
                taskInfo.setProgress(1.0);
                updateTask(taskInfo, TaskStatus.SUCCEEDED, false);
                return taskInfo; // Vrne TaskInfo
            } catch (Exception e) {
                updateTask(taskInfo, TaskStatus.FAILED, false, e.getMessage());
            }

            return taskInfo;

        }, executorService);

        runningTasks.put(taskId, future);
        log.info("Enqueued task {}", taskId);
        return future;
    }

    /**
     * Kliče se iz ScheduledTaskChecker, ko cron definicija pravi, da je čas za nov "child".
     * Preveri skipIfAlreadyRunning, ustvari (ali dobi) instanco logic (Spring bean?), zažene child.
     */
    public void enqueueNewChildOf(TaskInfo masterTask) {
        if (masterTask.isSkipIfAlreadyRunning() && hasActiveChild(masterTask)) {
            String msg = messageSource.getMessage(
                    "child.skip.active",
                    new Object[]{masterTask.getId()},
                    LocaleContextHolder.getLocale()
            );
            log.debug(msg);
            return;
        }

        AbstractScheduledTask logic = buildScheduledTask(masterTask);
        if (logic == null) {
            // Napaka pri kreiranju => označi master kot FAILED
            updateTask(masterTask, TaskStatus.FAILED, false, "Error instantiating scheduled task");
            return;
        }
        enqueueNewChildOf(masterTask, logic);
    }

    /**
     * Sprejme konkretno ProgressTask instanco in jo zažene kot child.
     */
    public void enqueueNewChildOf(TaskInfo masterTask, ProgressTask progressTask) {
        String childId = UUID.randomUUID().toString();
        TaskInfo child = new TaskInfo(childId, masterTask.getName() + " [CHILD]");
        child.setParentId(masterTask.getId());
        child.setStatus(TaskStatus.ENQUEUED);
        child.setClassName(masterTask.getClassName());
        repository.save(child);

        Future<?> future = executorService.submit(() -> {
            updateTask(child, TaskStatus.PROCESSING, true);
            try {
                ProgressUpdater updater = new ProgressUpdater(child, this);
                progressTask.execute(updater);
                child.setProgress(1.0);
                updateTask(child, TaskStatus.SUCCEEDED, false);
            } catch (Exception e) {
                updateTask(child, TaskStatus.FAILED, false, e.getMessage());
            }
        });

        runningTasks.put(childId, future);
        log.info("Enqueued child {} for master {}", childId, masterTask.getId());
    }

    /**
     * Poskusi pridobiti Spring bean iz className, sicer navadno newInstance.
     */
    private AbstractScheduledTask buildScheduledTask(TaskInfo masterTask) {
        try {
            String className = masterTask.getClassName();
            Class<?> clz = Class.forName(className);

            if (!AbstractScheduledTask.class.isAssignableFrom(clz)) {
                log.error("Class {} is not a AbstractScheduledTask", className);
                return null;
            }

            AbstractScheduledTask logic;
            try {
                logic = (AbstractScheduledTask) applicationContext.getBean(clz);
            } catch (NoSuchBeanDefinitionException e) {
                logic = (AbstractScheduledTask) clz.getDeclaredConstructor().newInstance();
            }

            logic.setTaskInfo(masterTask);
            return logic;

        } catch (Exception ex) {
            log.error("Error creating scheduled logic for task {}: {}", masterTask.getId(), ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Preveri, ali obstaja aktiven child (ENQUEUED ali PROCESSING).
     */
    private boolean hasActiveChild(TaskInfo master) {
        List<TaskInfo> activeChildren = repository.findByParentIdAndStatusIn(
                master.getId(),
                Arrays.asList(TaskStatus.ENQUEUED, TaskStatus.PROCESSING)
        );
        return !activeChildren.isEmpty();
    }

    /**
     * Posodobi "task" in zapiše v bazo; če gre v SUCCEEDED/FAILED, ga odstrani iz runningTasks.
     */
    public void updateTask(TaskInfo taskInfo, TaskStatus newStatus, boolean running) {
        updateTask(taskInfo, newStatus, running, null);
    }

    public void updateTask(TaskInfo taskInfo, TaskStatus newStatus, boolean running, String error) {
        TaskInfo stored = repository.findById(taskInfo.getId()).orElse(null);
        if (stored != null) {
            stored.setProgress(taskInfo.getProgress());
            stored.setStatus(newStatus);
            stored.setCurrentProgress(taskInfo.getCurrentProgress());
            //stored.addLogLine("[" + LocalDateTime.now() + "] Progress: " + (taskInfo.getProgress() * 100) + "%" + " - " + taskInfo.getCurrentProgress());

            stored.addLogLine(String.format("[%s] Progress: %.2f%% - %s",
                    LocalDateTime.now(),
                    taskInfo.getProgress() * 100,
                    taskInfo.getCurrentProgress() != null ? taskInfo.getCurrentProgress() : ""
            ));

            if (newStatus == TaskStatus.PROCESSING && stored.getStartedAt() == null) {
                stored.setStartedAt(System.currentTimeMillis());
            } else if (newStatus == TaskStatus.SUCCEEDED || newStatus == TaskStatus.FAILED) {
                stored.setEndedAt(System.currentTimeMillis());
                runningTasks.remove(taskInfo.getId());
            }
            stored.setRunning(running);
            stored.setError(error);
            if (newStatus == TaskStatus.DELETED) {
                stored.setDeletedAt(System.currentTimeMillis());
            }
            repository.save(stored);
        } else {
            // fallback, če je ni v bazi
            taskInfo.setStatus(newStatus);
            taskInfo.setRunning(running);
            taskInfo.setError(error);
            if (newStatus == TaskStatus.DELETED) {
                taskInfo.setDeletedAt(System.currentTimeMillis());
            }
            repository.save(taskInfo);
        }
    }

    /**
     * Cancel "running" ali "enqueued" nalogo
     */
    public boolean cancelTask(String taskId) {
        Future<?> future = runningTasks.remove(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                TaskInfo info = repository.findById(taskId).orElse(null);
                if (info != null) {
                    updateTask(info, TaskStatus.DELETED, false);
                }
            }
            return cancelled;
        } else {
            // Ni v runningTasks, vseeno označimo kot DELETED, če obstaja v bazi
            TaskInfo info = repository.findById(taskId).orElse(null);
            if (info != null) {
                updateTask(info, TaskStatus.DELETED, false);
            }
            return false;
        }
    }

    // Primer dodatnih metod
    public TaskInfo getTask(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<TaskInfo> getAllTasks() {
        return repository.findAll();
    }

    public Page<TaskInfo> getTasksByStatus(TaskStatus status, Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    public void deleteTasksByStatus(TaskStatus taskStatus) {
        List<TaskInfo> tasks = repository.findByStatusOrderByCreatedAtDesc(taskStatus);
        for (TaskInfo task : tasks) {
            runningTasks.remove(task.getId());
            repository.delete(task);
        }
        String msg = messageSource.getMessage("task.deleted", new Object[]{taskStatus}, LocaleContextHolder.getLocale());
        log.info(msg);
    }

    public Optional<TaskInfo> findByNameAndStatus(String taskName, TaskStatus taskStatus) {
        return repository.findByNameAndStatus(taskName, taskStatus);
    }
}
