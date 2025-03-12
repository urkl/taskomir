package net.urosk.taskomir.core.service;

import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.config.TaskomirProperties;
import net.urosk.taskomir.core.lib.TaskInfo;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Odgovoren za periodično preverjanje (npr. vsakih X sekund),
 * ali je katera 'SCHEDULED' naloga "dozorela" za nov child zagon
 * glede na njeno cron definicijo.
 */
@Service
@Slf4j
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
     * Periodično preveri, ali obstaja SCHEDULED naloga, katere 'next execution time' je že pretekel.
     * Interval preverjanja je določen v konfiguraciji `taskomir.scheduledCheckInterval`
     */
    @Scheduled(fixedDelayString = "#{@taskomirProperties.scheduledCheckInterval.toMillis()}")
    public void checkScheduledTasks() {
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
