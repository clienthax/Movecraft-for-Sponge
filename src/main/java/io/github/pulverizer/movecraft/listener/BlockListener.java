package io.github.pulverizer.movecraft.listener;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.spongepowered.api.event.Order.FIRST;
import static org.spongepowered.api.event.Order.LAST;

public class BlockListener {

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

        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(block.getLocation().get().getExtent())) {
            if ((!tcraft.isNotProcessing()) && MathUtils.locIsNearCraftFast(tcraft, block.getLocation().get().getBlockPosition())) {
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



                    //TODO: Is broken in API. Flowing Water should not == BlockTypes.WATER
                    for (Location<World> testLoc : blockList) {

                        if (testLoc.getBlockType() == BlockTypes.WATER || testLoc.getBlockType() == BlockTypes.LAVA) {
                            //testLoc.restoreSnapshot(BlockSnapshot.builder().blockState(BlockTypes.AIR.getDefaultState()).position(testLoc.getBlockPosition()).world(testLoc.getExtent().getProperties()).build(), true, BlockChangeFlags.ALL);
                        //}

                        //if (testLoc.getBlockType() == BlockTypes.FLOWING_WATER || testLoc.getBlockType() == BlockTypes.FLOWING_LAVA) {
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
                        .delayTicks(65)
                        .execute(() -> finalisedPlayer.resetBlockChange(location.getBlockPosition()))
                        .submit(Movecraft.getInstance());
            }
        }
    }

}