package net.urosk.taskomir.core.lib;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "tasks")
public class TaskInfo {
    @Id
    String id;
    private String name;
    private double progress = 0.0;
    private String currentProgress;
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
    private List<String> logLines; // Lahko ali pa: private List<String> log; odvisno od potreb

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

    public void addLogLine(String line) {
        if (logLines == null) {
            logLines = new ArrayList<>();
        }
        logLines.add(line);
    }
}
