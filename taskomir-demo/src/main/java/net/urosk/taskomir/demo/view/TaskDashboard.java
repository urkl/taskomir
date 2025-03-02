package net.urosk.taskomir.demo.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import net.urosk.taskomir.core.config.MessageConfig;
import net.urosk.taskomir.core.service.TaskManagerService;
import net.urosk.taskomir.core.ui.AllTasksComponent;
import org.springframework.context.MessageSource;

@Route("dashboard")
public class TaskDashboard extends VerticalLayout {

    public TaskDashboard(TaskManagerService taskManagerService, MessageSource messageSource) {
        AllTasksComponent allTasksComponent = new AllTasksComponent(taskManagerService, messageSource);
        add(allTasksComponent);
    }
}
