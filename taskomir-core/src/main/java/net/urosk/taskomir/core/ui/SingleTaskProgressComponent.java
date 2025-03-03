package net.urosk.taskomir.core.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import net.urosk.taskomir.core.service.TaskomirService;


public class SingleTaskProgressComponent extends VerticalLayout {

    private final TaskomirService taskomirService;
    private final ProgressBar progressBar;
    private final Span progressLabel;

    public SingleTaskProgressComponent(TaskomirService taskomirService) {

        this.taskomirService = taskomirService;

        // Inicializacija UI elementov
        progressBar = createProgressBar();
        progressLabel = createProgressLabel();

        add(progressLabel, progressBar);

        // Zagon naloge
        //     startTask();
    }

    private ProgressBar createProgressBar() {
        ProgressBar bar = new ProgressBar(0, 1);
        bar.setWidthFull();
        bar.setHeight("40px");
        bar.getStyle().set("border-radius", "10px");
        bar.getStyle().set("box-shadow", "0 4px 6px rgba(0, 0, 0, 0.2)");
        return bar;
    }

    private Span createProgressLabel() {
        Span label = new Span("Progress: 0%");
        label.getStyle().set("font-size", "1.5em");
        label.getStyle().set("color", "#007BFF");
        label.getStyle().set("font-weight", "bold");
        label.getStyle().set("margin-bottom", "10px");
        return label;
    }


}
