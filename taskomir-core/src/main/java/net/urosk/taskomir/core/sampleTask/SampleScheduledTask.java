package net.urosk.taskomir.core.sampleTask;


import net.urosk.taskomir.core.lib.AbstractScheduledTask;
import net.urosk.taskomir.core.lib.ProgressUpdater;

public class SampleScheduledTask extends AbstractScheduledTask {
    @Override
    protected void runScheduledLogic(ProgressUpdater updater) throws Exception {

        for (int i = 0; i <= 100; i++) {


            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task canceled");
            }

            double progress = i / 100.0;
            updater.update(progress,"Progress: " + i + "%");

            try {
                Thread.sleep(200); // simulacija dela
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}
