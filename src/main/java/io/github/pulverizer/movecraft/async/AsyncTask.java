package io.github.pulverizer.movecraft.async;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.scheduler.Task;

public abstract class AsyncTask {
    protected final Craft craft;
    protected final String type;

    public AsyncTask(Craft c, String taskType) {
        craft = c;
        type = taskType;
    }

    public void run() {

        String taskName = "Movecraft - " + type + " Task - " + craft.getId();

        Task.builder()
                .async()
                .name(taskName)
                .execute(this::task)
                .submit(Movecraft.getInstance());

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info(taskName);

    }

    private void task() {
        try {
            long startTime = System.currentTimeMillis();

            execute();

            long endTime = System.currentTimeMillis();
            if (Settings.Debug) {
                Movecraft.getInstance().getLogger().info(type + " Task Took: " + (endTime - startTime) + "ms");
            }

            AsyncManager.getInstance().submitCompletedTask(this);
        } catch (Exception e) {
            Movecraft.getInstance().getLogger().error("Internal Error - Processor thread encountered an error!");
            e.printStackTrace();
        }
    }

    protected abstract void execute() throws InterruptedException;

    protected abstract void postProcess();

    protected Craft getCraft() {
        return craft;
    }
}