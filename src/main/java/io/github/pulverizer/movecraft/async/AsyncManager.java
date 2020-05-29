package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.Sets;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.enums.DirectControlMode;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.entity.PlayerInventory;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AsyncManager implements Runnable {
    private static AsyncManager ourInstance;
    private final HashMap<Craft, HashMap<Craft, Long>> recentContactTracking = new HashMap<>();
    private final BlockingQueue<AsyncTask> taskQueue = new LinkedBlockingQueue<>();
    private final HashSet<Craft> clearanceSet = new HashSet<>();
    private long lastFadeCheck = 0;
    private long lastContactCheck = 0;

    public static AsyncManager getInstance() {
        if (ourInstance == null) initialize();

        return ourInstance;
    }
    private static void initialize() {
        ourInstance = new AsyncManager();
    }

    public void submitCompletedTask(AsyncTask task) {
        taskQueue.add(task);
    }

    private void processAlgorithmQueue() {
        int runLength = 10;
        int queueLength = taskQueue.size();

        runLength = Math.min(runLength, queueLength);

        for (int i = 0; i < runLength; i++) {
            AsyncTask poll = taskQueue.poll();

            if (poll != null) {
                poll.postProcess();
            }
        }
    }

    private void processCruise() {
        for (Craft craft : CraftManager.getInstance()) {
            if (craft == null || craft.isProcessing() || !craft.isCruising()) {
                continue;
            }

            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - craft.getLastMoveTick();
            World world = craft.getWorld();
            // if the craft should go slower underwater, make time pass more slowly there
            //TODO: Replace world.getSeaLevel() with something better
            if (craft.getType().getHalfSpeedUnderwater() && craft.getHitBox().getMinY() < world.getSeaLevel())
                ticksElapsed >>= 1;

            if (ticksElapsed < craft.getTickCooldown()) {
                continue;
            }

            if (craft.getType().getCruiseOnPilot() && craft.getType().getCruiseOnPilotMaxMoves() > 0 && craft.getType().getCruiseOnPilotMaxMoves() <= craft.getNumberOfMoves()) {
                craft.sink();
            }

            // check direct controls to modify movement
            boolean bankLeft = false;
            boolean bankRight = false;
            boolean dive = false;
            if (craft.getDirectControlMode().equals(DirectControlMode.B)) {

                Player pilot = Sponge.getServer().getPlayer(craft.getPilot()).get();
                if (pilot.get(Keys.IS_SNEAKING).get())
                    dive = true;
                if (((PlayerInventory) pilot.getInventory()).getHotbar().getSelectedSlotIndex() == 3)
                    bankLeft = true;
                if (((PlayerInventory) pilot.getInventory()).getHotbar().getSelectedSlotIndex() == 5)
                    bankRight = true;
            }

            int dx = 0;
            int dz = 0;
            int dy = 0;

            // ascend
            if (craft.getVerticalCruiseDirection() == Direction.UP) {
                if (craft.getHorizontalCruiseDirection() != Direction.NONE) {
                    dy = (1 + craft.getType().getVertCruiseSkipBlocks()) >> 1;
                } else {
                    dy = 1 + craft.getType().getVertCruiseSkipBlocks();
                }
            }
            // descend
            if (craft.getVerticalCruiseDirection() == Direction.DOWN) {
                if (craft.getHorizontalCruiseDirection() != Direction.NONE) {
                    dy = (-1 - craft.getType().getVertCruiseSkipBlocks()) >> 1;
                } else {
                    dy = -1 - craft.getType().getVertCruiseSkipBlocks();
                }
            } else if (dive) {
                dy = -((craft.getType().getCruiseSkipBlocks() + 1) >> 1);
                if (craft.getHitBox().getMinY() <= world.getSeaLevel()) {
                    dy = -1;
                }
            }

            switch (craft.getHorizontalCruiseDirection()) {
                case WEST:
                    dx = 1 + craft.getType().getCruiseSkipBlocks();
                    if (bankRight) {
                        dz = (-1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    if (bankLeft) {
                        dz = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    break;

                case EAST:
                    dx = -1 - craft.getType().getCruiseSkipBlocks();
                    if (bankRight) {
                        dz = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    if (bankLeft) {
                        dz = (-1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    break;

                case NORTH:
                    dz = 1 + craft.getType().getCruiseSkipBlocks();
                    if (bankRight) {
                        dx = (-1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    if (bankLeft) {
                        dx = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    break;

                case SOUTH:
                    dz = -1 - craft.getType().getCruiseSkipBlocks();
                    if (bankRight) {
                        dx = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    if (bankLeft) {
                        dx = (-1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                    }

                    break;
            }

            if (craft.getType().getCruiseOnPilot()) {
                dy = craft.getType().getCruiseOnPilotVertMove();
            }

            craft.translate(new Vector3i(dx, dy, dz));
        }
    }

    private void detectSinking(){
        HashSet<Craft> crafts = Sets.newHashSet(CraftManager.getInstance());
        crafts.forEach(craft -> {

            if (Sponge.getServer().getRunningTimeTicks() - craft.getLastCheckTick() <= Settings.SinkCheckTicks) {
                return;
            }

            craft.runChecks();
        });
    }

    //Controls sinking crafts
    private void processSinking() {
        //copy the crafts before iteration to prevent concurrent modifications
        HashSet<Craft> crafts = Sets.newHashSet(CraftManager.getInstance());
        crafts.forEach(craft -> {
            if (craft == null || !craft.isSinking()) {
                return;
            }
            if (craft.getHitBox().isEmpty() || craft.getHitBox().getMinY() < 5) {
                craft.release(null);
                return;
            }
            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - craft.getLastMoveTick();
            if (Math.abs(ticksElapsed) < craft.getType().getSinkRateTicks()) {
                return;
            }
            int dx = 0;
            int dz = 0;
            if (craft.getType().getKeepMovingOnSink()) {
                dx = craft.getLastMoveVector().getX();
                dz = craft.getLastMoveVector().getZ();
            }
            craft.translate(new Vector3i(dx, -1, dz));
        });
    }

    private void processDetection() {
        long ticksElapsed = (System.currentTimeMillis() - lastContactCheck) / 50;
        if (ticksElapsed > 21) {
            for (World world : Sponge.getServer().getWorlds()) {
                if (world != null) {
                    for (Craft ccraft : CraftManager.getInstance().getCraftsInWorld(world)) {
                        if (!ccraft.crewIsEmpty()) {
                            if (!recentContactTracking.containsKey(ccraft)) {
                                recentContactTracking.put(ccraft, new HashMap<>());
                            }
                            for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(world)) {
                                long cposx = ccraft.getHitBox().getMaxX() + ccraft.getHitBox().getMinX();
                                long cposy = ccraft.getHitBox().getMaxY() + ccraft.getHitBox().getMinY();
                                long cposz = ccraft.getHitBox().getMaxZ() + ccraft.getHitBox().getMinZ();
                                cposx = cposx >> 1;
                                cposy = cposy >> 1;
                                cposz = cposz >> 1;
                                long tposx = tcraft.getHitBox().getMaxX() + tcraft.getHitBox().getMinX();
                                long tposy = tcraft.getHitBox().getMaxY() + tcraft.getHitBox().getMinY();
                                long tposz = tcraft.getHitBox().getMaxZ() + tcraft.getHitBox().getMinZ();
                                tposx = tposx >> 1;
                                tposy = tposy >> 1;
                                tposz = tposz >> 1;
                                long diffx = cposx - tposx;
                                long diffy = cposy - tposy;
                                long diffz = cposz - tposz;
                                long distsquared = Math.abs(diffx) * Math.abs(diffx);
                                distsquared += Math.abs(diffy) * Math.abs(diffy);
                                distsquared += Math.abs(diffz) * Math.abs(diffz);
                                long detectionRange;
                                if (tposy > 65) {
                                    detectionRange = (long) (Math.sqrt(tcraft.getInitialSize())
                                            * tcraft.getType().getDetectionMultiplier());
                                } else {
                                    detectionRange = (long) (Math.sqrt(tcraft.getInitialSize())
                                            * tcraft.getType().getUnderwaterDetectionMultiplier());
                                }
                                if (distsquared < detectionRange * detectionRange
                                        && tcraft.getPilot() != ccraft.getPilot()) {
                                    // craft has been detected

                                    // has the craft not been seen in the last
                                    // minute, or is completely new?
                                    if (recentContactTracking.get(ccraft).get(tcraft) == null
                                            || System.currentTimeMillis()
                                            - recentContactTracking.get(ccraft).get(tcraft) > 60000) {
                                        String notification = "New contact: ";
                                        notification += tcraft.getType().getName();
                                        notification += " commanded by ";
                                        if (tcraft.getPilot() != null) {
                                            notification += Sponge.getServer().getPlayer(tcraft.getPilot()).get().getDisplayNameData().displayName().toString();
                                        } else {
                                            notification += "NULL";
                                        }
                                        notification += ", size: ";
                                        notification += tcraft.getInitialSize();
                                        notification += ", range: ";
                                        notification += (int) Math.sqrt(distsquared);
                                        notification += " to the";
                                        if (Math.abs(diffx) > Math.abs(diffz))
                                            if (diffx < 0)
                                                notification += " east.";
                                            else
                                                notification += " west.";
                                        else if (diffz < 0)
                                            notification += " south.";
                                        else
                                            notification += " north.";

                                        final String finalisedNotification = notification;

                                        Sponge.getServer().getPlayer(ccraft.getPilot()).ifPresent(player -> {
                                            player.sendMessage(Text.of(finalisedNotification));
                                            world.playSound(SoundTypes.BLOCK_ANVIL_LAND, player.getLocation().getPosition(), 1.0f, 2.0f);
                                        });
                                    }

                                    long timestamp = System.currentTimeMillis();
                                    recentContactTracking.get(ccraft).put(tcraft, timestamp);
                                }
                            }
                        }
                    }
                }
            }

            lastContactCheck = System.currentTimeMillis();
        }
    }

    public void run() {

        //processSubcraftQueuedTasks();
        processCruise();
        detectSinking();
        processSinking();
        processDetection();
        processAlgorithmQueue();

        // Cleanup crafts that are bugged and have not moved in the past 60 seconds, but have no crew or are still processing.
        for (Craft craft : CraftManager.getInstance()) {

            if (craft.crewIsEmpty() && craft.getLastMoveTick() < Sponge.getServer().getRunningTimeTicks() - 1200) {
                CraftManager.getInstance().forceRemoveCraft(craft);
            }

            // Stop crafts from moving if they have taken too long to process.
            if (craft.isProcessing() && craft.isCruising() && craft.getProcessingStartTime() < Sponge.getServer().getRunningTimeTicks() - 1200) {
                craft.setProcessing(false);
            }
        }
    }
}