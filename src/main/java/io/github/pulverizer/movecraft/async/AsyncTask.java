package io.github.pulverizer.movecraft.async;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.spongepowered.api.scheduler.Task;

public abstract class AsyncTask {
    protected final Craft craft;

    protected AsyncTask(Craft c) {
        craft = c;
    }

    public void run(Object plugin, boolean isAsync) {

        if (isAsync) {
            Task.builder().async().execute(() -> task()).submit(plugin);
        } else {
            Task.builder().execute(() -> task()).submit(plugin);
        }


    }

    private void task() {
        try {
            excecute();
            Movecraft.getInstance().getAsyncManager().submitCompletedTask(this);
        } catch (Exception e) {
            Movecraft.getInstance().getLogger().error(I18nSupport.getInternationalisedString("Internal - Error - Proccessor thread encountered an error"));
            e.printStackTrace();
        }
    }

    protected abstract void excecute();

    protected Craft getCraft() {
        return craft;
    }
}