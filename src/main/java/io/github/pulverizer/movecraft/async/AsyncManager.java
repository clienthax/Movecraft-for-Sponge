package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.Lists;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.enums.Rotation;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        return ourInstance;
    }
    public static void initialize(){
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
            if (craft == null || !craft.isNotProcessing() || craft.getState() != CraftState.CRUISING) {
                continue;
            }

            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - craft.getLastMoveTick();
            World world = craft.getWorld();
            // if the craft should go slower underwater, make time pass more slowly there
            //TODO: Replace world.getSeaLevel() with something better
            if (craft.getType().getHalfSpeedUnderwater() && craft.getHitBox().getMinY() < world.getSeaLevel())
                ticksElapsed >>= 1;
            // check direct controls to modify movement
            boolean bankLeft = false;
            boolean bankRight = false;
            boolean dive = false;
            if (craft.isUnderDirectControl()) {
                Player pilot = Sponge.getServer().getPlayer(craft.getPilot()).get();
                if (pilot.get(Keys.IS_SNEAKING).get())
                    dive = true;
                if (((PlayerInventory) pilot.getInventory()).getHotbar().getSelectedSlotIndex() == 3)
                    bankLeft = true;
                if (((PlayerInventory) pilot.getInventory()).getHotbar().getSelectedSlotIndex() == 5)
                    bankRight = true;
            }

            if (ticksElapsed < craft.getTickCooldown()) {
                return;
            }

            int dx = 0;
            int dz = 0;
            int dy = 0;

            // ascend
            if (craft.getCruiseDirection() == Direction.UP) {
                dy = 1 + craft.getType().getVertCruiseSkipBlocks();
            }
            // descend
            if (craft.getCruiseDirection() == Direction.DOWN) {
                dy = 0 - 1 - craft.getType().getVertCruiseSkipBlocks();
                if (craft.getHitBox().getMinY() <= world.getSeaLevel()) {
                    dy = -1;
                }
            } else if (dive) {
                dy = 0 - ((craft.getType().getCruiseSkipBlocks() + 1) >> 1);
                if (craft.getHitBox().getMinY() <= world.getSeaLevel()) {
                    dy = -1;
                }
            }
            // ship faces west
            if (craft.getCruiseDirection() == Direction.WEST) {
                dx = 1 + craft.getType().getCruiseSkipBlocks();
                if (bankRight) {
                    dz = (0 - 1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankLeft) {
                    dz = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            // ship faces east
            if (craft.getCruiseDirection() == Direction.EAST) {
                dx = 0 - 1 - craft.getType().getCruiseSkipBlocks();
                if (bankLeft) {
                    dz = (0 - 1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankRight) {
                    dz = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            // ship faces north
            if (craft.getCruiseDirection() == Direction.NORTH) {
                dz = 1 + craft.getType().getCruiseSkipBlocks();
                if (bankRight) {
                    dx = (0 - 1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankLeft) {
                    dx = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            // ship faces south
            if (craft.getCruiseDirection() == Direction.SOUTH) {
                dz = 0 - 1 - craft.getType().getCruiseSkipBlocks();
                if (bankLeft) {
                    dx = (0 - 1 - craft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankRight) {
                    dx = (1 + craft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            if (craft.getType().getCruiseOnPilot()) {
                dy = craft.getType().getCruiseOnPilotVertMove();
            }
            craft.translate(Rotation.NONE, new Vector3i(dx, dy, dz), false);
            craft.setLastMoveVector(new Vector3i(dx, dy, dz));
            if (craft.getLastMoveTick() != -1) {
                craft.setLastMoveTick(Sponge.getServer().getRunningTimeTicks());
            } else {
                craft.setLastMoveTick(Sponge.getServer().getRunningTimeTicks() - 600);
            }
        }
    }

    private void detectSinking(){
        List<Craft> crafts = Lists.newArrayList(CraftManager.getInstance());
        for(Craft pcraft : crafts) {
            if (pcraft.getState() == CraftState.SINKING) {
                continue;
            }
            if (pcraft.getType().getSinkPercent() == 0.0 || !pcraft.isNotProcessing()) {
                continue;
            }
            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - pcraft.getLastCheckTime();

            if (ticksElapsed <= Settings.SinkCheckTicks) {
                continue;
            }

            final World world = pcraft.getWorld();
            int totalNonAirBlocks = 0;
            int totalNonAirWaterBlocks = 0;
            HashMap<List<BlockType>, Integer> foundFlyBlocks = new HashMap<>();
            HashMap<List<BlockType>, Integer> foundMoveBlocks = new HashMap<>();
            // go through each block in the blocklist, and
            // if its in the FlyBlocks, total up the number
            // of them
            for (Vector3i l : pcraft.getHitBox()) {
                BlockType blockType = world.getBlock(l.getX(), l.getY(), l.getZ()).getType();
                for (List<BlockType> flyBlockDef : pcraft.getType().getFlyBlocks().keySet()) {
                    if (flyBlockDef.contains(blockType)) {
                        foundFlyBlocks.merge(flyBlockDef, 1, (a, b) -> a + b);
                    }
                }
                for (List<BlockType> moveBlockDef : pcraft.getType().getMoveBlocks().keySet()) {
                    if (moveBlockDef.contains(blockType)) {
                        foundMoveBlocks.merge(moveBlockDef, 1, (a, b) -> a + b);
                    }
                }

                if (blockType != BlockTypes.AIR) {
                    totalNonAirBlocks++;
                }
                if (blockType != BlockTypes.AIR && blockType != BlockTypes.FLOWING_WATER && blockType != BlockTypes.WATER) {
                    totalNonAirWaterBlocks++;
                }
            }

            // now see if any of the resulting percentages
            // are below the threshold specified in
            // SinkPercent
            boolean isSinking = false;

            for (List<BlockType> i : pcraft.getType().getFlyBlocks().keySet()) {
                int numfound = 0;
                if (foundFlyBlocks.get(i) != null) {
                    numfound = foundFlyBlocks.get(i);
                }
                double percent = ((double) numfound / (double) totalNonAirBlocks) * 100.0;
                double flyPercent = pcraft.getType().getFlyBlocks().get(i).get(0);
                double sinkPercent = flyPercent * pcraft.getType().getSinkPercent() / 100.0;
                if (percent < sinkPercent) {
                    isSinking = true;
                }

            }
            for (List<BlockType> i : pcraft.getType().getMoveBlocks().keySet()) {
                int numfound = 0;
                if (foundMoveBlocks.get(i) != null) {
                    numfound = foundMoveBlocks.get(i);
                }
                double percent = ((double) numfound / (double) totalNonAirBlocks) * 100.0;
                double movePercent = pcraft.getType().getMoveBlocks().get(i).get(0);
                double disablePercent = movePercent * pcraft.getType().getSinkPercent() / 100.0;
                if (percent < disablePercent && pcraft.getState() != CraftState.DISABLED && pcraft.isNotProcessing()) {
                    pcraft.setState(CraftState.DISABLED);
                    if (pcraft.getPilot() != null) {
                        Location loc = Sponge.getServer().getPlayer(pcraft.getPilot()).get().getLocation();
                        pcraft.getWorld().playSound(SoundTypes.ENTITY_IRONGOLEM_DEATH, loc.getPosition(),  5.0f, 5.0f);
                    }
                }
            }

            // And check the overallsinkpercent
            if (pcraft.getType().getOverallSinkPercent() != 0.0) {
                double percent;
                if (pcraft.getType().blockedByWater()) {
                    percent = (double) totalNonAirBlocks
                            / (double) pcraft.getInitialSize();
                } else {
                    percent = (double) totalNonAirWaterBlocks
                            / (double) pcraft.getInitialSize();
                }
                if (percent * 100.0 < pcraft.getType().getOverallSinkPercent()) {
                    isSinking = true;
                }
            }

            if (totalNonAirBlocks == 0) {
                isSinking = true;
            }

            // if the craft is sinking, let the player
            // know and release the craft. Otherwise
            // update the time for the next check
            if (isSinking && pcraft.isNotProcessing()) {
                Player notifyP = Sponge.getServer().getPlayer(pcraft.getPilot()).orElse(null);
                if (notifyP != null) {
                    notifyP.sendMessage(Text.of("Craft is sinking!"));
                }
                pcraft.setState(CraftState.SINKING);
                CraftManager.getInstance().removePlayerFromCraft(pcraft);
            } else {
                pcraft.setLastCheckTime(Sponge.getServer().getRunningTimeTicks());
            }
        }
    }

    //Controls sinking crafts
    private void processSinking() {
        //copy the crafts before iteration to prevent concurrent modifications
        List<Craft> crafts = Lists.newArrayList(CraftManager.getInstance());
        for(Craft craft : crafts){
            if (craft == null || craft.getState() != CraftState.SINKING) {
                continue;
            }
            if (craft.getHitBox().isEmpty() || craft.getHitBox().getMinY() < 5) {
                CraftManager.getInstance().removeCraft(craft);
                continue;
            }
            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - craft.getLastMoveTick();
            if (Math.abs(ticksElapsed) < craft.getType().getSinkRateTicks()) {
                continue;
            }
            int dx = 0;
            int dz = 0;
            if (craft.getType().getKeepMovingOnSink()) {
                dx = craft.getLastMoveVector().getX();
                dz = craft.getLastMoveVector().getZ();
            }
            craft.translate(Rotation.NONE, new Vector3i(dx, -1, dz), false);
            craft.setLastMoveTick(craft.getLastMoveTick() != -1 ? Sponge.getServer().getRunningTimeTicks() : Sponge.getServer().getRunningTimeTicks() - 600);
        }
    }

    private void processDetection() {
        long ticksElapsed = (System.currentTimeMillis() - lastContactCheck) / 50;
        if (ticksElapsed > 21) {
            for (World world : Sponge.getServer().getWorlds()) {
                if (world != null) {
                    for (Craft ccraft : CraftManager.getInstance().getCraftsInWorld(world)) {
                        if (!ccraft.getCrewList().isEmpty()) {
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

        processCruise();
        detectSinking();
        processSinking();
        processDetection();
        processAlgorithmQueue();

        // Cleanup crafts that are bugged and have not moved in the past 60 seconds, but have no crew or are still processing.
        for (Craft craft : CraftManager.getInstance()) {
            if (craft.getCrewList().isEmpty() && craft.getLastMoveTick() < Sponge.getServer().getRunningTimeTicks() - 1200) {
                CraftManager.getInstance().forceRemoveCraft(craft);
            }

            // Stop crafts from moving if they have taken too long to process.
            if (!craft.isNotProcessing() && craft.getState() == CraftState.CRUISING && craft.getLastMoveTick() < Sponge.getServer().getRunningTimeTicks() - 100) {

                    craft.setProcessing(false);
            }
        }
    }
}