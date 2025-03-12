package net.urosk.taskomir.core.service;

import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.ProgressTask;
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


import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers()

class TaskLifecycleServiceIntegrationTest {

    // Uporabimo MongoDB Docker sliko; lahko prilagodiš verzijo po potrebi
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    /**
     * Dinamično nastavi lastnost 'spring.data.mongodb.uri', tako da Spring Boot
     * ve, kam se povezati. Ta metoda prebere URI iz Testcontainers containerja.
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private TaskLifecycleService taskLifecycleService;

    @Autowired
    private TaskInfoRepository repository;

    @BeforeEach
    void setUp() {
        // Pred vsakim testom pobriši vse naloge, da imaš čist začetek
        repository.deleteAll();
    }

    /**
     * Dummy implementacija ProgressTask za integracijske teste.
     * Ta implementacija simulira nekaj dela (spanje 200ms) in nato zaključi z 100% napredka.
     */
    static class DummyProgressTask implements ProgressTask {
        @Override
        public void execute(net.urosk.taskomir.core.lib.ProgressUpdater updater) throws Exception {
            Thread.sleep(200);
            updater.update(1.0, "Completed");
        }
    }

    /**
     * Testira ustvarjanje scheduled naloge.
     * Preveri, da se naloga ustrezno shrani v bazo s statusom SCHEDULED.
     */
    @Test
    void testCreateScheduledTaskIntegration() {
        // Arrange
        ProgressTask dummyTask = new DummyProgressTask();
        String cronExpression = "0 0 12 * * ?"; // vsak dan ob 12:00
        String taskName = "IntegrationScheduledTask";

        // Act
        TaskInfo masterTask = taskLifecycleService.createScheduledTask(taskName, dummyTask, cronExpression, true);

        // Assert
        assertNotNull(masterTask);
        assertThat(masterTask.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(masterTask.getCronExpression()).isEqualTo(cronExpression);
        Optional<TaskInfo> found = repository.findById(masterTask.getId());
        assertTrue(found.isPresent(), "Scheduled task should be stored in the repository.");
    }

    /**
     * Testira metodo enqueue() za enkratno zagon naloge.
     * DummyProgressTask simulira nekaj dela in nato uspešno zaključi nalogo.
     */
    @Test
    void testEnqueueIntegration() throws Exception {
        // Arrange
        ProgressTask dummyTask = new DummyProgressTask();
        String taskName = "IntegrationOneOffTask";

        // Act
        CompletableFuture<TaskInfo> future = taskLifecycleService.enqueue(taskName, dummyTask);
        TaskInfo result = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertNotNull(result);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.ENQUEUED);
    }

    /**
     * Test updateTask() metode.
     * Ta test ustvari dummy nalogo, jo shrani v bazo, in nato posodobi status na SUCCEEDED.
     */
    @Test
    void testUpdateTaskIntegration() {
        // Arrange: ročno ustvarimo TaskInfo in ga shranimo v bazo.
        TaskInfo task = new TaskInfo("update-test", "TaskToUpdate");
        task.setStatus(TaskStatus.PROCESSING);
        repository.save(task);

        // Act: posodobimo nalogo na SUCCEEDED s 75% napredka.
        TaskInfo update = new TaskInfo("update-test", "TaskToUpdate");
        update.setProgress(0.75);
        taskLifecycleService.updateTask(update, TaskStatus.SUCCEEDED, false, null);

        // Assert
        Optional<TaskInfo> found = repository.findById("update-test");
        assertTrue(found.isPresent());
        TaskInfo updatedTask = found.get();
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(updatedTask.getProgress()).isEqualTo(0.75);
        assertNotNull(updatedTask.getEndedAt(), "The endedAt timestamp should be set when the task is completed.");
    }

    @Test
    void testCancelTaskIntegration() throws Exception {
        // Arrange: Ročno ustvarimo TaskInfo in ga shranimo v bazo.
        TaskInfo task = new TaskInfo("cancel-test", "TaskToCancel");
        task.setStatus(TaskStatus.ENQUEUED);
        repository.save(task);

        // Uporabimo Mockito za ustvarjanje dummy Future, ki vrne true pri cancel(true)
        Future<?> mockFuture = mock(Future.class);
        when(mockFuture.cancel(true)).thenReturn(true);

        // Dodamo mockFuture v runningTasks (predpostavljamo, da imamo getter za runningTasks)
        taskLifecycleService.getRunningTasks().put("cancel-test", mockFuture);

        // Act: Pokličemo cancelTask()
        boolean cancelled = taskLifecycleService.cancelTask("cancel-test");

        // Assert: Preverimo, da je metoda vrnila true in da se je status naloge posodobil na DELETED.
        assertTrue(cancelled, "Task should be canceled successfully.");
        Optional<TaskInfo> found = repository.findById("cancel-test");
        assertTrue(found.isPresent(), "Task should exist in repository after cancel.");
        assertThat(found.get().getStatus()).isEqualTo(TaskStatus.DELETED);
    }

}
