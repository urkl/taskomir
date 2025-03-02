package net.urosk.taskomir.core.lib;



@FunctionalInterface
public interface ProgressTask {
    void execute(ProgressUpdater progress) throws Exception;
}