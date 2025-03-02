package net.urosk.taskomir.core.lib;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "tasks")
public class TaskInfo {
    @Id
    String id;
    private String name;
    private double progress = 0.0;
    private TaskStatus status = TaskStatus.ENQUEUED;
    private boolean running = false;
    private String error;
    private Long deletedAt;
    private Long createdAt = System.currentTimeMillis();
    private Long startedAt;
    private Long endedAt;
    private String className;
    private String cronExpression;
    private Long lastRunTime;
    private String parentId;
    /**
     * If true, the task will be skipped if it is already running
     * Relevant just for Scheduled tasks
     */
    private boolean skipIfAlreadyRunning = false;
    public TaskInfo() {
    }

    public TaskInfo(String id, String taskName) {
        this.id = id;
        this.name = taskName;
    }
}
