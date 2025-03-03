package net.urosk.taskomir.core.lib;


import lombok.extern.slf4j.Slf4j;
import net.urosk.taskomir.core.service.TaskLifecycleService;


@Slf4j
public class ProgressUpdater {

    private final TaskInfo taskInfo;
    private final TaskLifecycleService taskLifecycleService;

    public ProgressUpdater(TaskInfo taskInfo, TaskLifecycleService taskLifecycleService) {
        this.taskInfo = taskInfo;
        this.taskLifecycleService = taskLifecycleService;
    }

    public void update(double progress, String progressText) {
        if (progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Progress should be between 0.0 in 1.0");
        }
        taskInfo.setProgress(progress);
        taskInfo.setCurrentProgress(progressText);
        taskLifecycleService.updateTask(taskInfo, TaskStatus.PROCESSING, true);
     //   log.info("Napredek za task {}: {}%", taskInfo.getId(), (int) (progress * 100));
    }
}