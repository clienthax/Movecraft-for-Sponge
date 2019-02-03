package io.github.pulverizer.movecraft.mapUpdater;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.mapUpdater.update.CraftRotateCommand;
import io.github.pulverizer.movecraft.mapUpdater.update.CraftTranslateCommand;
import io.github.pulverizer.movecraft.mapUpdater.update.UpdateCommand;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MapUpdateManager implements Runnable {
    private static MapUpdateManager ourInstance;

    private final Queue<UpdateCommand> updates = new ConcurrentLinkedQueue<>();

    private MapUpdateManager() { }

    public static MapUpdateManager getInstance() {
        return ourInstance;
    }
    public static void initialize(){
        ourInstance = new MapUpdateManager();
    }

    public void run() {
        if (updates.isEmpty()) return;
        long startTime = System.currentTimeMillis();
        // and set all crafts that were updated to not processing



        synchronized (updates) {
            UpdateCommand next = updates.poll();
            while(next != null) {
                next.doUpdate();
                next = updates.poll();
            }
        }

        if (Settings.Debug) {
            long endTime = System.currentTimeMillis();
            Sponge.getServer().getBroadcastChannel().send(Text.of("Map update took (ms): " + (endTime - startTime)));
        }
    }


    public void scheduleUpdate(UpdateCommand update){
        updates.add(update);
    }

    public void scheduleUpdates(UpdateCommand... updates){
        Collections.addAll(this.updates, updates);
    }

    public void scheduleUpdates(Collection<UpdateCommand> updates){
        this.updates.addAll(updates);
    }

}