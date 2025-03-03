package net.urosk.taskomir.demo.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import net.urosk.taskomir.core.service.TaskomirService;
import net.urosk.taskomir.core.ui.TaskDashboard;
import org.springframework.context.MessageSource;

@Route("dashboard")
public class TaskomirDashboard extends VerticalLayout {

    public TaskomirDashboard(TaskomirService taskomirService, MessageSource messageSource) {
        TaskDashboard allTasksComponent = new TaskDashboard(taskomirService, messageSource);
        add(allTasksComponent);
    }
}
