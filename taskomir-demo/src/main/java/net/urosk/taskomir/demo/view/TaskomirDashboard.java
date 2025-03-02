package net.urosk.taskomir.demo.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import net.urosk.taskomir.core.service.TaskManagerService;
import net.urosk.taskomir.core.ui.TaskDashboard;
import org.springframework.context.MessageSource;

@Route("dashboard")
public class TaskomirDashboard extends VerticalLayout {

    public TaskomirDashboard(TaskManagerService taskManagerService, MessageSource messageSource) {
        TaskDashboard allTasksComponent = new TaskDashboard(taskManagerService, messageSource);
        add(allTasksComponent);
    }
}
