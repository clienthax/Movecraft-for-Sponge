package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.utils.MathUtils;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.property.block.MatterProperty;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.monster.Creeper;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.spongepowered.api.event.Order.FIRST;
import static org.spongepowered.api.event.Order.LAST;

public class BlockListener {

    private HashSet<PrimedTNT> tntControlList = new HashSet<>();
    private int tntControlTimer = 0;
    private long lastDamagesUpdate = 0;

    @Listener(order = LAST)
    public void onBlockBreak(ChangeBlockEvent.Break event, @Root Player player) {

        if (!Settings.ProtectPilotedCrafts)
            return;

        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            BlockSnapshot blockSnapshot = transaction.getOriginal();

            for (Craft craft : CraftManager.getInstance().getCraftsInWorld(blockSnapshot.getLocation().get().getExtent())) {

                if (craft == null || craft.getState() == CraftState.SINKING) {
                    continue;
                }

                if(craft.getHitBox().contains(blockSnapshot.getLocation().get().getBlockPosition())) {

                    transaction.setValid(false);

                    if (event.getCause().root() instanceof Player)
                        ((Player) event.getCause().root()).sendMessage(Text.of("BLOCK IS PART OF A PILOTED CRAFT"));
                }
            }
        }
    }

    // prevent water and lava from spreading on moving crafts
    //TODO: This doesn't actually seem to work.
    @Listener(order = FIRST)
    public void onBlockFromTo(ChangeBlockEvent.Pre event) {

        if (!(event.getSource() instanceof BlockSnapshot))
            return;

        BlockSnapshot block = (BlockSnapshot) event.getSource();

        if (!block.getLocation().isPresent())
            return;

        if (block.getState().getType() != BlockTypes.WATER && block.getState().getType() != BlockTypes.LAVA)
            return;

        for (Craft craft : CraftManager.getInstance().getCraftsInWorld(block.getLocation().get().getExtent())) {
            if ((!craft.isNotProcessing()) && MathUtils.locIsNearCraftFast(craft, block.getLocation().get().getBlockPosition())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // prevent pistons on cruising crafts
    /*@Listener(order = FIRST)
    public void onPistonEvent(BlockPistonExtendEvent event) {
        Block block = event.getBlock();
        CraftManager.getInstance().getCraftsInWorld(block.getWorld());
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(block.getWorld())) {
            Vector3i mloc = new Vector3i(block.getX(), block.getY(), block.getZ());
            if (MathUtils.locIsNearCraftFast(tcraft, mloc) && tcraft.getCruising() && !tcraft.isNotProcessing()) {
                event.setCancelled(true);
                return;
            }
        }
    }*/

    /*@Listener(order = LAST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // replace blocks with fire occasionally, to prevent fast crafts from simply ignoring fire
        if (!Settings.FireballPenetration || event.isCancelled() || event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) {
            return;
        }
        BlockSnapshot testBlock = event.getBlock().getRelative(-1, 0, 0);
        if (!testBlock.getType().isBurnable())
            testBlock = event.getBlock().getRelative(1, 0, 0);

        if (!testBlock.getType().isBurnable())
            testBlock = event.getBlock().getRelative(0, 0, -1);

        if (!testBlock.getType().isBurnable())
            testBlock = event.getBlock().getRelative(0, 0, 1);

        if (!testBlock.getType().isBurnable()) {
            return;
        }

        testBlock.setType(BlockTypes.AIR);
    }*/

    @Listener
    public void tntBlastCondenser(ExplosionEvent.Pre event) {

        if (!(event.getSource() instanceof PrimedTNT))
            return;

        if (tntControlTimer < Sponge.getServer().getRunningTimeTicks()) {
            tntControlTimer = Sponge.getServer().getRunningTimeTicks();
            tntControlList.clear();
        }

        PrimedTNT eventTNT = (PrimedTNT) event.getSource();
        Location<World> tntLoc = eventTNT.getLocation();

        if (tntControlList.contains(eventTNT)) {
            //event.setCancelled(true);
            //eventTNT.remove();
            return;
        }

        int tntFound = 1;

        for (Entity entity : event.getTargetWorld().getIntersectingEntities(new AABB(tntLoc.getPosition().sub(20, 20, 20), tntLoc.getPosition().add(20, 20, 20)))) {

            if (!(entity instanceof PrimedTNT))
                continue;

            PrimedTNT tnt = (PrimedTNT) entity;

            if (tnt.getFuseData().ticksRemaining().get() > eventTNT.getFuseData().ticksRemaining().get() + 1 || tnt.equals(eventTNT))
                continue;

            //tnt.remove();
            tntControlList.add(tnt);
            tntFound++;
        }

        float explosionPower = tntFound * 4;

        if (explosionPower > 4) {
            Movecraft.getInstance().getLogger().info("BOOM: " + explosionPower);

            Explosion explosion = Explosion.builder()
                    .from(event.getExplosion())
                    .radius(explosionPower)
                    .build();

            event.setExplosion(explosion);
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

        if (Settings.DurabilityOverride != null) {
            event.getAffectedLocations().removeIf(loc -> Settings.DurabilityOverride.containsKey(loc.getBlockType()) && new Random().nextInt(Settings.DurabilityOverride.get(loc.getBlockType())) > 1);
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