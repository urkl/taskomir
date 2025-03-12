package net.urosk.taskomir.core.service;

import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.AbstractScheduledTask;
import net.urosk.taskomir.core.lib.ProgressUpdater;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ScheduledTaskCheckerIntegrationTest {

    // Uporabimo MongoDB container iz Dockerja (npr. mongo:4.4.3)
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.3");

    /**
     * Dinamično nastavi lastnost 'spring.data.mongodb.uri', da se Spring Boot poveže na MongoDB container.
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private ScheduledTaskChecker scheduledTaskChecker;

    @Autowired
    private TaskInfoRepository repository;

    @Autowired
    private TaskLifecycleService taskLifecycleService;

    @Autowired
    private net.urosk.taskomir.core.config.TaskomirProperties properties;

    @BeforeEach
    void setUp() {
        // Počistimo bazo pred vsakim testom
        repository.deleteAll();
    }

    /**
     * Dummy implementacija DummyScheduledTask, ki razširi AbstractScheduledTask.
     * Ta implementacija se uporablja pri testiranju metode checkScheduledTasks.
     */
    public static class DummyScheduledTask extends AbstractScheduledTask {
        @Override
        public void execute(ProgressUpdater updater) throws Exception {
            // Simuliraj preprost task, ki takoj zaključi z 100% napredka
            updater.update(1.0, "Dummy task executed");
        }

        @Override
        protected void runScheduledLogic(ProgressUpdater updater) throws Exception {

        }
    }

    /**
     * Testira metodo checkScheduledTasks():
     * Ustvari master nalogo s statusom SCHEDULED in zelo starim lastRunTime,
     * pri čemer nastavi tudi className na DummyScheduledTask,
     * nato pokliče metodo in preveri, da je bil ustvarjen child task.
     */
    @Test
    void testCheckScheduledTasks() {
        long now = System.currentTimeMillis();

        // Ustvari master nalogo z veljavnimi podatki:
        TaskInfo masterTask = new TaskInfo(UUID.randomUUID().toString(), "ScheduledMasterTask");
        masterTask.setStatus(TaskStatus.SCHEDULED);
        // Uporabi veljaven cron izraz, ki se sproži vsak dan ob 12:00
        String cronExpression = "0 0 12 * * ?";
        masterTask.setCronExpression(cronExpression);
        // Nastavi lastRunTime tako, da je zelo star (npr. 24 ur nazaj)
        masterTask.setLastRunTime(now - 24 * 3600_000L);
        // Ključna nastavitev: setClassName na DummyScheduledTask, da se lahko dinamično ustvari instanca
        masterTask.setClassName(DummyScheduledTask.class.getName());

        // Shrani master nalogo
        repository.save(masterTask);

        // Preveri, da pred klicem ni child nalog
        List<TaskInfo> childrenBefore = repository.findByParentIdAndStatusIn(
                masterTask.getId(),
                Arrays.asList(TaskStatus.ENQUEUED, TaskStatus.PROCESSING)
        );
        assertTrue(childrenBefore.isEmpty(), "Pred klicem ni child nalog");

        // Pokliči checkScheduledTasks, ki naj preveri in sproži child nalogo
        scheduledTaskChecker.checkScheduledTasks();

        // Preveri, da se je master naloga posodobila (lastRunTime je bil nadomeščen)
        Optional<TaskInfo> updatedMasterOpt = repository.findById(masterTask.getId());
        assertTrue(updatedMasterOpt.isPresent());
        TaskInfo updatedMaster = updatedMasterOpt.get();
        // Preverimo, da je lastRunTime nova (približno trenutna vrednost)
        long newLastRunTime = updatedMaster.getLastRunTime();
        assertTrue(newLastRunTime > now, "Master task's lastRunTime mora biti posodobljen");

        // Preveri, da je bil ustvarjen vsaj en child task
        List<TaskInfo> childrenAfter = repository.findByParentIdAndStatusIn(
                masterTask.getId(),
                Arrays.asList(TaskStatus.ENQUEUED, TaskStatus.PROCESSING)
        );
        assertFalse(childrenAfter.isEmpty(), "Child naloga mora biti ustvarjena");
    }
}
