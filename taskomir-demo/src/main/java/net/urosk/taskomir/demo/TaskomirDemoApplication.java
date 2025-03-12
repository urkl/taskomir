package net.urosk.taskomir.demo;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan({"net.urosk.taskomir.*"})
@EnableMongoRepositories(basePackages = "net.urosk.taskomir.core.repository")
@Theme("taskomir")
@Push
public class TaskomirDemoApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(TaskomirDemoApplication.class, args);
    }

}
