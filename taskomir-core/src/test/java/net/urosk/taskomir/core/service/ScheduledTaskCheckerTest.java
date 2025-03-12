package net.urosk.taskomir.core.service;

import net.urosk.taskomir.core.config.TaskomirProperties;
import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.repository.TaskInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskCheckerTest {

    @Mock
    TaskInfoRepository repository;
    @Mock
    TaskLifecycleService lifecycleService;
    @Mock
    TaskomirProperties properties;

    ScheduledTaskChecker checker; // Testiran razred

    @BeforeEach
    void setUp() {
        checker = new ScheduledTaskChecker(repository, lifecycleService, properties);
    }

    @Test
    void testCleanupOldTasks() {
        // 1) Nastavimo mock TaskomirProperties => cleanupInterval, retentionTimes...
        when(properties.getSucceededRetentionTime()).thenReturn(Duration.ofHours(24));
        when(properties.getDeletedRetentionTime()).thenReturn(Duration.ofDays(7));

        // 2) Pripravimo testne podatke (npr. ena SUCCEEDED naloga, ena DELETED...).
        TaskInfo succeeded = new TaskInfo("suc-123", "SomeSucceededTask");
        succeeded.setStatus(TaskStatus.SUCCEEDED);
        succeeded.setEndedAt(System.currentTimeMillis() - 25 * 3600_000); // >24h nazaj

        TaskInfo deleted = new TaskInfo("del-321", "SomeDeletedTask");
        deleted.setStatus(TaskStatus.DELETED);
        deleted.setDeletedAt(System.currentTimeMillis() - 8L * 24 * 3600_000); // >7 dni nazaj

        when(repository.findByStatusOrderByCreatedAtDesc(TaskStatus.SUCCEEDED))
                .thenReturn(List.of(succeeded));
        when(repository.findByStatusOrderByCreatedAtDesc(TaskStatus.DELETED))
                .thenReturn(List.of(deleted));

        // 3) Pokličemo metodo
        checker.cleanupOldTasks();

        // 4) Preverimo, da sta se obe nalogi "posodobili" oz. zbrisali
        verify(repository).save(argThat(task ->
                task.getId().equals("suc-123") && task.getStatus() == TaskStatus.DELETED
        ));
        verify(repository).delete(argThat(task ->
                task.getId().equals("del-321")
        ));
    }
}