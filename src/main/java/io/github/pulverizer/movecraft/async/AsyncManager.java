package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.Lists;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.async.detection.DetectionTask;
import io.github.pulverizer.movecraft.async.detection.DetectionTaskData;
import io.github.pulverizer.movecraft.async.rotation.RotationTask;
import io.github.pulverizer.movecraft.async.translation.TranslationTask;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.mapUpdater.MapUpdateManager;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Dispenser;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.data.value.mutable.MapValue;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.explosive.fireball.SmallFireball;
import org.spongepowered.api.item.inventory.entity.PlayerInventory;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.blockray.BlockRay;
import org.spongepowered.api.util.blockray.BlockRayHit;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings("deprecation")
public class AsyncManager implements Runnable {
    private static AsyncManager ourInstance;
    private final HashMap<AsyncTask, Craft> ownershipMap = new HashMap<>();
    private final HashMap<PrimedTNT, Double> TNTTracking = new HashMap<>();
    private final HashMap<Craft, HashMap<Craft, Long>> recentContactTracking = new HashMap<>();
    private final BlockingQueue<AsyncTask> finishedAlgorithms = new LinkedBlockingQueue<>();
    private final HashSet<Craft> clearanceSet = new HashSet<>();
    private HashMap<SmallFireball, HashMap<Long, Vector3d>> FireballTracking = new HashMap<>();
    private long lastTracerUpdate = 0;
    private long lastFireballCheck = 0;
    private long lastTNTContactCheck = 0;
    private long lastFadeCheck = 0;
    private long lastContactCheck = 0;
    private HashSet<BlockType> transparent;

    public AsyncManager() {
        transparent = new HashSet<>();
        transparent.add(BlockTypes.AIR);
        transparent.add(BlockTypes.GLASS);
        transparent.add(BlockTypes.GLASS_PANE);
        transparent.add(BlockTypes.STAINED_GLASS);
        transparent.add(BlockTypes.STAINED_GLASS_PANE);
        transparent.add(BlockTypes.IRON_BARS);
        transparent.add(BlockTypes.REDSTONE_WIRE);
        transparent.add(BlockTypes.IRON_TRAPDOOR);
        transparent.add(BlockTypes.TRAPDOOR);
        transparent.add(BlockTypes.NETHER_BRICK_STAIRS);
        transparent.add(BlockTypes.LEVER);
        transparent.add(BlockTypes.STONE_BUTTON);
        transparent.add(BlockTypes.WOODEN_BUTTON);
        transparent.add(BlockTypes.ACACIA_STAIRS);
        transparent.add(BlockTypes.SANDSTONE_STAIRS);
        transparent.add(BlockTypes.BIRCH_STAIRS);
        transparent.add(BlockTypes.BRICK_STAIRS);
        transparent.add(BlockTypes.DARK_OAK_STAIRS);
        transparent.add(BlockTypes.JUNGLE_STAIRS);
        transparent.add(BlockTypes.OAK_STAIRS);
        transparent.add(BlockTypes.PURPUR_STAIRS);
        transparent.add(BlockTypes.QUARTZ_STAIRS);
        transparent.add(BlockTypes.RED_SANDSTONE_STAIRS);
        transparent.add(BlockTypes.SPRUCE_STAIRS);
        transparent.add(BlockTypes.STONE_BRICK_STAIRS);
        transparent.add(BlockTypes.STONE_STAIRS);
        transparent.add(BlockTypes.WALL_SIGN);
        transparent.add(BlockTypes.STANDING_SIGN);
    }

    public static AsyncManager getInstance() {
        return ourInstance;
    }
    public static void initialize(){
        ourInstance = new AsyncManager();
    }

    public void submitTask(AsyncTask task, Craft c) {
        if (c.isNotProcessing()) {
            c.setProcessing(true);
            ownershipMap.put(task, c);
            task.run(Movecraft.getInstance(), true);
        }
    }

    public void submitCompletedTask(AsyncTask task) {
        finishedAlgorithms.add(task);
    }

    private void processAlgorithmQueue() {
        int runLength = 10;
        int queueLength = finishedAlgorithms.size();

        runLength = Math.min(runLength, queueLength);

        for (int i = 0; i < runLength; i++) {
            boolean sentMapUpdate = false;
            AsyncTask poll = finishedAlgorithms.poll();
            Craft c = ownershipMap.get(poll);

            if (poll instanceof DetectionTask) {
                // Process detection task

                DetectionTask task = (DetectionTask) poll;
                DetectionTaskData data = task.getData();

                Player p = data.getPlayer();
                Player notifyP = data.getNotificationPlayer();
                Craft pCraft = CraftManager.getInstance().getCraftByPlayer(p);

                if (pCraft != null && p != null) {
                    // Player is already controlling a craft
                    notifyP.sendMessage(Text.of("Detection Failed! You are already commanding a craft."));
                } else {
                    if (data.failed()) {
                        if (notifyP != null)
                            notifyP.sendMessage(Text.of(data.getFailMessage()));
                        else
                            Movecraft.getInstance().getLogger().info("NULL Player Craft Detection failed:" + data.getFailMessage());

                    } else {
                        Set<Craft> craftsInWorld = CraftManager.getInstance().getCraftsInWorld(c.getW());
                        boolean failed = false;
                        boolean isSubcraft = false;

                        for (Craft craft : craftsInWorld) {
                            if(craft.getHitBox().intersects(data.getBlockList())){
                                isSubcraft = true;
                                if (c.getType().getCruiseOnPilot() || p != null) {
                                    if (craft.getType() == c.getType()
                                            || craft.getHitBox().size() <= data.getBlockList().size()) {
                                        notifyP.sendMessage(Text.of("Detection Failed. Craft is already being controlled by another player."));
                                        failed = true;
                                    } else {
                                        // if this is a different type than
                                        // the overlapping craft, and is
                                        // smaller, this must be a child
                                        // craft, like a fighter on a
                                        // carrier
                                        if (!craft.isNotProcessing()) {
                                            failed = true;
                                            notifyP.sendMessage(Text.of("Parent Craft is busy."));
                                        }
                                        craft.setHitBox(new HashHitBox(CollectionUtils.filter(craft.getHitBox(),data.getBlockList())));
                                        craft.setOrigBlockCount(craft.getOrigBlockCount() - data.getBlockList().size());
                                    }
                                }
                            }


                        }
                        if (c.getType().getMustBeSubcraft() && !isSubcraft) {
                            failed = true;
                            notifyP.sendMessage(Text.of("Craft must be part of another craft!"));
                        }
                        if (!failed) {
                            c.setHitBox(task.getData().getBlockList());
                            c.setOrigBlockCount(data.getBlockList().size());
                            c.setNotificationPlayer(notifyP);
                            final int waterLine = c.getWaterLine();
                            if(!c.getType().blockedByWater() && c.getHitBox().getMinY() <= waterLine){
                                for(MovecraftLocation location : c.getHitBox().boundingHitBox()){
                                    if(location.getY() <= waterLine){
                                        c.getPhaseBlocks().put(location, BlockTypes.WATER.getDefaultState().snapshotFor(location.toSponge(c.getW())));
                                    }
                                }
                            }

                            if (notifyP != null) {
                                notifyP.sendMessage(Text.of("Successfully piloted " + c.getType().getCraftName() + " Size: " + c.getHitBox().size()));
                                Movecraft.getInstance().getLogger().info("New Craft Detected! Pilot: " + notifyP.getName() + " CraftType: " + c.getType().getCraftName() + " Size: " + c.getHitBox().size() + " Location: " + c.getHitBox().getMinX() + ", " + c.getHitBox().getMinY() + ", " + c.getHitBox().getMinZ());
                            } else {
                                Movecraft.getInstance().getLogger().info("New Craft Detected! Pilot: " + "NULL PLAYER" + " CraftType: " + c.getType().getCraftName() + " Size: " + c.getHitBox().size() + " Location: " + c.getHitBox().getMinX() + ", " + c.getHitBox().getMinY() + ", " + c.getHitBox().getMinZ());
                            }
                            CraftManager.getInstance().addCraft(c, p);
                        }
                    }
                }
                if(c!=null){
                    Sponge.getEventManager().post(new CraftDetectEvent(c));
                }

            } else if (poll instanceof TranslationTask) {
                // Process translation task

                TranslationTask task = (TranslationTask) poll;
                Player notifyP = c.getNotificationPlayer();

                // Check that the craft hasn't been sneakily unpiloted
                // if ( p != null ) { cruiseOnPilot crafts don't have player
                // pilots

                if (task.failed()) {
                    // The craft translation failed
                    if (notifyP != null && !c.getSinking())
                        notifyP.sendMessage(Text.of(task.getFailMessage()));

                    if (task.isCollisionExplosion()) {
                        //c.setHitBox(task.getNewHitBox());
                        MapUpdateManager.getInstance().scheduleUpdates(task.getUpdates());
                        sentMapUpdate = true;
                        CraftManager.getInstance().addReleaseTask(c);

                    }
                } else {
                    // The craft is clear to move, perform the block updates
                    MapUpdateManager.getInstance().scheduleUpdates(task.getUpdates());

                    sentMapUpdate = true;
                    //c.setHitBox(task.getNewHitBox());
                }

            } else if (poll instanceof RotationTask) {
                // Process rotation task
                RotationTask task = (RotationTask) poll;
                Player notifyP = c.getNotificationPlayer();

                // Check that the craft hasn't been sneakily unpiloted
                if (notifyP != null || task.getIsSubCraft()) {

                    if (task.isFailed()) {
                        // The craft translation failed, don't try to notify
                        // them if there is no pilot
                        if (notifyP != null)
                            notifyP.sendMessage(Text.of(task.getFailMessage()));
                        else
                            Movecraft.getInstance().getLogger().info("NULL Player Rotation Failed: " + task.getFailMessage());
                    } else {
                        if (c.getNotificationPlayer() != null) {
                            // convert blocklist to location list
                            List<Location> shipLocations = new ArrayList<>();
                            for (MovecraftLocation loc : c.getHitBox()) {
                                shipLocations.add(loc.toSponge(c.getW()));
                            }
                        }

                        MapUpdateManager.getInstance().scheduleUpdates(task.getUpdates());


                        sentMapUpdate = true;

                        c.setHitBox(task.getNewHitBox());
                    }
                }
            }

            ownershipMap.remove(poll);

            // only mark the craft as having finished updating if you didn't
            // send any updates to the map updater. Otherwise the map updater
            // will mark the crafts once it is done with them.
            if (!sentMapUpdate) {
                clear(c);
            }
        }
    }

    private void processCruise() {
        for (Craft pcraft : CraftManager.getInstance()) {
            if (pcraft == null || !pcraft.isNotProcessing() || !pcraft.getCruising()) {
                continue;
            }
            long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;
            World w = pcraft.getW();
            // if the craft should go slower underwater, make
            // time pass more slowly there
            if (pcraft.getType().getHalfSpeedUnderwater() && pcraft.getHitBox().getMinY() < w.getSeaLevel())
                ticksElapsed >>= 1;
            // check direct controls to modify movement
            boolean bankLeft = false;
            boolean bankRight = false;
            boolean dive = false;
            if (pcraft.getPilotLocked()) {
                if (pcraft.getNotificationPlayer().get(Keys.IS_SNEAKING).get())
                    dive = true;
                if (((PlayerInventory) pcraft.getNotificationPlayer().getInventory()).getHotbar().getSelectedSlotIndex() == 3)
                    bankLeft = true;
                if (((PlayerInventory) pcraft.getNotificationPlayer().getInventory()).getHotbar().getSelectedSlotIndex() == 5)
                    bankRight = true;
            }

            if (Math.abs(ticksElapsed) < pcraft.getTickCooldown()) {
                return;
            }
            int dx = 0;
            int dz = 0;
            int dy = 0;

            // ascend
            if (pcraft.getCruiseDirection() == Direction.UP) {
                dy = 1 + pcraft.getType().getVertCruiseSkipBlocks();
            }
            // descend
            if (pcraft.getCruiseDirection() == Direction.DOWN) {
                dy = 0 - 1 - pcraft.getType().getVertCruiseSkipBlocks();
                if (pcraft.getHitBox().getMinY() <= w.getSeaLevel()) {
                    dy = -1;
                }
            } else if (dive) {
                dy = 0 - ((pcraft.getType().getCruiseSkipBlocks() + 1) >> 1);
                if (pcraft.getHitBox().getMinY() <= w.getSeaLevel()) {
                    dy = -1;
                }
            }
            // ship faces west
            if (pcraft.getCruiseDirection() == Direction.WEST) {
                dx = 1 + pcraft.getType().getCruiseSkipBlocks();
                if (bankRight) {
                    dz = (0 - 1 - pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankLeft) {
                    dz = (1 + pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            // ship faces east
            if (pcraft.getCruiseDirection() == Direction.EAST) {
                dx = 0 - 1 - pcraft.getType().getCruiseSkipBlocks();
                if (bankLeft) {
                    dz = (0 - 1 - pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankRight) {
                    dz = (1 + pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            // ship faces north
            if (pcraft.getCruiseDirection() == Direction.NORTH) {
                dz = 1 + pcraft.getType().getCruiseSkipBlocks();
                if (bankRight) {
                    dx = (0 - 1 - pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankLeft) {
                    dx = (1 + pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            // ship faces south
            if (pcraft.getCruiseDirection() == Direction.SOUTH) {
                dz = 0 - 1 - pcraft.getType().getCruiseSkipBlocks();
                if (bankLeft) {
                    dx = (0 - 1 - pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
                if (bankRight) {
                    dx = (1 + pcraft.getType().getCruiseSkipBlocks()) >> 1;
                }
            }
            if (pcraft.getType().getCruiseOnPilot()) {
                dy = pcraft.getType().getCruiseOnPilotVertMove();
            }
            pcraft.translate(dx, dy, dz);
            pcraft.setLastDX(dx);
            pcraft.setLastDZ(dz);
            if (pcraft.getLastCruiseUpdate() != -1) {
                pcraft.setLastCruiseUpdate(System.currentTimeMillis());
            } else {
                pcraft.setLastCruiseUpdate(System.currentTimeMillis() - 30000);
            }
        }
    }

    private void detectSinking(){
        List<Craft> crafts = Lists.newArrayList(CraftManager.getInstance());
        for(Craft pcraft : crafts) {
            if (pcraft.getSinking()) {
                continue;
            }
            if (pcraft.getType().getSinkPercent() == 0.0 || !pcraft.isNotProcessing()) {
                continue;
            }
            long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastBlockCheck()) / 50;

            if (ticksElapsed <= Settings.SinkCheckTicks) {
                continue;
            }
            final World w = pcraft.getW();
            int totalNonAirBlocks = 0;
            int totalNonAirWaterBlocks = 0;
            HashMap<List<BlockType>, Integer> foundFlyBlocks = new HashMap<>();
            HashMap<List<BlockType>, Integer> foundMoveBlocks = new HashMap<>();
            // go through each block in the blocklist, and
            // if its in the FlyBlocks, total up the number
            // of them
            for (MovecraftLocation l : pcraft.getHitBox()) {
                BlockType blockID = w.getBlock(l.getX(), l.getY(), l.getZ()).getType();
                for (List<BlockType> flyBlockDef : pcraft.getType().getFlyBlocks().keySet()) {
                    if (flyBlockDef.contains(blockID)) {
                        foundFlyBlocks.merge(flyBlockDef, 1, (a, b) -> a + b);
                    }
                }
                for (List<BlockType> moveBlockDef : pcraft.getType().getMoveBlocks().keySet()) {
                    if (moveBlockDef.contains(blockID)) {
                        foundMoveBlocks.merge(moveBlockDef, 1, (a, b) -> a + b);
                    }
                }

                if (blockID != BlockTypes.AIR) {
                    totalNonAirBlocks++;
                }
                if (blockID != BlockTypes.AIR && blockID != BlockTypes.FLOWING_WATER && blockID != BlockTypes.WATER) {
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
                if (percent < disablePercent && !pcraft.getDisabled() && pcraft.isNotProcessing()) {
                    pcraft.setDisabled(true);
                    if (pcraft.getNotificationPlayer() != null) {
                        Location loc = pcraft.getNotificationPlayer().getLocation();
                        pcraft.getW().playSound(SoundTypes.ENTITY_IRONGOLEM_DEATH, loc.getPosition(),  5.0f, 5.0f);
                    }
                }
            }

            // And check the overallsinkpercent
            if (pcraft.getType().getOverallSinkPercent() != 0.0) {
                double percent;
                if (pcraft.getType().blockedByWater()) {
                    percent = (double) totalNonAirBlocks
                            / (double) pcraft.getOrigBlockCount();
                } else {
                    percent = (double) totalNonAirWaterBlocks
                            / (double) pcraft.getOrigBlockCount();
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
                Player notifyP = pcraft.getNotificationPlayer();
                if (notifyP != null) {
                    notifyP.sendMessage(Text.of("Craft is sinking!"));
                }
                pcraft.setCruising(false);
                pcraft.sink();
                CraftManager.getInstance().removePlayerFromCraft(pcraft);
            } else {
                pcraft.setLastBlockCheck(System.currentTimeMillis());
            }
        }
    }

    //Controls sinking crafts
    private void processSinking() {
        //copy the crafts before iteration to prevent concurrent modifications
        List<Craft> crafts = Lists.newArrayList(CraftManager.getInstance());
        for(Craft craft : crafts){
            if (craft == null || !craft.getSinking()) {
                continue;
            }
            if (craft.getHitBox().isEmpty() || craft.getHitBox().getMinY() < 5) {
                CraftManager.getInstance().removeCraft(craft);
                continue;
            }
            long ticksElapsed = (System.currentTimeMillis() - craft.getLastCruiseUpdate()) / 50;
            if (Math.abs(ticksElapsed) < craft.getType().getSinkRateTicks()) {
                continue;
            }
            int dx = 0;
            int dz = 0;
            if (craft.getType().getKeepMovingOnSink()) {
                dx = craft.getLastDX();
                dz = craft.getLastDZ();
            }
            craft.translate(dx, -1, dz);
            craft.setLastCruiseUpdate(System.currentTimeMillis() - (craft.getLastCruiseUpdate() != -1 ? 0 : 30000));
        }
    }

    private void processTracers() {
        if (Settings.TracerRateTicks == 0)
            return;
        long ticksElapsed = (System.currentTimeMillis() - lastTracerUpdate) / 50;
        if (ticksElapsed > Settings.TracerRateTicks) {
            for (World w : Sponge.getServer().getWorlds()) {
                if (w != null) {
                    for (Entity entity : w.getEntities(entity -> entity.getType().equals(EntityTypes.PRIMED_TNT))) {
                        PrimedTNT tnt = null;
                        if (entity instanceof PrimedTNT)
                            tnt = (PrimedTNT) entity;
                        if (tnt == null)
                            continue;
                        if (tnt.getVelocity().lengthSquared() > 0.25) {
                            for (Player p : w.getPlayers()) {
                                // is the TNT within the render distance of the player?
                                long maxDistSquared = w.getViewDistance() * 16;
                                maxDistSquared = maxDistSquared - 16;
                                maxDistSquared = maxDistSquared * maxDistSquared;

                                if (p.getLocation().getBlockPosition().distanceSquared(tnt.getLocation().getBlockPosition()) < maxDistSquared) { // we
                                    // use
                                    // squared
                                    // because
                                    // its
                                    // faster
                                    final Vector3i loc = tnt.getLocation().getBlockPosition();
                                    final Player fp = p;
                                    // then make a cobweb to look like smoke,
                                    // place it a little later so it isn't right
                                    // in the middle of the volley
                                    Task.builder().delayTicks(5).execute( () -> fp.sendBlockChange(loc, BlockTypes.WEB.getDefaultState()) ).submit(Movecraft.getInstance());

                                    // then remove it
                                    Task.builder().delayTicks(160).execute( () -> fp.sendBlockChange(loc, BlockTypes.AIR.getDefaultState()) ).submit(Movecraft.getInstance());
                                }
                            }
                        }
                    }
                }
            }
            lastTracerUpdate = System.currentTimeMillis();
        }
    }


    private void processFireballs() {
        long ticksElapsed = (System.currentTimeMillis() - lastFireballCheck) / 50;

        if (ticksElapsed > 0) {
            for (World w : Sponge.getServer().getWorlds()) {
                if (w != null) {
                    for (Entity entity : w.getEntities(entity -> entity.getType().equals(EntityTypes.SMALL_FIREBALL))) {
                        SmallFireball fireball = null;
                        if (entity instanceof SmallFireball)
                            fireball = (SmallFireball) entity;
                        if (fireball == null)
                            continue;

                        if (fireball.getShooter() instanceof Dispenser) {
                            // means it was launched by a dispenser

                            if (!FireballTracking.containsKey(fireball)) {
                                //TODO: SEE HACK-PATCH BELOW! Moved this higher to allow hack-patch to work. Move back after!!!
                                Vector3d fireballVelocity = fireball.getVelocity();

                                Craft c = fastNearestCraftToLoc(fireball.getLocation());
                                if (c != null) {
                                    int distX = c.getHitBox().getMinX() + c.getHitBox().getMaxX();
                                    distX = distX >> 1;
                                    distX = Math.abs(distX - fireball.getLocation().getBlockX());
                                    int distY = c.getHitBox().getMinY() + c.getHitBox().getMaxY();
                                    distY = distY >> 1;
                                    distY = Math.abs(distY - fireball.getLocation().getBlockY());
                                    int distZ = c.getHitBox().getMinZ() + c.getHitBox().getMaxZ();
                                    distZ = distZ >> 1;
                                    distZ = Math.abs(distZ - fireball.getLocation().getBlockZ());
                                    boolean inRange = (distX < 50) && (distY < 50) && (distZ < 50);
                                    if ((c.getAADirector() != null) && inRange) {
                                        Player p = c.getAADirector();
                                        if (p.getItemInHand(HandTypes.MAIN_HAND).get().getType() == Settings.PilotTool) {

                                            //TODO: SEE HACK-PATCH BELOW! Moving this higher to allow hack-patch to work. Move back after!!!
                                            //Vector3d fireballVelocity = fireball.getVelocity();
                                            double speed = fireballVelocity.length(); // store the speed to add it back in later, since all the values we will be using are "normalized", IE: have a speed of 1
                                            fireballVelocity = fireballVelocity.normalize(); // you normalize it for comparison with the new direction to see if we are trying to steer too far
                                            Movecraft.getInstance().getLogger().info("Normalised Velocity: " + fireballVelocity.toString());

                                            BlockSnapshot targetBlock = null;
                                            Optional<BlockRayHit<World>> blockRayHit = BlockRay
                                                    .from(p)
                                                    .distanceLimit(p.getViewDistance() * 16)
                                                    .skipFilter(hit -> transparent.contains(hit.getLocation().getBlockType()))
                                                    .stopFilter(BlockRay.allFilter())
                                                    .build()
                                                    .end();

                                            if (blockRayHit.isPresent()) {
                                                // Target is Block :)
                                                targetBlock = blockRayHit.get().getLocation().createSnapshot();
                                            }

                                            Vector3d targetVector;
                                            if (targetBlock == null) { // the player is looking at nothing, shoot in that general direction
                                                targetVector = p.getHeadRotation();
                                            } else { // shoot directly at the block the player is looking at (IE: with convergence)
                                                targetVector = targetBlock.getLocation().get().getPosition().sub(fireball.getLocation().getPosition());
                                                targetVector = targetVector.normalize();
                                                Movecraft.getInstance().getLogger().info("Normalised Target Vector: " + targetVector.toString());
                                            }

                                            if (targetVector.getX() - fireballVelocity.getX() > 0.5) {
                                                Movecraft.getInstance().getLogger().info("IF Fired!");
                                                fireballVelocity = fireballVelocity.add(0.5, 0, 0);
                                            } else if (targetVector.getX() - fireballVelocity.getX() < -0.5) {
                                                Movecraft.getInstance().getLogger().info("IF Fired!");
                                                fireballVelocity = fireballVelocity.sub(0.5, 0, 0);
                                            } else {
                                                fireballVelocity = new Vector3d(targetVector.getX(), fireballVelocity.getY(), fireballVelocity.getZ());
                                            }

                                            if (targetVector.getY() - fireballVelocity.getY() > 0.5) {
                                                Movecraft.getInstance().getLogger().info("IF Fired!");
                                                fireballVelocity = fireballVelocity.add(0, 0.5, 0);
                                            } else if (targetVector.getY() - fireballVelocity.getY() < -0.5) {
                                                Movecraft.getInstance().getLogger().info("IF Fired!");
                                                fireballVelocity = fireballVelocity.sub(0, 0.5, 0);
                                            } else {
                                                fireballVelocity = new Vector3d(fireballVelocity.getX(), targetVector.getY(), fireballVelocity.getZ());
                                            }

                                            if (targetVector.getZ() - fireballVelocity.getZ() > 0.5) {
                                                Movecraft.getInstance().getLogger().info("IF Fired!");
                                                fireballVelocity = fireballVelocity.add(0, 0, 0.5);
                                            } else if (targetVector.getZ() - fireballVelocity.getZ() < -0.5) {
                                                Movecraft.getInstance().getLogger().info("IF Fired!");
                                                fireballVelocity = fireballVelocity.sub(0, 0, 0.5);
                                            } else {
                                                fireballVelocity = new Vector3d(fireballVelocity.getX(), fireballVelocity.getY(), targetVector.getZ());
                                            }

                                            Movecraft.getInstance().getLogger().info("New Normalised Velocity: " + fireballVelocity.toString());
                                            fireballVelocity = fireballVelocity.mul(speed); // put the original speed back in, but now along a different trajectory

                                            fireball.setVelocity(fireballVelocity);
                                        }
                                    }
                                }

                                //TODO: Remove hacky Vector data. Waiting on fix of SpongeAPI: https://github.com/SpongePowered/SpongeAPI/issues/1981
                                HashMap<Long, Vector3d> timeAndAcceleration = new HashMap<>();
                                timeAndAcceleration.put(System.currentTimeMillis(), fireballVelocity.mul(5));
                                FireballTracking.put(fireball, timeAndAcceleration);
                            }
                        }
                    }
                }
            }

            int timeLimit = 20 * Settings.FireballLifespan * 50;
            Iterator<SmallFireball> fireballI = FireballTracking.keySet().iterator();
            while (fireballI.hasNext()) {
                SmallFireball fireball = fireballI.next();
                if (fireball != null)
                    //remove any dead Fireballs from tracking
                    if (System.currentTimeMillis() - FireballTracking.get(fireball).keySet().iterator().next() > timeLimit) {
                        fireball.remove();
                        fireballI.remove();
                    }

                    //TODO: More of the hack-patch that shall not be mentioned.
                    try {
                        fireball.setVelocity(FireballTracking.get(fireball).entrySet().iterator().next().getValue());
                    } catch (NullPointerException e) {}


            }

            if (Settings.Debug && FireballTracking.size() > 0)
                Movecraft.getInstance().getLogger().info("Tracking " + FireballTracking.size() + " Fireballs.");

            lastFireballCheck = System.currentTimeMillis();
        }
    }

    private Craft fastNearestCraftToLoc(Location loc) {
        Craft ret = null;
        long closestDistSquared = 1000000000L;
        Set<Craft> craftsList = CraftManager.getInstance().getCraftsInWorld((World) loc.getExtent());
        for (Craft i : craftsList) {
            int midX = (i.getHitBox().getMaxX() + i.getHitBox().getMinX()) >> 1;
//				int midY=(i.getMaxY()+i.getMinY())>>1; don't check Y because it is slow
            int midZ = (i.getHitBox().getMaxZ() + i.getHitBox().getMinZ()) >> 1;
            long distSquared = Math.abs(midX - (int) loc.getX());
//				distSquared+=Math.abs(midY-(int)loc.getY());
            distSquared += Math.abs(midZ - (int) loc.getZ());
            if (distSquared < closestDistSquared) {
                closestDistSquared = distSquared;
                ret = i;
            }
        }
        return ret;
    }

    private void processTNTContactExplosives() {
        long ticksElapsed = (System.currentTimeMillis() - lastTNTContactCheck) / 50;
        if (ticksElapsed > 0) {
            // see if there is any new rapid moving TNT in the worlds
            for (World w : Sponge.getServer().getWorlds()) {
                if (w != null) {
                    for (Entity entity : w.getEntities(entity -> entity.getType().equals(EntityTypes.PRIMED_TNT))) {
                        PrimedTNT tnt = null;
                        if (entity instanceof PrimedTNT)
                            tnt = (PrimedTNT) entity;
                        if (tnt == null)
                            continue;
                        if ((tnt.getVelocity().lengthSquared() > 0.35)) {
                            if (!TNTTracking.containsKey(tnt)) {
                                Craft c = fastNearestCraftToLoc(tnt.getLocation());
                                if (c != null) {
                                    int distX = c.getHitBox().getMinX() + c.getHitBox().getMaxX();
                                    distX = distX >> 1;
                                    distX = Math.abs(distX - tnt.getLocation().getBlockX());
                                    int distY = c.getHitBox().getMinY() + c.getHitBox().getMaxY();
                                    distY = distY >> 1;
                                    distY = Math.abs(distY - tnt.getLocation().getBlockY());
                                    int distZ = c.getHitBox().getMinZ() + c.getHitBox().getMaxZ();
                                    distZ = distZ >> 1;
                                    distZ = Math.abs(distZ - tnt.getLocation().getBlockZ());
                                    boolean inRange = (distX < 100) && (distY < 100) && (distZ < 100);
                                    if ((c.getCannonDirector() != null) && inRange) {
                                        Player p = c.getCannonDirector();
                                        if (p.getItemInHand(HandTypes.MAIN_HAND).get().getType() == Settings.PilotTool) {
                                            Vector3d tntVelocity = tnt.getVelocity();
                                            double speed = tntVelocity.length(); // store the speed to add it back in later, since all the values we will be using are "normalized", IE: have a speed of 1
                                            tntVelocity = tntVelocity.normalize(); // you normalize it for comparison with the new direction to see if we are trying to steer too far

                                            BlockSnapshot targetBlock = null;
                                            Optional<BlockRayHit<World>> blockRayHit = BlockRay
                                                    .from(p)
                                                    .distanceLimit(p.getViewDistance() * 16)
                                                    .skipFilter(hit -> transparent.contains(hit.getLocation().getBlockType()))
                                                    .stopFilter(BlockRay.allFilter())
                                                    .build()
                                                    .end();

                                            if (blockRayHit.isPresent())
                                                // Target is Block :)
                                                targetBlock = blockRayHit.get().getLocation().createSnapshot();

                                            Vector3d targetVector;
                                            if (targetBlock == null) { // the player is looking at nothing, shoot in that general direction
                                                targetVector = p.getHeadRotation();
                                            } else { // shoot directly at the block the player is looking at (IE: with convergence)
                                                targetVector = targetBlock.getLocation().get().getPosition().sub(tnt.getLocation().getPosition());
                                                targetVector = targetVector.normalize();
                                            }
                                            //leave the original Y (or vertical axis) trajectory as it was
                                            if (targetVector.getX() - tntVelocity.getX() > 0.7) {
                                                tntVelocity = tntVelocity.add(0.7, 0, 0);
                                            } else if (targetVector.getX() - tntVelocity.getX() < -0.7) {
                                                tntVelocity = tntVelocity.sub(0.7, 0, 0);
                                            } else {
                                                tntVelocity = new Vector3d(targetVector.getX(), tntVelocity.getY(), tntVelocity.getZ());
                                            }
                                            if (targetVector.getZ() - tntVelocity.getZ() > 0.7) {
                                                tntVelocity = tntVelocity.add(0, 0, 0.7);
                                            } else if (targetVector.getZ() - tntVelocity.getZ() < -0.7) {
                                                tntVelocity = tntVelocity.sub(0, 0, 0.7);
                                            } else {
                                                tntVelocity = new Vector3d(tntVelocity.getX(), tntVelocity.getY(), targetVector.getZ());
                                            }
                                            tntVelocity = tntVelocity.mul(speed); // put the original speed back in, but now along a different trajectory
                                            tnt.setVelocity(tntVelocity);
                                        }
                                    }
                                }
                                TNTTracking.put(tnt, tnt.getVelocity().lengthSquared());
                            }
                        }
                    }
                }
            }

            // then, removed any exploded TNT from tracking
            TNTTracking.keySet().removeIf(tnt -> (tnt.getFuseData().ticksRemaining().get() <= 0));

            // now check to see if any has abruptly changed velocity, and should explode
            if (!TNTTracking.isEmpty()) {

                for (PrimedTNT tnt : TNTTracking.keySet()) {
                    double vel = tnt.getVelocity().lengthSquared();
                    if (vel < TNTTracking.get(tnt) / 10.0) {
                        tnt.detonate();
                        TNTTracking.remove(tnt);
                    } else {
                        // update the tracking with the new velocity so gradual
                        // changes do not make TNT explode
                        TNTTracking.put(tnt, vel);
                    }
                }
            }

            if (Settings.Debug && TNTTracking.size() > 0)
                Movecraft.getInstance().getLogger().info("Tracking " + TNTTracking.size() + " TNT.");

            lastTNTContactCheck = System.currentTimeMillis();
        }
    }

    private void processDetection() {
        long ticksElapsed = (System.currentTimeMillis() - lastContactCheck) / 50;
        if (ticksElapsed > 21) {
            for (World w : Sponge.getServer().getWorlds()) {
                if (w != null) {
                    for (Craft ccraft : CraftManager.getInstance().getCraftsInWorld(w)) {
                        if (CraftManager.getInstance().getPlayerFromCraft(ccraft) != null) {
                            if (!recentContactTracking.containsKey(ccraft)) {
                                recentContactTracking.put(ccraft, new HashMap<>());
                            }
                            for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(w)) {
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
                                    detectionRange = (long) (Math.sqrt(tcraft.getOrigBlockCount())
                                            * tcraft.getType().getDetectionMultiplier());
                                } else {
                                    detectionRange = (long) (Math.sqrt(tcraft.getOrigBlockCount())
                                            * tcraft.getType().getUnderwaterDetectionMultiplier());
                                }
                                if (distsquared < detectionRange * detectionRange
                                        && tcraft.getNotificationPlayer() != ccraft.getNotificationPlayer()) {
                                    // craft has been detected

                                    // has the craft not been seen in the last
                                    // minute, or is completely new?
                                    if (recentContactTracking.get(ccraft).get(tcraft) == null
                                            || System.currentTimeMillis()
                                            - recentContactTracking.get(ccraft).get(tcraft) > 60000) {
                                        String notification = "New contact: ";
                                        notification += tcraft.getType().getCraftName();
                                        notification += " commanded by ";
                                        if (tcraft.getNotificationPlayer() != null) {
                                            notification += tcraft.getNotificationPlayer().getDisplayNameData().displayName().toString();
                                        } else {
                                            notification += "NULL";
                                        }
                                        notification += ", size: ";
                                        notification += tcraft.getOrigBlockCount();
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

                                        ccraft.getNotificationPlayer().sendMessage(Text.of(notification));
                                        w.playSound(SoundTypes.BLOCK_ANVIL_LAND, ccraft.getNotificationPlayer().getLocation().getPosition(), 1.0f, 2.0f);
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
        clearAll();

        processCruise();
        detectSinking();
        processSinking();
        processTracers();
        processFireballs();
        processTNTContactExplosives();
        processDetection();
        processAlgorithmQueue();

        // now cleanup craft that are bugged and have not moved in the past 60 seconds, but have no pilot or are still processing
        for (Craft pcraft : CraftManager.getInstance()) {
            if (CraftManager.getInstance().getPlayerFromCraft(pcraft) == null) {
                if (pcraft.getLastCruiseUpdate() < System.currentTimeMillis() - 60000) {
                    CraftManager.getInstance().forceRemoveCraft(pcraft);
                }
            }
            if (!pcraft.isNotProcessing()) {
                if (pcraft.getCruising()) {
                    if (pcraft.getLastCruiseUpdate() < System.currentTimeMillis() - 5000) {
                        pcraft.setProcessing(false);
                    }
                }
            }
        }

    }

    private void clear(Craft c) {
        clearanceSet.add(c);
    }

    private void clearAll() {
        for (Craft c : clearanceSet) {
            c.setProcessing(false);
        }

        clearanceSet.clear();
    }
}