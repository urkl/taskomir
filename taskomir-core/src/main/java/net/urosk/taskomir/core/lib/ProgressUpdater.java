package net.urosk.taskomir.core.lib;


import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.service.TaskManagerService;

@Slf4j
public class ProgressUpdater {

    private final TaskInfo taskInfo;
    private final TaskManagerService taskManager;

    public ProgressUpdater(TaskInfo taskInfo, TaskManagerService taskManager) {
        this.taskInfo = taskInfo;
        this.taskManager = taskManager;
    }

    public void update(double progress) {
        if (progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Progress should be between 0.0 in 1.0");
        }
        taskInfo.setProgress(progress);
        taskManager.updateTask(taskInfo, TaskStatus.PROCESSING, true);
     //   log.info("Napredek za task {}: {}%", taskInfo.getId(), (int) (progress * 100));
    }
}