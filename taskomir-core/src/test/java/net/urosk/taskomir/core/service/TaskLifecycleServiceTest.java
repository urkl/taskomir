package net.urosk.taskomir.core.service;

import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.AbstractScheduledTask;
import net.urosk.taskomir.core.lib.ProgressTask;
import net.urosk.taskomir.core.lib.ProgressUpdater;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskLifecycleService}.
 *
 * These tests use Mockito to mock dependencies such as {@link TaskInfoRepository},
 * {@link ThreadPoolExecutor}, {@link MessageSource}, and {@link ApplicationContext},
 * so that we can verify the internal logic without requiring a real database or asynchronous execution.
 *
 * Lenient stubbing is enabled to avoid errors about unnecessary stubbings.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskLifecycleServiceTest {

    @Mock
    private TaskInfoRepository repository;

    @Mock
    private ThreadPoolExecutor executorService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private TaskLifecycleService taskLifecycleService;

    /**
     * Dummy implementation of an asynchronous task.
     */
    private static class DummyProgressTask implements ProgressTask {
        @Override
        public void execute(net.urosk.taskomir.core.lib.ProgressUpdater updater) throws Exception {
            // For testing, do nothing (or simulate brief work)
            updater.update(1.0, "Done");
        }
    }

    /**
     * Dummy Scheduled Task that extends AbstractScheduledTask.
     * This is used to ensure that buildScheduledTask() does not return null.
     */
    private static class DummyScheduledTask extends AbstractScheduledTask {
        @Override
        public void execute(net.urosk.taskomir.core.lib.ProgressUpdater updater) throws Exception {
            updater.update(1.0, "Dummy scheduled task executed");
        }

        @Override
        protected void runScheduledLogic(ProgressUpdater updater) throws Exception {

        }
    }

    /**
     * Set up default stubs before each test.
     * We stub repository.findById() to return an empty Optional by default and
     * force executorService.submit() and execute() to run tasks synchronously.
     */
    @BeforeEach
    void setUp() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        // Stub executorService.submit(Callable) to execute the callable synchronously
        when(executorService.submit(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            try {
                Object result = callable.call();
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        });

        // Stub executorService.submit(Runnable) to run the task and return a completed Future
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return CompletableFuture.completedFuture(null);
        });

        // Stub executorService.execute(Runnable) to run the Runnable immediately
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
    }

    /**
     * Test createScheduledTask() to ensure that:
     * - The cron expression is validated.
     * - A new TaskInfo with SCHEDULED status is created and saved.
     * - The returned TaskInfo has the expected fields.
     */
    @Test
    void testCreateScheduledTask() {
        // Given a fake progressTask and a valid cron expression
        ProgressTask mockTask = mock(ProgressTask.class);
        String cronExpression = "0 0 * * * ?"; // top of every hour
        String taskName = "HourlyTask";

        // When we call createScheduledTask
        TaskInfo result = taskLifecycleService.createScheduledTask(taskName, mockTask, cronExpression, true);

        // Then verify that repository.save() is called with a TaskInfo having the expected properties
        verify(repository).save(argThat(taskInfo ->
                taskInfo.getName().equals(taskName)
                        && taskInfo.getStatus() == TaskStatus.SCHEDULED
                        && cronExpression.equals(taskInfo.getCronExpression())
                        && taskInfo.isSkipIfAlreadyRunning()
        ));

        // Also, check that the returned TaskInfo has the expected fields
        assertThat(result.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(result.getCronExpression()).isEqualTo(cronExpression);
        assertThat(result.getName()).isEqualTo(taskName);
    }

    /**
     * Test that createScheduledTask() throws an exception for an invalid cron expression.
     */
    @Test
    void testCreateScheduledTaskWithInvalidCronThrows() {
        ProgressTask mockTask = mock(ProgressTask.class);
        String invalidCron = "invalid-cron-string";

        assertThatThrownBy(() ->
                taskLifecycleService.createScheduledTask("BadTask", mockTask, invalidCron, false)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEnqueue() throws Exception {
        // Given a mock ProgressTask and a task name.
        ProgressTask mockTask = mock(ProgressTask.class);
        String taskName = "OneOffTask";

        // Capture all calls to repository.save()
        ArgumentCaptor<TaskInfo> captor = ArgumentCaptor.forClass(TaskInfo.class);

        // When: call enqueue() (executorService is stubbed to run synchronously)
        CompletableFuture<TaskInfo> future = taskLifecycleService.enqueue(taskName, mockTask);
        TaskInfo finalTask = future.get(5, TimeUnit.SECONDS); // Wait for result

        // Then: finalTask should have SUCCEEDED status
        assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);

        // Capture all invocations of repository.save()
        verify(repository, atLeastOnce()).save(captor.capture());
        List<TaskInfo> savedTasks = captor.getAllValues();

        // Assert that among all saved TaskInfo objects at least one matches the expected properties.
        boolean succeededFound = savedTasks.stream().anyMatch(taskInfo ->
                taskName.equals(taskInfo.getName()) &&
                        taskInfo.getStatus() == TaskStatus.SUCCEEDED &&
                        taskInfo.getProgress() == 1.0
        );
        assertTrue(succeededFound, "Expected a repository.save() invocation that sets the task status to SUCCEEDED with progress 1.0");
    }

    /**
     * Test enqueue() when the ProgressTask throws an exception.
     * In this case, the final status should be FAILED.
     */
    @Test
    void testEnqueueTaskFails() throws Exception {
        // Given a mock progress task that throws an exception
        ProgressTask failingTask = mock(ProgressTask.class);
        doThrow(new RuntimeException("Boom!")).when(failingTask).execute(any());

        // When we call enqueue()
        CompletableFuture<TaskInfo> future = taskLifecycleService.enqueue("FailingTask", failingTask);
        TaskInfo finalTask = future.get(5, TimeUnit.SECONDS);

        // Then, final status should be FAILED
        assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        verify(repository, atLeastOnce()).save(argThat(t -> t.getStatus() == TaskStatus.FAILED));
    }

    /**
     * Test enqueueNewChildOf() in the scenario where skipIfAlreadyRunning is true and an active child exists.
     * Expect that no new child task is created.
     */
    @Test
    void testEnqueueNewChildOf_skipIfAlreadyRunning() {
        // Given a master task with skipIfAlreadyRunning = true
        TaskInfo master = new TaskInfo("master-123", "MasterScheduled");
        master.setSkipIfAlreadyRunning(true);

        // Simulate an active child exists.
        when(repository.findByParentIdAndStatusIn(eq("master-123"), anyList()))
                .thenReturn(List.of(new TaskInfo("child-running", "SomeChild")));

        // Stub messageSource for the skip message.
        when(messageSource.getMessage(eq("child.skip.active"), any(), any()))
                .thenReturn("Child is active, skipping.");

        // When we call enqueueNewChildOf()
        taskLifecycleService.enqueueNewChildOf(master);

        // Then, verify that repository.save() is never called with a new child task.
        verify(repository, never()).save(argThat(t -> "MasterScheduled [CHILD]".equals(t.getName())));
    }

    /**
     * Test enqueueNewChildOf() when there is no active child and skipIfAlreadyRunning is false.
     * Expect that a new child task is created and submitted to the executor.
     */
    @Test
    void testEnqueueNewChildOf_createsChildIfNoActive() {
        // Given a master task with skipIfAlreadyRunning = false.
        TaskInfo master = new TaskInfo("master-999", "MasterScheduled");
        master.setSkipIfAlreadyRunning(false);
        // IMPORTANT: Nastavimo className na DummyScheduledTask, da buildScheduledTask() vrne instanco in ne null.
        master.setClassName(DummyScheduledTask.class.getName());

        // Simulate that there is no active child.
        when(repository.findByParentIdAndStatusIn(eq("master-999"), anyList()))
                .thenReturn(Collections.emptyList());

        // Stub applicationContext.getBean(...) to return a mock AbstractScheduledTask.
        AbstractScheduledTask mockLogic = mock(AbstractScheduledTask.class);
        try {
            when(applicationContext.getBean(any(Class.class))).thenReturn(mockLogic);
        } catch (NoSuchBeanDefinitionException e) {
            fail("Should not throw NoSuchBeanDefinitionException in test stub");
        }

        // When: call enqueueNewChildOf()
        taskLifecycleService.enqueueNewChildOf(master);

        // Capture all invocations of repository.save()
        ArgumentCaptor<TaskInfo> captor = ArgumentCaptor.forClass(TaskInfo.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        // Filter captured TaskInfos to find one with the expected properties.
        boolean childCreated = captor.getAllValues().stream()
                .anyMatch(taskInfo -> master.getId().equals(taskInfo.getParentId()) &&
                        "MasterScheduled [CHILD]".equals(taskInfo.getName()));
        assertTrue(childCreated, "A new child task should have been created with the correct parentId and name.");

        // Also verify that a job was submitted to the executor.
        verify(executorService).submit(any(Runnable.class));
    }


    /**
     * Test cancelTask() for a task present in runningTasks.
     * Ensure that the task is canceled (Future.cancel returns true) and its status is updated to DELETED.
     */
    @Test
    void testCancelTask_runningTask() {
        // Suppose a task with id="task-1" is present in runningTasks.
        Future<?> mockFuture = mock(Future.class);
        when(mockFuture.cancel(true)).thenReturn(true);
        taskLifecycleService.getRunningTasks().put("task-1", mockFuture);

        // Simulate repository returning a TaskInfo for task-1.
        TaskInfo info = new TaskInfo("task-1", "Some Running");
        when(repository.findById("task-1")).thenReturn(Optional.of(info));

        // When: call cancelTask()
        boolean result = taskLifecycleService.cancelTask("task-1");

        // Then: Verify that the task was canceled and its status updated to DELETED.
        assertTrue(result, "Task should be canceled successfully.");
        verify(mockFuture).cancel(true);
        verify(repository).save(argThat(t -> t.getStatus() == TaskStatus.DELETED));
    }

    /**
     * Test cancelTask() when the task is not in runningTasks.
     * In this case, if the task exists in the repository, its status is updated to DELETED,
     * but the method returns false.
     */
    @Test
    void testCancelTask_notInRunningTasks() {
        // Given a TaskInfo that exists in repository but not in runningTasks.
        TaskInfo info = new TaskInfo("task-2", "Some queued or done");
        info.setStatus(TaskStatus.ENQUEUED);
        when(repository.findById("task-2")).thenReturn(Optional.of(info));

        // When: call cancelTask()
        boolean result = taskLifecycleService.cancelTask("task-2");

        // Then: The method should return false, but the task should be updated to DELETED.
        assertFalse(result, "Task should not be canceled via Future if not in runningTasks.");
        verify(repository).save(argThat(t -> t.getStatus() == TaskStatus.DELETED));
    }

    /**
     * Test updateTask() to ensure that:
     * - The stored TaskInfo is updated with the new status, progress, and endedAt timestamp.
     * - The task is removed from runningTasks if the final status is SUCCEEDED.
     */
    @Test
    void testUpdateTask() {
        // Arrange: Create a stored TaskInfo in the repository.
        TaskInfo stored = new TaskInfo("update-123", "UpdateMe");
        stored.setStatus(TaskStatus.PROCESSING);
        when(repository.findById("update-123")).thenReturn(Optional.of(stored));

        // Also add the task to runningTasks.
        Future<?> mockFuture = mock(Future.class);
        taskLifecycleService.getRunningTasks().put("update-123", mockFuture);

        // Act: Call updateTask to mark it SUCCEEDED.
        TaskInfo pseudoUpdate = new TaskInfo("update-123", "DoesntMatter");
        pseudoUpdate.setProgress(0.8);
        taskLifecycleService.updateTask(pseudoUpdate, TaskStatus.SUCCEEDED, false, null);

        // Assert: Verify that the stored TaskInfo is updated correctly.
        verify(repository).save(argThat(t ->
                "update-123".equals(t.getId())
                        && t.getStatus() == TaskStatus.SUCCEEDED
                        && t.getProgress() == 0.8
                        && t.getEndedAt() != null
        ));
        // Also, runningTasks should no longer contain the task.
        assertThat(taskLifecycleService.getRunningTasks()).doesNotContainKey("update-123");
    }

    /**
     * Test getTask() to verify that it correctly delegates to repository.findById().
     */
    @Test
    void testGetTask() {
        // Arrange
        TaskInfo info = new TaskInfo("someId", "SomeTask");
        when(repository.findById("someId")).thenReturn(Optional.of(info));

        // Act
        TaskInfo result = taskLifecycleService.getTask("someId");

        // Assert
        assertThat(result).isSameAs(info);
        verify(repository).findById("someId");
    }
}
