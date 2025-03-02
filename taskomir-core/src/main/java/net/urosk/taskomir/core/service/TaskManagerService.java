package net.urosk.taskomir.core.service;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.config.TaskExecutorConfig;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.lib.AbstractScheduledTask;
import net.urosk.taskomir.core.lib.ProgressTask;
import net.urosk.taskomir.core.lib.ProgressUpdater;
import net.urosk.taskomir.core.lib.TaskInfo;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@Slf4j
public class TaskManagerService {

    private final TaskInfoRepository repository;
    private final ThreadPoolExecutor executorService;
    private final TaskExecutorConfig taskExecutorConfig;
    private final MessageSource messageSource;
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public TaskManagerService(TaskInfoRepository repository, ThreadPoolExecutor executorService,
                              TaskExecutorConfig taskExecutorConfig, MessageSource messageSource) {
        this.repository = repository;
        this.executorService = executorService;
        this.taskExecutorConfig = taskExecutorConfig;
        this.messageSource = messageSource;
    }

    public TaskInfo createScheduledTask(String taskName, ProgressTask progressTask, String cronExpression, boolean skipIfAlreadyRunning) {

        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException e) {
            String errorMsg = messageSource.getMessage("invalid.cron", new Object[]{cronExpression}, LocaleContextHolder.getLocale());
            Notification notification = new Notification(errorMsg, 3000);
            notification.setPosition(Notification.Position.TOP_STRETCH);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
            throw new RuntimeException(errorMsg, e);
        }

        String masterId = UUID.randomUUID().toString();

        TaskInfo masterTask = new TaskInfo(masterId, taskName);
        masterTask.setStatus(TaskStatus.SCHEDULED);
        masterTask.setCronExpression(cronExpression);
        masterTask.setClassName(progressTask.getClass().getName());
        masterTask.setSkipIfAlreadyRunning(skipIfAlreadyRunning);

        repository.save(masterTask);

        scheduleMasterTask(masterTask, progressTask);

        return masterTask;
    }

    private void addTask(TaskInfo taskInfo) {
        repository.save(taskInfo);
        String msg = messageSource.getMessage("task.added", new Object[]{taskInfo.getName()}, LocaleContextHolder.getLocale());
        log.info(msg);
    }

    public TaskInfo getTask(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<TaskInfo> getAllTasks() {
        return repository.findAll();
    }

    public void updateTask(TaskInfo taskInfo, TaskStatus status, boolean running) {
        updateTask(taskInfo, status, running, null);
    }

    private void updateTask(TaskInfo taskInfo, TaskStatus status, boolean running, String error) {
        TaskInfo task = repository.findById(taskInfo.getId()).orElse(null);
        if (task != null) {
            task.setProgress(taskInfo.getProgress());
            task.setStatus(status);

            if (status == TaskStatus.PROCESSING && task.getStartedAt() == null) {
                task.setStartedAt(System.currentTimeMillis());
            } else if (status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED) {
                task.setEndedAt(System.currentTimeMillis());
                runningTasks.remove(taskInfo.getId());
            }
            task.setRunning(running);
            task.setError(error);
            if (status == TaskStatus.DELETED) {
                task.setDeletedAt(System.currentTimeMillis());
            } else {
                task.setDeletedAt(null);
            }
            repository.save(task);
        } else {
            taskInfo.setStatus(status);
            taskInfo.setRunning(running);
            taskInfo.setError(error);
            repository.save(taskInfo);
        }
    }

    public Page<TaskInfo> getTasksByStatus(TaskStatus status, Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Scheduled(fixedDelayString = "${taskomir.cleanupIntervalMs:30000}")
    public void cleanupDeletedTasks() {
        String msg = messageSource.getMessage("task.cleanup.info", null, LocaleContextHolder.getLocale());
        log.info(msg);
    }

    public void deleteTasksByStatus(TaskStatus status) {
        List<TaskInfo> tasks = repository.findByStatusOrderByCreatedAtDesc(status);
        for (TaskInfo task : tasks) {
            runningTasks.remove(task.getId());
            repository.delete(task);
        }
        String msg = messageSource.getMessage("task.deleted", new Object[]{status}, LocaleContextHolder.getLocale());
        log.info(msg);
    }

    public boolean cancelTask(String taskId) {
        Future<?> future = runningTasks.remove(taskId);
        if (future != null) {
            boolean canceled = future.cancel(true);
            if (canceled) {
                TaskInfo taskInfo = repository.findById(taskId).orElse(null);
                if (taskInfo != null) {
                    updateTask(taskInfo, TaskStatus.DELETED, false);
                }
                String msg = messageSource.getMessage("task.cancel.success", new Object[]{taskId}, LocaleContextHolder.getLocale());
                log.info(msg);
            } else {
                String warnMsg = messageSource.getMessage("task.cancel.fail", new Object[]{taskId}, LocaleContextHolder.getLocale());
                log.warn(warnMsg);
            }
            return canceled;
        } else {
            TaskInfo taskInfo = repository.findById(taskId).orElse(null);
            if (taskInfo != null) {
                updateTask(taskInfo, TaskStatus.DELETED, false);
            }
            String msg = messageSource.getMessage("task.cancel.success", new Object[]{taskId}, LocaleContextHolder.getLocale());
            log.info(msg);
            return false;
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void checkScheduledTasks() {
        long now = System.currentTimeMillis();
        Instant nowInstant = Instant.ofEpochMilli(now);

        // 1) Preglej SCHEDULED master naloge
        List<TaskInfo> scheduledList = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.SCHEDULED);

        for (TaskInfo master : scheduledList) {
            String cron = master.getCronExpression();
            if (cron == null || cron.isEmpty()) {
                continue;
            }
            try {
                CronExpression cronExp = CronExpression.parse(cron);

                // Kdaj je bil zadnji child?
                Long lastRun = master.getLastRunTime();
                Instant last = (lastRun != null)
                        ? Instant.ofEpochMilli(lastRun)
                        : Instant.EPOCH;

                LocalDateTime lastDateTime = LocalDateTime.ofInstant(last, ZoneId.systemDefault());
                LocalDateTime nextValid = cronExp.next(lastDateTime);
                if (nextValid == null) {
                    continue;
                }
                Instant nextValidInstant = nextValid.atZone(ZoneId.systemDefault()).toInstant();

                if (!nextValidInstant.isAfter(nowInstant)) {
                    // Čas je za nov zagon child-a:
                    enqueueNewChildOf(master);
                    master.setLastRunTime(now);
                    repository.save(master);
                }
            } catch (Exception e) {
                String errorMsg = messageSource.getMessage("error.cron.process", new Object[]{master.getId(), e.getMessage()}, LocaleContextHolder.getLocale());
                log.error(errorMsg);
            }
        }

        // 2) Avtomatsko čiščenje starih SUCCEEDED => DELETED po 1 dnevu
        long dayAgo = now - taskExecutorConfig.succeededRetentionTimeMs;
        List<TaskInfo> succeeded = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.SUCCEEDED);
        for (TaskInfo task : succeeded) {
            if (task.getEndedAt() != null && task.getEndedAt() < dayAgo) {
                updateTask(task, TaskStatus.DELETED, false);
            }
        }

        // 3) Popolno brisanje DELETED nalog, starejših od 7 dni
        long weekAgo = now - taskExecutorConfig.deletedRetentionTimeMs;
        List<TaskInfo> deleted = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.DELETED);
        for (TaskInfo task : deleted) {
            if (task.getDeletedAt() != null && task.getDeletedAt() < weekAgo) {
                repository.delete(task);
            }
        }
    }

    public TaskInfo enqueue(String taskName, ProgressTask task) {
        String taskId = UUID.randomUUID().toString();

        TaskInfo taskInfo = new TaskInfo(taskId, taskName);
        taskInfo.setClassName(task.getClass().getName());

        repository.save(taskInfo);

        ProgressUpdater progressUpdater = new ProgressUpdater(taskInfo, this);

        Future<?> future = executorService.submit(() -> {
            updateTask(taskInfo, TaskStatus.PROCESSING, true);
            try {
                task.execute(progressUpdater);
                taskInfo.setProgress(1.0);
                updateTask(taskInfo, TaskStatus.SUCCEEDED, false);
            } catch (Exception e) {
                updateTask(taskInfo, TaskStatus.FAILED, false, e.getMessage());
            }
        });

        runningTasks.put(taskId, future);
        return taskInfo;
    }

    public void enqueueNewChildOf(TaskInfo masterTask) {
        if (masterTask.isSkipIfAlreadyRunning() && hasActiveChild(masterTask)) {
            String msg = messageSource.getMessage("child.skip.active", new Object[]{masterTask.getId()}, LocaleContextHolder.getLocale());
            log.debug(msg);
            return;
        }

        try {
            String className = masterTask.getClassName();
            Class<?> clz = Class.forName(className);

            if (!AbstractScheduledTask.class.isAssignableFrom(clz)) {
                String errorMsg = messageSource.getMessage("error.not.instance", new Object[]{className}, LocaleContextHolder.getLocale());
                log.error(errorMsg);
                return;
            }
            AbstractScheduledTask scheduledLogic = (AbstractScheduledTask) clz.getDeclaredConstructor().newInstance();
            scheduledLogic.setTaskInfo(masterTask);

            enqueueNewChildOf(masterTask, scheduledLogic);
        } catch (Exception e) {
            String errorMsg = messageSource.getMessage("error.instantiating.task", new Object[]{masterTask.getId(), e.getMessage()}, LocaleContextHolder.getLocale());
            log.error(errorMsg);
        }
    }

    private boolean hasActiveChild(TaskInfo masterTask) {
        List<TaskInfo> activeChildren = repository.findByParentIdAndStatusIn(
                masterTask.getId(),
                Arrays.asList(TaskStatus.ENQUEUED, TaskStatus.PROCESSING)
        );
        return !activeChildren.isEmpty();
    }

    public void enqueueNewChildOf(TaskInfo masterTask, ProgressTask progressTask) {
        String childId = UUID.randomUUID().toString();
        TaskInfo childTask = new TaskInfo(childId, masterTask.getName() + " [CHILD]");
        childTask.setParentId(masterTask.getId());
        childTask.setStatus(TaskStatus.ENQUEUED);

        repository.save(childTask);

        Future<?> future = executorService.submit(() -> {
            updateTask(childTask, TaskStatus.PROCESSING, true);
            try {
                ProgressUpdater progressUpdater = new ProgressUpdater(childTask, this);
                progressTask.execute(progressUpdater);
                childTask.setProgress(1.0);
                updateTask(childTask, TaskStatus.SUCCEEDED, false);
            } catch (Exception e) {
                updateTask(childTask, TaskStatus.FAILED, false, e.getMessage());
            }
        });

        runningTasks.put(childId, future);
        String childMsg = messageSource.getMessage("child.enqueued", new Object[]{childId, masterTask.getId()}, LocaleContextHolder.getLocale());
        log.info(childMsg);
    }

    public void scheduleMasterTask(TaskInfo masterTask, ProgressTask progressTask) {
        String cronExpr = masterTask.getCronExpression();
        if (cronExpr == null || cronExpr.isEmpty()) {
            String warnMsg = messageSource.getMessage("warn.no.cron", new Object[]{masterTask.getId()}, LocaleContextHolder.getLocale());
            log.warn(warnMsg);
            return;
        }

        CronTrigger cronTrigger = new CronTrigger(cronExpr);

        Runnable cronLogic = () -> {
            String cronMsg = messageSource.getMessage("cron.schedule.info", new Object[]{masterTask.getId()}, LocaleContextHolder.getLocale());
            log.info(cronMsg);
            enqueueNewChildOf(masterTask);
        };

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000);
                    cronLogic.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        String scheduledMsg = messageSource.getMessage("cron.scheduled", new Object[]{masterTask.getId(), masterTask.getCronExpression()}, LocaleContextHolder.getLocale());
        log.info(scheduledMsg);
    }

    @PostConstruct
    public void requeueIncompleteTasks() {
        List<TaskStatus> incompleteStatuses = Arrays.asList(TaskStatus.ENQUEUED, TaskStatus.PROCESSING);
        List<TaskInfo> tasks = repository.findByStatusInOrderByCreatedAtDesc(incompleteStatuses, null).getContent();
        for (TaskInfo task : tasks) {
            String requeueMsg = messageSource.getMessage("requeue.info", new Object[]{task.getId(), task.getStatus()}, LocaleContextHolder.getLocale());
            log.info(requeueMsg);
            updateTask(task, TaskStatus.ENQUEUED, false, null);
        }
    }
}
