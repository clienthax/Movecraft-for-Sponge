package io.github.pulverizer.movecraft.listener;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.async.AsyncManager;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.property.block.MatterProperty;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.blockray.BlockRay;
import org.spongepowered.api.util.blockray.BlockRayHit;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;

import java.util.*;

import static org.spongepowered.api.event.Order.LAST;

public class TNTListener {

    private final HashMap<PrimedTNT, Double> TNTTracking = new HashMap<>();
    private final HashMap<PrimedTNT, Integer> TNTTracers = new HashMap<>();
    private HashSet<PrimedTNT> tntControlList = new HashSet<>();
    private int tntControlTimer = 0;

    @Listener
    public void tntTracking(MoveEntityEvent event, @Getter("getTargetEntity") PrimedTNT primedTNT) {

        double velocity = primedTNT.getVelocity().lengthSquared();

        //Cannon Directors
        if (velocity > 0.25 && !TNTTracers.containsKey(primedTNT)) {
            Craft c = CraftManager.getInstance().fastNearestCraftToLoc(primedTNT.getLocation());

            if (c != null) {
                Vector3i midpoint = c.getHitBox().getMidPoint();
                int distX = Math.abs(midpoint.getX() - primedTNT.getLocation().getBlockX());
                int distY = Math.abs(midpoint.getY() - primedTNT.getLocation().getBlockY());
                int distZ = Math.abs(midpoint.getZ() - primedTNT.getLocation().getBlockZ());

                if (c.getCannonDirector() != null || distX <= 100 || distY <= 100 || distZ <= 100) {
                    Player player = Sponge.getServer().getPlayer(c.getCannonDirector()).orElse(null);

                    if (player != null && player.getItemInHand(HandTypes.MAIN_HAND).get().getType() == Settings.PilotTool) {

                        Vector3d tntVelocity = primedTNT.getVelocity();
                        double speed = tntVelocity.length(); // store the speed to add it back in later, since all the values we will be using are "normalized", IE: have a speed of 1
                        tntVelocity = tntVelocity.normalize(); // you normalize it for comparison with the new direction to see if we are trying to steer too far
                        BlockSnapshot targetBlock = null;
                        Optional<BlockRayHit<World>> blockRayHit = BlockRay
                                .from(player)
                                .distanceLimit((player.getViewDistance() + 1) * 16)
                                .skipFilter(hit -> CraftManager.getInstance().getTransparentBlocks().contains(hit.getLocation().getBlockType()))
                                .stopFilter(BlockRay.allFilter())
                                .build()
                                .end();

                        if (blockRayHit.isPresent())
                            // Target is Block :)
                            targetBlock = blockRayHit.get().getLocation().createSnapshot();

                        Vector3d targetVector;
                        if (targetBlock == null) { // the player is looking at nothing, shoot in that general direction
                            targetVector = player.getHeadRotation();
                        } else { // shoot directly at the block the player is looking at (IE: with convergence)
                            targetVector = targetBlock.getLocation().get().getPosition().sub(primedTNT.getLocation().getPosition());
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
                        tntVelocity = new Vector3d(tntVelocity.getX(), primedTNT.getVelocity().getY(), tntVelocity.getZ()); // you leave the original Y (or vertical axis) trajectory as it was
                        primedTNT.setVelocity(tntVelocity);
                    }
                }
            }
        }

        //TNT Tracers
        if (Settings.TracerRateTicks != 0) {
            if (!TNTTracers.containsKey(primedTNT) && velocity > 0.25) {
                TNTTracers.put(primedTNT, Sponge.getServer().getRunningTimeTicks() -  (int) Settings.TracerRateTicks);
            }

            if (TNTTracers.containsKey(primedTNT) && TNTTracers.get(primedTNT) < Sponge.getServer().getRunningTimeTicks() -  (int) Settings.TracerRateTicks) {

                for (Player player : primedTNT.getWorld().getPlayers()) {
                    // is the TNT within the render distance of the player?
                    long maxDistSquared = player.getViewDistance() * 16;
                    maxDistSquared = maxDistSquared - 16;
                    maxDistSquared = maxDistSquared * maxDistSquared;

                    if (player.getLocation().getBlockPosition().distanceSquared(primedTNT.getLocation().getBlockPosition()) < maxDistSquared) {
                        // we
                        // use
                        // squared
                        // because
                        // its
                        // faster
                        final Vector3i loc = primedTNT.getLocation().getBlockPosition();
                        final Player fp = player;
                        // then make a cobweb to look like smoke,
                        // place it a little later so it isn't right
                        // in the middle of the volley
                        Task.builder()
                                .delayTicks(5)
                                .execute(() -> fp.sendBlockChange(loc, BlockTypes.WEB.getDefaultState()))
                                .submit(Movecraft.getInstance());

                        // then remove it
                        Task.builder()
                                .delayTicks(65)
                                .execute(() -> fp.resetBlockChange(loc))
                                .submit(Movecraft.getInstance());
                    }
                }
            }
        }

        //Contact Explosives
        if (!TNTTracking.containsKey(primedTNT) && velocity > 0.35) {
            TNTTracking.put(primedTNT, velocity);

        } else if (TNTTracking.containsKey(primedTNT)) {
            if (velocity < TNTTracking.get(primedTNT) / 10) {
                primedTNT.detonate();
                TNTTracking.remove(primedTNT);
                TNTTracers.remove(primedTNT);

            } else {
                TNTTracking.put(primedTNT, velocity);
            }
        }

        //Clean up any exploded TNT from Tracking
        TNTTracking.keySet().removeIf(tnt -> tnt.getFuseData().ticksRemaining().get() <= 0);
        TNTTracers.keySet().removeIf(tnt -> tnt.getFuseData().ticksRemaining().get() <= 0);
    }

    @Listener
    public void tntBlastCondenser(ExplosionEvent.Pre event) {

        if (!event.getExplosion().getSourceExplosive().isPresent() || !(event.getExplosion().getSourceExplosive().get() instanceof PrimedTNT))
            return;

        if (tntControlTimer < Sponge.getServer().getRunningTimeTicks()) {
            tntControlTimer = Sponge.getServer().getRunningTimeTicks();
            tntControlList.clear();
        }

        PrimedTNT eventTNT = (PrimedTNT) event.getExplosion().getSourceExplosive().get();
        Location<World> tntLoc = eventTNT.getLocation();

        if (tntControlList.contains(eventTNT)) {
            event.setCancelled(true);
            eventTNT.remove();
            return;
        }

        int tntFound = 1;

        //TODO: Seems to only be finding the first 8 TNT Entities when using World#getIntersectingEntities(AABB)?
        Collection<Entity> entities = event.getTargetWorld().getNearbyEntities(tntLoc.getPosition(), 3);

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Entity Count: " + entities.size());

        for (Entity entity : entities) {

            if (!(entity instanceof PrimedTNT))
                continue;

            PrimedTNT tnt = (PrimedTNT) entity;

            //TODO: Testing this - scatters were OP if all shells landed together
            if (tnt.getFuseData().ticksRemaining().get() > eventTNT.getFuseData().ticksRemaining().get() + 1 || tnt.equals(eventTNT))
                continue;

            tnt.remove();
            tntControlList.add(tnt);
            tntFound++;

            //30 breaks the water block it's in and has a large AoE, going to max out at 16.
            if (tntFound >= 16)
                break;
        }

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("BOOM: " + tntFound);

        //TODO: Waiting on Explosion Settings PR on SpongeCommon
        Explosion explosion = Explosion.builder()
                .from(event.getExplosion())
                .radius(tntFound)
                .knockback(tntFound)
                .randomness(0)
                .resolution(tntFound * 2)
                .build();

        event.setExplosion(explosion);
    }

    //TODO: Waiting on PR
    @Listener (order = LAST)
    public void changeBlockResistance(ExplosionEvent.BlockExplosionResistance event) {

        if (event.getDefaultExplosionResistance() != event.getExplosionResistance())
            Movecraft.getInstance().getLogger().warn("Another plugin is altering the blast resistance of " + event.getBlockState().getType() + " and has been overwritten!");

        if (Settings.DurabilityOverride != null && Settings.DurabilityOverride.containsKey(event.getBlockState().getType())) {
            event.setExplosionResistance(Settings.DurabilityOverride.get(event.getBlockState().getType()));
        }

        //TODO: Add to craft config
        for (Craft craft : CraftManager.getInstance().getCraftsInWorld(event.getTargetWorld())) {
            if (craft.getHitBox().contains(event.getBlockPosition().toInt())) {
                final float newResistance = event.getExplosionResistance() * (1F + ((float) craft.getSize() / 50000F));
                Movecraft.getInstance().getLogger().info(String.valueOf((float) craft.getSize() / 50000F));
                Movecraft.getInstance().getLogger().info(String.valueOf(newResistance));
                event.setExplosionResistance(newResistance);
                break;
            }
        }
    }

    @Listener
    public void explodeEvent(ExplosionEvent.Detonate event) {

        // Remove any blocks from the list that were adjacent to water, to prevent spillage
        if (!Settings.DisableSpillProtection) {

            List<Location<World>> affectedLocations = new ArrayList<>(event.getAffectedLocations());

            for (Location<World> affectedLocation : affectedLocations) {

                for (Craft craft : CraftManager.getInstance().getCraftsInWorld(affectedLocation.getExtent())) {

                    if (craft == null || !craft.getHitBox().contains(affectedLocation.getBlockX(), affectedLocation.getBlockY(), affectedLocation.getBlockZ()))
                        continue;

                    HashSet<Location<World>> blockList = new HashSet<>();

                    Location<World> relativeBlockPos = affectedLocation.getBlockRelative(Direction.NORTH);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.WEST);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.EAST);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.SOUTH);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.UP);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.UP).getBlockRelative(Direction.NORTH);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.UP).getBlockRelative(Direction.WEST);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.UP).getBlockRelative(Direction.EAST);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);

                    relativeBlockPos = affectedLocation.getBlockRelative(Direction.UP).getBlockRelative(Direction.SOUTH);

                    if (craft.getHitBox().contains(relativeBlockPos.getBlockPosition()))
                        blockList.add(relativeBlockPos);



                    //TODO: Can't seem to get Fluid Level???
                    for (Location<World> testLoc : blockList) {

                        if (testLoc.getProperty(MatterProperty.class).get().getValue() == MatterProperty.Matter.LIQUID && testLoc.getBlock().get(Keys.FLUID_LEVEL).isPresent()) {// && testLoc.get(Keys.FLUID_LEVEL).get() == 1) {
                            Movecraft.getInstance().getLogger().info("Fluid Level: " + testLoc.getBlock().get(Keys.FLUID_LEVEL).get());
                            testLoc.restoreSnapshot(BlockTypes.AIR.getDefaultState().snapshotFor(testLoc), true, BlockChangeFlags.ALL);
                        }

                        if (testLoc.getProperty(MatterProperty.class).isPresent() && testLoc.getProperty(MatterProperty.class).get().getValue() == MatterProperty.Matter.LIQUID) {
                            event.getAffectedLocations().remove(affectedLocation);
                        }
                    }
                }
            }
        }

        if (!event.getExplosion().getSourceExplosive().isPresent() || !(event.getExplosion().getSourceExplosive().get() instanceof PrimedTNT) || Settings.TracerRateTicks == 0)
            return;

        Location<World> explosionLocation = event.getExplosion().getLocation();

        for (Player player : event.getTargetWorld().getPlayers()) {

            double renderDistance = (player.getViewDistance() + 1) * 16;

            // is the TNT within the view distance (rendered world) of the player, yet further than 60 blocks?
            if (player.getPosition().distance(explosionLocation.getPosition()) < renderDistance) {
                final Location location = explosionLocation;
                final Player finalisedPlayer = player;

                // make a glowstone to look like the explosion, place it a little later so it isn't right in the middle of the volley
                Task.builder()
                        .delayTicks(5)
                        .execute(() -> finalisedPlayer.sendBlockChange(location.getBlockPosition(), BlockTypes.GLOWSTONE.getDefaultState()))
                        .submit(Movecraft.getInstance());

                // then remove it
                Task.builder()
                        .delayTicks(105)
                        .execute(() -> finalisedPlayer.resetBlockChange(location.getBlockPosition()))
                        .submit(Movecraft.getInstance());
            }
        }
    }
}
