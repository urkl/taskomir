package net.urosk.taskomir.core.lib;


import lombok.Setter;
import net.urosk.taskomir.core.domain.TaskInfo;

/**
 * Abstract class for all scheduled tasks.
 *
 */
@Setter
public abstract class AbstractScheduledTask implements ProgressTask {
    protected TaskInfo taskInfo;

    @Override
    public final void execute(ProgressUpdater progressUpdater) throws Exception {
        // Tukaj lahko dodate kak skupen logging, varnostne preverbe ipd.
        runScheduledLogic(progressUpdater);
    }

    /**
     * Metoda, ki jo konkretni scheduled task implementira.
     */
    protected abstract void runScheduledLogic(ProgressUpdater updater) throws Exception;
}
