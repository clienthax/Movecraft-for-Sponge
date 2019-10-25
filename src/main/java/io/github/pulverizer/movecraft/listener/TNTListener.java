package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.property.block.MatterProperty;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static org.spongepowered.api.event.Order.LAST;

public class TNTListener {

    private HashSet<PrimedTNT> tntControlList = new HashSet<>();
    private int tntControlTimer = 0;

    @Listener
    public void tntTracker(MoveEntityEvent event, @Getter("getTargetEntity") Entity entity) {

        if (!(entity instanceof PrimedTNT))
            return;



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
            if (tnt.getFuseData().ticksRemaining().get() > eventTNT.getFuseData().ticksRemaining().get() + 1 || tnt.getFuseData().ticksRemaining().get() < eventTNT.getFuseData().ticksRemaining().get() || tnt.equals(eventTNT))
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
