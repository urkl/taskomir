package net.urosk.taskomir.core.sampleTask;


import net.urosk.taskomir.core.lib.AbstractScheduledTask;
import net.urosk.taskomir.core.lib.ProgressUpdater;
import org.springframework.stereotype.Component;

@Component
public class SampleSpringScheduledTask extends AbstractScheduledTask {
    @Override
    protected void runScheduledLogic(ProgressUpdater updater) throws Exception {

        // Začetek zanke
        for (int i = 0; i <= 100; i++) {

            // Preveri, če je thread prekinjen
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task canceled");
            }

            double progress = i / 100.0;
            updater.update(progress,"");

            try {
                Thread.sleep(200); // simulacija dela
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}
