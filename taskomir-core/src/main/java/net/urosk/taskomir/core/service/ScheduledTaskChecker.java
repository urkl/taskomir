package net.urosk.taskomir.core.service;

import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.config.TaskomirProperties;
import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * This class is responsible for periodically checking the database for scheduled tasks that are ready to run
 * (according to their cron expressions) and for cleaning up old tasks.
 *
 * The @ConditionalOnProperty annotation ensures that this bean (and thus its @Scheduled methods)
 * is only registered when 'taskomir.primary' is set to 'true' or not specified (due to matchIfMissing = true).
 *
 * In other words, only the "primary" instance of the application will execute these scheduling routines:
 *  - cleanupOldTasks(): Automatically transitions SUCCEEDED tasks to DELETED after a configured time,
 *    and physically removes DELETED tasks older than another configured threshold.
 *  - checkScheduledTasks(): Periodically checks if any SCHEDULED tasks are due to be started based on their
 *    cron expressions, and enqueues them if it's time.
 *
 * This prevents multiple application instances from running the same scheduled logic simultaneously if they
 * share the same database.
 */

@Service
@Slf4j
@ConditionalOnProperty(name="taskomir.primary", havingValue="true", matchIfMissing = true)
public class ScheduledTaskChecker {

    private final TaskInfoRepository repository;
    private final TaskLifecycleService taskLifecycleService;
    private final TaskomirProperties taskomirProperties;

    public ScheduledTaskChecker(TaskInfoRepository repository,
                                TaskLifecycleService taskLifecycleService,
                                TaskomirProperties taskomirProperties) {
        this.repository = repository;
        this.taskLifecycleService = taskLifecycleService;
        this.taskomirProperties = taskomirProperties;
    }
    /**
     * Metoda se proži na podlagi konfiguracije (`cleanupInterval`).
     */
    @Scheduled(fixedDelayString = "#{@taskomirProperties.cleanupInterval.toMillis()}")
    public void cleanupOldTasks() {

        log.info("Cleaning up old tasks");
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
    /**
     * Periodično preveri, ali obstaja SCHEDULED naloga, katere 'next execution time' je že pretekel.
     * Interval preverjanja je določen v konfiguraciji `taskomir.scheduledCheckInterval`
     */
    @Scheduled(fixedDelayString = "#{@taskomirProperties.scheduledCheckInterval.toMillis()}")
    public void checkScheduledTasks() {

        log.info("Checking scheduled tasks...");
        long nowMillis = System.currentTimeMillis();
        Instant nowInstant = Instant.ofEpochMilli(nowMillis);

        // 1) Poiščemo vse master naloge v statusu SCHEDULED
        List<TaskInfo> scheduledList = repository.findByStatusOrderByCreatedAtDesc(TaskStatus.SCHEDULED);

        for (TaskInfo master : scheduledList) {
            String cronExpr = master.getCronExpression();
            if (cronExpr == null || cronExpr.isEmpty()) {
                // Če ni crona, ni kaj delati
                continue;
            }

            try {
                CronExpression cron = CronExpression.parse(cronExpr);

                Long lastRun = master.getLastRunTime();
                Instant lastInstant = (lastRun != null)
                        ? Instant.ofEpochMilli(lastRun)
                        : Instant.EPOCH; // ali pa prvič

                LocalDateTime lastDateTime = LocalDateTime.ofInstant(lastInstant, ZoneId.systemDefault());
                LocalDateTime nextValid = cron.next(lastDateTime);
                if (nextValid == null) {
                    // Cron definicija se je iztekla
                    continue;
                }

                Instant nextInstant = nextValid.atZone(ZoneId.systemDefault()).toInstant();
                if (!nextInstant.isAfter(nowInstant)) {
                    // Čas je za nov child zagon
                    taskLifecycleService.enqueueNewChildOf(master);
                    master.setLastRunTime(nowMillis);
                    repository.save(master);
                }

            } catch (Exception e) {
                log.error("Error processing cron for task {}: {}", master.getId(), e.getMessage(), e);
            }
        }
    }
}
