package net.urosk.taskomir.core.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.dom.ThemeList;
import net.urosk.taskomir.core.config.TaskomirProperties;
import net.urosk.taskomir.core.domain.AppLock;
import net.urosk.taskomir.core.domain.TaskInfo;
import net.urosk.taskomir.core.lib.ConfigEntry;
import net.urosk.taskomir.core.lib.TaskStatus;
import net.urosk.taskomir.core.sampleTask.SampleScheduledTask;
import net.urosk.taskomir.core.sampleTask.SampleSpringScheduledTask;
import net.urosk.taskomir.core.service.TaskomirService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.vaadin.lineawesome.LineAwesomeIcon;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.vaadin.flow.component.button.ButtonVariant.*;

public class TaskDashboard extends VerticalLayout {

    private static final DateTimeFormatter SL_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", new Locale("sl", "SI"));
    private final TaskomirService taskomirService;
    private final MessageSource messageSource;

    // Gridi za posamezne statuse
    private final Grid<TaskInfo> enqueuedGrid = new Grid<>(TaskInfo.class, false);
    private final Grid<TaskInfo> scheduledGrid = new Grid<>(TaskInfo.class, false);
    private final Grid<TaskInfo> processingGrid = new Grid<>(TaskInfo.class, false);
    private final Grid<TaskInfo> succeededGrid = new Grid<>(TaskInfo.class, false);
    private final Grid<TaskInfo> failedGrid = new Grid<>(TaskInfo.class, false);
    private final Grid<TaskInfo> deletedGrid = new Grid<>(TaskInfo.class, false);

    public TaskDashboard(TaskomirService taskomirService, MessageSource messageSource, boolean showSampleTasks) {
        this.taskomirService = taskomirService;
        this.messageSource = messageSource;

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setSpacing(true);
        if (showSampleTasks) {
            // Pridobi sporočila iz MessageSource
            String addTaskText = messageSource.getMessage("ui.addTask", null, LocaleContextHolder.getLocale());
            String addErrorTaskText = messageSource.getMessage("ui.addErrorTask", null, LocaleContextHolder.getLocale());
            String addScheduledTaskText = messageSource.getMessage("ui.addScheduledTask", null, LocaleContextHolder.getLocale());

            Button addTaskButton = new Button(addTaskText, event -> addNewTask());
            Button addErrorTaskButton = new Button(addErrorTaskText, event -> addErrorTask());

            CronField cronField = new CronField();

            Button addScheduledTaskButton = new Button(addScheduledTaskText, event -> addScheduledTask(cronField.getValue()));
            Button addScheduledTaskButton2 = new Button(addScheduledTaskText, event -> addScheduledTaskSpring(cronField.getValue()));

            buttons.add(addTaskButton, addErrorTaskButton, cronField, addScheduledTaskButton, addScheduledTaskButton2);
            add(buttons);
        }
        // Dodaj ločilno črto
        add(new Hr());

        add(createConfigPanel(taskomirService.getTaskomirProperties()));
        add(new Hr());
        // SCHEDULED tasks
        H3 scheduledCounter = new H3("0");
        add(getHeader(LineAwesomeIcon.CALENDAR_ALT, messageSource.getMessage("ui.scheduledHeader", null, LocaleContextHolder.getLocale()), "var(--lumo-primary-color)", scheduledCounter));
        configureDefaultColumns(scheduledGrid, false, true, false, true);
        scheduledGrid.setDataProvider(createDataProvider(TaskStatus.SCHEDULED, count -> scheduledCounter.setText(String.valueOf(count))));
        add(scheduledGrid);

        // ENQUEUED tasks
        H3 enqueuedCounter = new H3("0");
        add(getHeader(LineAwesomeIcon.TASKS_SOLID, messageSource.getMessage("ui.enqueuedHeader", null, LocaleContextHolder.getLocale()), "var(--lumo-primary-color)", enqueuedCounter, new Button(messageSource.getMessage("ui.cleanEnqueued", null, LocaleContextHolder.getLocale()), event -> taskomirService.deleteTasksByStatus(TaskStatus.ENQUEUED))));
        configureDefaultColumns(enqueuedGrid, false, true, false, false);
        enqueuedGrid.setDataProvider(createDataProvider(TaskStatus.ENQUEUED, count -> enqueuedCounter.setText(String.valueOf(count))));
        add(enqueuedGrid);

        // PROCESSING tasks
        H3 processingCounter = new H3("0");
        add(getHeader(LineAwesomeIcon.RUNNING_SOLID, messageSource.getMessage("ui.processingHeader", null, LocaleContextHolder.getLocale()), "var(--lumo-primary-color)", processingCounter));
        configureDefaultColumns(processingGrid, true, true, false, false);
        processingGrid.setDataProvider(createDataProvider(TaskStatus.PROCESSING, count -> processingCounter.setText(String.valueOf(count))));
        add(processingGrid);

        // SUCCEEDED tasks
        H3 succeededCounter = new H3("0");
        add(getHeader(LineAwesomeIcon.CHECK_CIRCLE, messageSource.getMessage("ui.succeededHeader", null, LocaleContextHolder.getLocale()), "var(--lumo-success-color)", succeededCounter, new Button(messageSource.getMessage("ui.cleanSucceeded", null, LocaleContextHolder.getLocale()), event -> taskomirService.deleteTasksByStatus(TaskStatus.SUCCEEDED))));
        configureDefaultColumns(succeededGrid, false, false, false, false);
        succeededGrid.setDataProvider(createDataProvider(TaskStatus.SUCCEEDED, count -> succeededCounter.setText(String.valueOf(count))));
        add(succeededGrid);

        // FAILED tasks
        H3 failedCounter = new H3("0");
        add(getHeader(LineAwesomeIcon.TIMES_CIRCLE, messageSource.getMessage("ui.failedHeader", null, LocaleContextHolder.getLocale()), "var(--lumo-error-color)", failedCounter), new Button(messageSource.getMessage("ui.cleanFailed", null, LocaleContextHolder.getLocale()), event -> taskomirService.deleteTasksByStatus(TaskStatus.FAILED)));
        configureDefaultColumns(failedGrid, false, false, true, false);
        failedGrid.setDataProvider(createDataProvider(TaskStatus.FAILED, count -> failedCounter.setText(String.valueOf(count))));
        add(failedGrid);

        // DELETED tasks
        H3 deletedCounter = new H3("0");
        add(getHeader(LineAwesomeIcon.TRASH_ALT, messageSource.getMessage("ui.deletedHeader", null, LocaleContextHolder.getLocale()), "var(--lumo-error-color)"), deletedCounter, new Button(messageSource.getMessage("ui.cleanDeleted", null, LocaleContextHolder.getLocale()), event -> taskomirService.deleteTasksByStatus(TaskStatus.DELETED)));
        configureDefaultColumns(deletedGrid, false, false, false, false);
        deletedGrid.setDataProvider(createDataProvider(TaskStatus.DELETED, count -> deletedCounter.setText(String.valueOf(count))));
        add(deletedGrid);

        // Nastavi polling – osvežujemo podatke vsako 300ms
        UI.getCurrent().getUI().ifPresent(ui -> {
            ui.setPollInterval(1500);
            ui.addPollListener(e -> refreshAll());
        });
    }

    private void addScheduledTaskSpring(String cronExpression) {
        taskomirService.createScheduledTask("Scheduled task", new SampleSpringScheduledTask(), cronExpression, false);
    }

    public void refreshAll() {
        enqueuedGrid.getDataProvider().refreshAll();
        scheduledGrid.getDataProvider().refreshAll();
        processingGrid.getDataProvider().refreshAll();
        succeededGrid.getDataProvider().refreshAll();
        failedGrid.getDataProvider().refreshAll();
        deletedGrid.getDataProvider().refreshAll();
    }

    public HorizontalLayout getHeader(LineAwesomeIcon icon, String text, String color, Component... components) {
        var svg = icon.create();
        svg.setSize("40px");
        svg.setColor(color);
        var hl = new HorizontalLayout(svg, new H3(text));
        if (components.length > 0) {
            hl.add(components);
        }
        hl.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
        hl.setVerticalComponentAlignment(Alignment.CENTER, svg);
        return hl;
    }

    private void configureDefaultColumns(Grid<TaskInfo> grid, boolean showProgressBar, boolean addKillButton, boolean addError, boolean addExecuteNowButton) {

        grid.addThemeVariants(
                GridVariant.LUMO_WRAP_CELL_CONTENT);

        grid.addColumn(TaskInfo::getId).setHeader(messageSource.getMessage("grid.column.id", null, LocaleContextHolder.getLocale())).setResizable(true);

        grid.addColumn(TaskInfo::getName).setHeader(messageSource.getMessage("grid.column.name", null, LocaleContextHolder.getLocale())).setResizable(true);

        grid.addColumn(task -> {
            if (task.getClassName() == null) return "";
            return task.getClassName().substring(task.getClassName().lastIndexOf('.') + 1);
        }).setHeader(messageSource.getMessage("grid.column.type", null, LocaleContextHolder.getLocale())).setResizable(true);

        grid.addColumn(task -> task.getProgress() * 100 + "%").setHeader(messageSource.getMessage("grid.column.progress", null, LocaleContextHolder.getLocale())).setResizable(true);

        if (showProgressBar) {

            grid.addComponentColumn(task -> {
                ProgressBar progressBar = new ProgressBar(0, 1, task.getProgress());
                progressBar.setWidth("150px");
                return progressBar;
            }).setHeader(messageSource.getMessage("grid.column.progress", null, LocaleContextHolder.getLocale()));

            grid.addColumn(TaskInfo::getCurrentProgress).setHeader(messageSource.getMessage("grid.column.progressText", null, LocaleContextHolder.getLocale())).setResizable(true);
        }


        grid.addColumn(task -> task.getStatus().toString()).setHeader(messageSource.getMessage("grid.column.status", null, LocaleContextHolder.getLocale())).setResizable(true);

        grid.addColumn(task -> LocalDateTime.ofInstant(Instant.ofEpochMilli(task.getCreatedAt()), ZoneId.systemDefault()).format(SL_FORMATTER)).setHeader(messageSource.getMessage("grid.column.created", null, LocaleContextHolder.getLocale())).setResizable(true);


        grid.addColumn(task -> task.getStartedAt() != null ? LocalDateTime.ofInstant(Instant.ofEpochMilli(task.getStartedAt()), ZoneId.systemDefault()).format(SL_FORMATTER) : "").setHeader(messageSource.getMessage("grid.column.started", null, LocaleContextHolder.getLocale()));

        grid.addColumn(task -> task.getEndedAt() != null ? LocalDateTime.ofInstant(Instant.ofEpochMilli(task.getEndedAt()), ZoneId.systemDefault()).format(SL_FORMATTER) : "").setHeader(messageSource.getMessage("grid.column.ended", null, LocaleContextHolder.getLocale()));

        grid.addColumn(task -> task.getLastRunTime() != null ? LocalDateTime.ofInstant(Instant.ofEpochMilli(task.getLastRunTime()), ZoneId.systemDefault()).format(SL_FORMATTER) : "").setHeader(messageSource.getMessage("grid.column.lastRun", null, LocaleContextHolder.getLocale()));
        grid.addColumn(TaskInfo::getCronExpression).setHeader(messageSource.getMessage("grid.column.cron", null, LocaleContextHolder.getLocale()));

        grid.addColumn(TaskInfo::getParentId).setHeader(messageSource.getMessage("grid.column.parent", null, LocaleContextHolder.getLocale()));

        if (addError)
            grid.addColumn(task -> task.getError() != null ? task.getError() : "").setHeader(messageSource.getMessage("grid.column.error", null, LocaleContextHolder.getLocale()));

        if (addKillButton) {
            grid.addComponentColumn(task -> {
                Button killButton = new Button(LineAwesomeIcon.SKULL_CROSSBONES_SOLID.create());
                killButton.addThemeVariants(LUMO_ERROR, LUMO_PRIMARY, LUMO_ICON);
                killButton.addClickListener(e -> {
                    UI.getCurrent().access(() -> {
                        boolean canceled = taskomirService.cancelTask(task.getId());
                        refreshAll();
                    });
                });
                return killButton;
            }).setHeader(messageSource.getMessage("grid.column.kill", null, LocaleContextHolder.getLocale())).setAutoWidth(true).setFlexGrow(0);
        }

        if (addExecuteNowButton) {
            grid.addComponentColumn(task -> {
                Button executeNowButton = new Button(LineAwesomeIcon.PLAY_CIRCLE.create());
                executeNowButton.addThemeVariants(LUMO_SUCCESS, LUMO_ICON, LUMO_PRIMARY);
                executeNowButton.addClickListener(e -> {
                    UI.getCurrent().access(() -> {
                        taskomirService.enqueueNewChildOf(task);
                        refreshAll();
                    });
                });
                return executeNowButton;
            }).setHeader(messageSource.getMessage("grid.column.execute", null, LocaleContextHolder.getLocale())).setAutoWidth(true).setFlexGrow(0);
        }

        grid.addComponentColumn(task -> {
            Button showLogButton = new Button(LineAwesomeIcon.FILE_ALT_SOLID.create());
            showLogButton.addClickListener(event -> {

                Dialog dialog = new Dialog();
                dialog.setWidth("400px");
                H3 title = new H3("Log for Task: " + task.getName());
                UnorderedList ul = new UnorderedList();
                if (task.getLogLines() != null) {
                    for (String line : task.getLogLines()) {
                        ul.add(new ListItem(line));
                    }
                }

                Button close = new Button("Close", e -> dialog.close());

                dialog.add(title, ul, close);
                dialog.open();
            });
            return showLogButton;
        }).setHeader("Log");
    }


    private DataProvider<TaskInfo, Void> createDataProvider(TaskStatus status, Consumer<Integer> countUpdater) {
        return DataProvider.fromCallbacks((Query<TaskInfo, Void> query) -> {
            try {
                int page = query.getOffset() / query.getLimit();
                Pageable pageable = PageRequest.of(page, query.getLimit());
                Page<TaskInfo> result = taskomirService.getTasksByStatus(status, pageable);
                return result.getContent().stream();
            } catch (IndexOutOfBoundsException e) {
                getUI().ifPresent(ui -> ui.access(this::refreshAll));
                return Stream.empty();
            }
        }, (Query<TaskInfo, Void> query) -> {
            Pageable pageable = PageRequest.of(0, 1);
            int count = (int) taskomirService.getTasksByStatus(status, pageable).getTotalElements();
            countUpdater.accept(count);
            return count;
        });
    }

    private void addScheduledTask(String cronExpression) {
        taskomirService.createScheduledTask("Scheduled task", new SampleScheduledTask(), cronExpression, true);
    }

    private void addNewTask() {
        taskomirService.enqueue(messageSource.getMessage("ui.simpleTaskName", null, LocaleContextHolder.getLocale()), progress -> {
            for (int i = 0; i <= 100; i++) {
                progress.update(i / 100.0, "");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void addErrorTask() {
        taskomirService.enqueue(messageSource.getMessage("ui.errorTaskName", null, LocaleContextHolder.getLocale()), progress -> {
            for (int i = 0; i <= 100; i++) {
                progress.update(i / 100.0, "");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (i == ThreadLocalRandom.current().nextInt(1, 101)) {
                    throw new RuntimeException("Napaka pri radnom");
                }
            }
        });
    }

    private Tab createTabWithBadge(String labelText, int badgeCount) {
        Span label = new Span(labelText);
        Span badge = new Span(String.valueOf(badgeCount));
        ThemeList badgeTheme = badge.getElement().getThemeList();
        badgeTheme.add("badge");

        badge.getStyle().set("background-color", "#d9534f");
        badge.getStyle().set("border-radius", "10px");
        badge.getStyle().set("padding", "2px 6px");
        badge.getStyle().set("color", "#fff");
        badge.getStyle().set("font-size", "0.8em");

        HorizontalLayout layout = new HorizontalLayout(label, badge);
        layout.setAlignItems(Alignment.CENTER);
        layout.setSpacing(true);

        return new Tab(layout);
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (days > 0) return days + " d";
        if (hours > 0) return hours + " h";
        if (minutes > 0) return minutes + " min";
        return seconds + " s";
    }

    private Component createConfigPanel(TaskomirProperties properties) {
        VerticalLayout configLayout = new VerticalLayout();
        configLayout.setPadding(true);
        configLayout.setSpacing(true);

        // Ugotovimo, ali je instanca primary ali ne
        boolean isPrimary = properties.isPrimary();

        Optional<AppLock> primary=taskomirService.getExistingPrimary();
        if(primary.isEmpty()){
            H2 missing = new H2(messageSource.getMessage("ui.config.primaryMissing", null, LocaleContextHolder.getLocale()));
            missing.getStyle().set("color", "var(--lumo-error-color)");
            add(missing);
        }
        // Pripravimo lokalizirano sporočilo; npr. "Configuration (PRIMARY)" ali "Configuration (SECONDARY)"
        String roleSuffix = isPrimary
                ? messageSource.getMessage("ui.config.primarySuffix", null, LocaleContextHolder.getLocale())
                : messageSource.getMessage("ui.config.secondarySuffix", null, LocaleContextHolder.getLocale());

        // Sestavimo celoten naslov
        String headerText = messageSource.getMessage("ui.config.header", null, LocaleContextHolder.getLocale())
        + " / "+messageSource.getMessage("ui.config.thisInstance", null, LocaleContextHolder.getLocale())
                + " :: " + roleSuffix;

        // Dodaj ikono in barvo v header
        var configHeader = getHeader(LineAwesomeIcon.COG_SOLID,
                messageSource.getMessage(headerText, null, LocaleContextHolder.getLocale()),
                "var(--lumo-primary-color)");



        Grid<ConfigEntry> configGrid = new Grid<>(ConfigEntry.class, false);
        // Najprej poskusimo dobiti PRIMARY lock iz baze.
        Optional<AppLock> lockOpt = taskomirService.getAppLockByName("PRIMARY");
        if (lockOpt.isPresent()) {
            // Če PRIMARY obstaja, uporabljamo lock-ove nastavitve
            AppLock lock = lockOpt.get();

            configGrid.setItems(
                    new ConfigEntry(
                            messageSource.getMessage("ui.config.instanceId", null, LocaleContextHolder.getLocale()),
                            lock.getInstanceId()
                    ),
                    new ConfigEntry(
                            messageSource.getMessage("ui.config.cleanupInterval", null, LocaleContextHolder.getLocale()),
                            formatMillisAsDuration(lock.getCleanupIntervalMs())
                    ),
                    new ConfigEntry(
                            messageSource.getMessage("ui.config.succeededRetentionTime", null, LocaleContextHolder.getLocale()),
                            formatMillisAsDuration(lock.getSucceededRetentionMs())
                    ),
                    new ConfigEntry(
                            messageSource.getMessage("ui.config.deletedRetentionTime", null, LocaleContextHolder.getLocale()),
                            formatMillisAsDuration(lock.getDeletedRetentionMs())
                    ),
                    new ConfigEntry(
                            messageSource.getMessage("ui.config.poolSize", null, LocaleContextHolder.getLocale()),
                            String.valueOf(lock.getPoolSize())
                    ),
                    new ConfigEntry(
                            messageSource.getMessage("ui.config.queueCapacity", null, LocaleContextHolder.getLocale()),
                            String.valueOf(lock.getQueueCapacity())
                    )

            );

        }

        configGrid.addColumn(ConfigEntry::getKey).setHeader(messageSource.getMessage("ui.config.parameter", null, LocaleContextHolder.getLocale()));
        configGrid.addColumn(ConfigEntry::getValue).setHeader(messageSource.getMessage("ui.config.value", null, LocaleContextHolder.getLocale()));

        configLayout.add(configHeader, configGrid);
        return configLayout;
    }

    private String formatMillisAsDuration(long ms) {
        Duration d = Duration.ofMillis(ms);
        return formatDuration(d);
    }

}
