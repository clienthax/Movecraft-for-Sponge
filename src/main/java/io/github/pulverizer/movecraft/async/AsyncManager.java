package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.Lists;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.mapUpdater.MapUpdateManager;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Dispenser;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.explosive.PrimedTNT;
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

public class AsyncManager implements Runnable {
    private static AsyncManager ourInstance;
    private final HashMap<PrimedTNT, Double> TNTTracking = new HashMap<>();
    private final HashMap<Craft, HashMap<Craft, Long>> recentContactTracking = new HashMap<>();
    private final BlockingQueue<AsyncTask> taskQueue = new LinkedBlockingQueue<>();
    private final HashSet<Craft> clearanceSet = new HashSet<>();
    private HashMap<SmallFireball, Long> FireballTracking = new HashMap<>();
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

    public void submitCompletedTask(AsyncTask task) {
        taskQueue.add(task);
    }

    private void processAlgorithmQueue() {
        int runLength = 10;
        int queueLength = taskQueue.size();

        runLength = Math.min(runLength, queueLength);

        for (int i = 0; i < runLength; i++) {
            boolean sentMapUpdate = false;
            AsyncTask poll = taskQueue.poll();
            Craft craft = poll.getCraft();

            if (poll instanceof DetectionTask) {
                // Process detection task

                DetectionTask task = (DetectionTask) poll;

                Player player = Sponge.getServer().getPlayer(craft.getOriginalPilot()).orElse(null);

                    if (task.failed()) {
                        if (player != null)
                            player.sendMessage(Text.of(task.getFailMessage()));
                        else
                            Movecraft.getInstance().getLogger().info("NULL Player Craft Detection failed:" + task.getFailMessage());

                    } else {
                        Set<Craft> craftsInWorld = CraftManager.getInstance().getCraftsInWorld(craft.getWorld());
                        boolean failed = false;
                        boolean isSubcraft = false;
                        Craft parentCraft = null;

                        for (Craft testCraft : craftsInWorld) {
                            if (testCraft.getHitBox().intersects(task.getHitBox())) {
                                Movecraft.getInstance().getLogger().info("Test Craft Size" + testCraft.getHitBox().size());

                                isSubcraft = true;
                                parentCraft = testCraft;
                                break;
                            }
                        }

                        Movecraft.getInstance().getLogger().info("Subcraft: " + isSubcraft);
                        Movecraft.getInstance().getLogger().info("Hitbox: " + task.getHitBox().size());
                        if (parentCraft != null)
                            Movecraft.getInstance().getLogger().info("Parent Craft: " + parentCraft.getHitBox().size() + "   Pilot: " + Sponge.getServer().getPlayer(player.getUniqueId()));

                        if (player != null && isSubcraft && !parentCraft.isCrewMember(player.getUniqueId())) {
                            // Player is already controlling a craft
                            player.sendMessage(Text.of("Detection Failed! You are not in the crew of this craft."));
                        } else {

                            if (isSubcraft) {

                                if (parentCraft.getType() == craft.getType() || parentCraft.getHitBox().size() <= task.getHitBox().size()) {
                                    player.sendMessage(Text.of("Detection Failed. Craft is already being controlled by another player."));
                                    failed = true;

                                } else {

                                    // if this is a different type than the overlapping craft, and is smaller, this must be a child craft, like a fighter on a carrier
                                    if (!parentCraft.isNotProcessing()) {
                                        failed = true;
                                        player.sendMessage(Text.of("Parent Craft is busy."));
                                    }
                                    parentCraft.setHitBox(new HashHitBox(CollectionUtils.filter(parentCraft.getHitBox(), task.getHitBox())));
                                    parentCraft.setInitialSize(parentCraft.getInitialSize() - task.getHitBox().size());
                                }
                            }
                        }

                        if (craft.getType().getMustBeSubcraft() && !isSubcraft) {
                            failed = true;
                            if (player != null)
                                player.sendMessage(Text.of("Craft must be part of another craft!"));
                        }
                        if (!failed) {
                            craft.setInitialSize(task.getHitBox().size());
                            craft.setHitBox(task.getHitBox());

                            final int waterLine = craft.getWaterLine();
                            if(!craft.getType().blockedByWater() && craft.getHitBox().getMinY() <= waterLine){
                                for(Vector3i location : craft.getHitBox().boundingHitBox()){
                                    if(location.getY() <= waterLine){
                                        craft.getPhasedBlocks().add(BlockTypes.WATER.getDefaultState().snapshotFor(MovecraftLocation.toSponge(craft.getWorld(), location)));
                                    }
                                }
                            }

                            if (craft.getHitBox() != null) {

                                if (player != null) {
                                    player.sendMessage(Text.of("Successfully piloted " + craft.getType().getName() + " Size: " + craft.getHitBox().size()));
                                    Movecraft.getInstance().getLogger().info("New Craft Detected! Pilot: " + player.getName() + " CraftType: " + craft.getType().getName() + " Size: " + craft.getHitBox().size() + " Location: " + craft.getHitBox().getMidPoint().toString());
                                } else {
                                    Movecraft.getInstance().getLogger().info("New Craft Detected! Pilot: " + "NULL PLAYER" + " CraftType: " + craft.getType().getName() + " Size: " + craft.getHitBox().size() + " Location: " + craft.getHitBox().getMidPoint().toString());
                                }
                                CraftManager.getInstance().addCraft(craft);
                            } else {
                                Movecraft.getInstance().getLogger().info("Detection Failed - NULL Hitbox!");
                            }
                        }
                    }
                if(craft != null && craft.getHitBox() != null){
                    CraftDetectEvent event = new CraftDetectEvent(craft);
                    Sponge.getEventManager().post(event);
                }


            } else if (poll instanceof TranslationTask) {
                // Process translation task

                TranslationTask task = (TranslationTask) poll;
                Player pilot = Sponge.getServer().getPlayer(craft.getPilot()).orElse(null);

                // Check that the craft hasn't been sneakily unpiloted
                // if ( p != null ) { cruiseOnPilot crafts don't have player
                // pilots

                if (task.failed()) {
                    // The craft translation failed
                    if (pilot != null && craft.getState() != CraftState.SINKING)
                        pilot.sendMessage(Text.of(task.getFailMessage()));

                    if (task.isCollisionExplosion()) {
                        //craft.setHitBox(task.getNewHitBox());
                        MapUpdateManager.getInstance().scheduleUpdates(task.getUpdates());
                        sentMapUpdate = true;
                        CraftManager.getInstance().addReleaseTask(craft);

                    }
                } else {
                    // The craft is clear to move, perform the block updates
                    MapUpdateManager.getInstance().scheduleUpdates(task.getUpdates());

                    sentMapUpdate = true;
                }

            } else if (poll instanceof RotationTask) {

                // Process rotation task
                RotationTask task = (RotationTask) poll;
                Player pilot = Sponge.getServer().getPlayer(craft.getPilot()).orElse(null);

                // Check that the craft hasn't been sneakily unpiloted
                if (pilot != null || task.getIsSubCraft()) {

                    if (task.isFailed()) {

                        // The craft translation failed, don't try to notify them if there is no pilot
                        if (pilot != null)
                            pilot.sendMessage(Text.of(task.getFailMessage()));
                        else
                            Movecraft.getInstance().getLogger().info("NULL Player Rotation Failed: " + task.getFailMessage());
                    } else {

                        MapUpdateManager.getInstance().scheduleUpdates(task.getUpdates());

                        sentMapUpdate = true;

                        craft.setHitBox(task.getNewHitBox());
                    }
                }
            }

            // only mark the craft as having finished updating if you didn't
            // send any updates to the map updater. Otherwise the map updater
            // will mark the crafts once it is done with them.
            if (!sentMapUpdate) {
                craft.setProcessing(false);
            }
        }
    }

    private void processCruise() {
        for (Craft craft : CraftManager.getInstance()) {
            if (craft == null || !craft.isNotProcessing() || craft.getState() != CraftState.CRUISING) {
                continue;
            }
            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - craft.getLastCruiseUpdateTick();
            World w = craft.getWorld();
            // if the craft should go slower underwater, make time pass more slowly there
            //TODO: Replace w.getSeaLevel() with something better
            if (craft.getType().getHalfSpeedUnderwater() && craft.getHitBox().getMinY() < w.getSeaLevel())
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
                if (craft.getHitBox().getMinY() <= w.getSeaLevel()) {
                    dy = -1;
                }
            } else if (dive) {
                dy = 0 - ((craft.getType().getCruiseSkipBlocks() + 1) >> 1);
                if (craft.getHitBox().getMinY() <= w.getSeaLevel()) {
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
            craft.translate(Rotation.NONE, new com.flowpowered.math.vector.Vector3i(dx, dy, dz), false);
            craft.setLastMoveVector(new com.flowpowered.math.vector.Vector3i(dx, dy, dz));
            if (craft.getLastCruiseUpdateTick() != -1) {
                craft.setLastCruiseUpdateTick(Sponge.getServer().getRunningTimeTicks());
            } else {
                craft.setLastCruiseUpdateTick(Sponge.getServer().getRunningTimeTicks() - 600);
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
            long ticksElapsed = (Sponge.getServer().getRunningTimeTicks() - pcraft.getLastCheckTime()) / 50;

            if (ticksElapsed <= Settings.SinkCheckTicks) {
                continue;
            }
            final World w = pcraft.getWorld();
            int totalNonAirBlocks = 0;
            int totalNonAirWaterBlocks = 0;
            HashMap<List<BlockType>, Integer> foundFlyBlocks = new HashMap<>();
            HashMap<List<BlockType>, Integer> foundMoveBlocks = new HashMap<>();
            // go through each block in the blocklist, and
            // if its in the FlyBlocks, total up the number
            // of them
            for (Vector3i l : pcraft.getHitBox()) {
                BlockType blockType = w.getBlock(l.getX(), l.getY(), l.getZ()).getType();
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
            long ticksElapsed = Sponge.getServer().getRunningTimeTicks() - craft.getLastCruiseUpdateTick();
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
            craft.setLastCruiseUpdateTick(craft.getLastCruiseUpdateTick() != -1 ? Sponge.getServer().getRunningTimeTicks() : Sponge.getServer().getRunningTimeTicks() - 600);
        }
    }

    private void processTracers() {
        if (Settings.TracerRateTicks == 0)
            return;
        long ticksElapsed = (System.currentTimeMillis() - lastTracerUpdate) / 50;
        if (ticksElapsed > Settings.TracerRateTicks) {
            for (World world : Sponge.getServer().getWorlds()) {
                if (world != null) {
                    for (Entity entity : world.getEntities(entity -> entity.getType().equals(EntityTypes.PRIMED_TNT))) {
                        PrimedTNT tnt = (PrimedTNT) entity;

                        if (tnt.getVelocity().lengthSquared() > 0.25) {
                            for (Player p : world.getPlayers()) {
                                // is the TNT within the render distance of the player?
                                long maxDistSquared = world.getViewDistance() * 16;
                                maxDistSquared = maxDistSquared - 16;
                                maxDistSquared = maxDistSquared * maxDistSquared;

                                if (p.getLocation().getBlockPosition().distanceSquared(tnt.getLocation().getBlockPosition()) < maxDistSquared) {
                                    // we
                                    // use
                                    // squared
                                    // because
                                    // its
                                    // faster
                                    final com.flowpowered.math.vector.Vector3i loc = tnt.getLocation().getBlockPosition();
                                    final Player fp = p;
                                    // then make a cobweb to look like smoke,
                                    // place it a little later so it isn't right
                                    // in the middle of the volley
                                    Task.builder()
                                            .delayTicks(5)
                                            .execute( () -> fp.sendBlockChange(loc, BlockTypes.WEB.getDefaultState()))
                                            .submit(Movecraft.getInstance());

                                    // then remove it
                                    Task.builder()
                                            .delayTicks(65)
                                            .execute( () -> fp.resetBlockChange(loc))
                                            .submit(Movecraft.getInstance());
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
        long timeElapsed = System.currentTimeMillis() - lastFireballCheck;

        if (timeElapsed < 150)
            return;

        for (World world : Sponge.getServer().getWorlds()) {

            if (world == null || world.getPlayers().isEmpty())
                continue;

            for (Entity entity : world.getEntities(entity -> entity.getType().equals(EntityTypes.SMALL_FIREBALL))) {
                SmallFireball fireball = (SmallFireball) entity;

                if (!(fireball.getShooter() instanceof Dispenser) || FireballTracking.containsKey(fireball))
                    continue;

                Craft craft = fastNearestCraftToLoc(fireball.getLocation());

                if (craft == null || craft.getAADirector() == null)
                    continue;

                Player player = Sponge.getServer().getPlayer(craft.getAADirector()).orElse(null);

                if (player == null || !player.getItemInHand(HandTypes.MAIN_HAND).isPresent() || player.getItemInHand(HandTypes.MAIN_HAND).get().getType() != Settings.PilotTool)
                    continue;

                int distX = craft.getHitBox().getMinX() + craft.getHitBox().getMaxX();
                distX = distX >> 1;
                distX = Math.abs(distX - fireball.getLocation().getBlockX());
                int distY = craft.getHitBox().getMinY() + craft.getHitBox().getMaxY();
                distY = distY >> 1;
                distY = Math.abs(distY - fireball.getLocation().getBlockY());
                int distZ = craft.getHitBox().getMinZ() + craft.getHitBox().getMaxZ();
                distZ = distZ >> 1;
                distZ = Math.abs(distZ - fireball.getLocation().getBlockZ());
                boolean inRange = (distX < 50) && (distY < 50) && (distZ < 50);

                if (inRange) {
                    Vector3d fireballVelocity = fireball.getVelocity();
                    double speed = fireballVelocity.length(); // store the speed to add it back in later, since all the values we will be using are "normalized", IE: have a speed of 1
                    fireballVelocity = fireballVelocity.normalize(); // you normalize it for comparison with the new direction to see if we are trying to steer too far

                    BlockSnapshot targetBlock = null;
                    Optional<BlockRayHit<World>> blockRayHit = BlockRay
                            .from(player)
                            .distanceLimit((player.getViewDistance() + 1) * 16)
                            .skipFilter(hit -> transparent.contains(hit.getLocation().getBlockType()))
                            .stopFilter(BlockRay.allFilter())
                            .build()
                            .end();

                    if (blockRayHit.isPresent()) {
                        // Target is Block :)
                        targetBlock = blockRayHit.get().getLocation().createSnapshot();
                    }

                    Vector3d targetVector;
                    if (targetBlock == null) {

                        // the player is looking at nothing, shoot in that general direction
                        targetVector = player.getHeadRotation();

                    } else {

                        // shoot directly at the block the player is looking at (IE: with convergence)
                        targetVector = targetBlock.getLocation().get().getPosition().sub(fireball.getLocation().getPosition());
                        targetVector = targetVector.normalize();

                    }

                    if (targetVector.getX() - fireballVelocity.getX() > 0.5) {
                        fireballVelocity = fireballVelocity.add(0.5, 0, 0);
                    } else if (targetVector.getX() - fireballVelocity.getX() < -0.5) {
                        fireballVelocity = fireballVelocity.sub(0.5, 0, 0);
                    } else {
                        fireballVelocity = new Vector3d(targetVector.getX(), fireballVelocity.getY(), fireballVelocity.getZ());
                    }

                    if (targetVector.getY() - fireballVelocity.getY() > 0.5) {
                        fireballVelocity = fireballVelocity.add(0, 0.5, 0);
                    } else if (targetVector.getY() - fireballVelocity.getY() < -0.5) {
                        fireballVelocity = fireballVelocity.sub(0, 0.5, 0);
                    } else {
                        fireballVelocity = new Vector3d(fireballVelocity.getX(), targetVector.getY(), fireballVelocity.getZ());
                    }

                    if (targetVector.getZ() - fireballVelocity.getZ() > 0.5) {
                        fireballVelocity = fireballVelocity.add(0, 0, 0.5);
                    } else if (targetVector.getZ() - fireballVelocity.getZ() < -0.5) {
                        fireballVelocity = fireballVelocity.sub(0, 0, 0.5);
                    } else {
                        fireballVelocity = new Vector3d(fireballVelocity.getX(), fireballVelocity.getY(), targetVector.getZ());
                    }

                    fireballVelocity = fireballVelocity.mul(speed); // put the original speed back in, but now along a different trajectory

                    fireball.setVelocity(fireballVelocity);
                    fireball.offer(Keys.ACCELERATION, fireballVelocity);
                }

                //add fireball to tracking
                FireballTracking.put(fireball, System.currentTimeMillis());
            }
        }

        int timeLimit = 20 * Settings.FireballLifespan * 50;
        // then, removed any expired fireballs from tracking
        FireballTracking.keySet().removeIf(fireball -> {

            if (fireball == null)
                return true;

            if (System.currentTimeMillis() - FireballTracking.get(fireball) > timeLimit) {
                fireball.remove();
                return true;
            }

            return false;

        });

        lastFireballCheck = System.currentTimeMillis();
    }

    private Craft fastNearestCraftToLoc(Location loc) {
        Craft returnedCraft = null;
        long closestDistSquared = 1000000000L;
        Set<Craft> craftsList = CraftManager.getInstance().getCraftsInWorld((World) loc.getExtent());
        for (Craft craft : craftsList) {

            Vector3i hitBoxMidPoint = craft.getHitBox().getMidPoint();
            int distSquared = hitBoxMidPoint.distanceSquared(loc.getBlockPosition());

            if (distSquared < closestDistSquared) {
                closestDistSquared = distSquared;
                returnedCraft = craft;
            }
        }
        return returnedCraft;
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
                                        Player p = Sponge.getServer().getPlayer(c.getCannonDirector()).orElse(null);
                                        if (p != null && p.getItemInHand(HandTypes.MAIN_HAND).get().getType() == Settings.PilotTool) {
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
        processTracers();
        processFireballs();
        processTNTContactExplosives();
        processDetection();
        processAlgorithmQueue();

        // Cleanup crafts that are bugged and have not moved in the past 60 seconds, but have no crew or are still processing.
        for (Craft craft : CraftManager.getInstance()) {
            if (craft.getCrewList().isEmpty() && craft.getLastCruiseUpdateTick() < Sponge.getServer().getRunningTimeTicks() - 1200) {
                CraftManager.getInstance().forceRemoveCraft(craft);
            }

            // Stop crafts from moving if they have taken too long to process.
            if (!craft.isNotProcessing() && craft.getState() == CraftState.CRUISING && craft.getLastCruiseUpdateTick() < Sponge.getServer().getRunningTimeTicks() - 100) {

                    craft.setProcessing(false);
            }
        }
    }
}