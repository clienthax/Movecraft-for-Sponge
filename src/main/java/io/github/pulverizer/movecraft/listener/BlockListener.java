package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.explosive.fireball.SmallFireball;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;

import static org.spongepowered.api.event.Order.FIRST;
import static org.spongepowered.api.event.Order.LAST;

public class BlockListener {

    private long lastDamagesUpdate = 0;

    @Listener(order = LAST)
    public void onBlockBreak(ChangeBlockEvent.Break event) {

        if (event.getCause().root() instanceof Movecraft || event.getCause().root() instanceof Explosion)
            return;

        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            BlockSnapshot blockSnapshot = transaction.getOriginal();

            MovecraftLocation mloc = MathUtils.sponge2MovecraftLoc(blockSnapshot.getLocation().get());
            World blockWorld = blockSnapshot.getLocation().get().getExtent();
            for (Craft craft : CraftManager.getInstance().getCraftsInWorld(blockWorld)) {

                if (craft == null || craft.getDisabled()) {
                    continue;
                }

                for (MovecraftLocation tloc : craft.getHitBox()) {
                    if (tloc.equals(mloc)) {

                        Movecraft.getInstance().getLogger().info(event.getCause().toString());

                        transaction.setValid(false);

                        if (!Settings.ProtectPilotedCrafts && event.getCause().root() instanceof Player) {

                            Player player = (Player) event.getCause().root();
                            player.sendMessage(Text.of("BLOCK IS PART OF A PILOTED CRAFT"));
                        }

                        break;
                    }
                }
            }
        }
    }

    // prevent water and lava from spreading on moving crafts
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
            if ((!tcraft.isNotProcessing()) && MathUtils.locIsNearCraftFast(tcraft, MathUtils.sponge2MovecraftLoc(block.getLocation().get()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /*
    // prevent pistons on cruising crafts
    @Listener(order = FIRST)
    public void onPistonEvent(BlockPistonExtendEvent event) {
        Block block = event.getBlock();
        CraftManager.getInstance().getCraftsInWorld(block.getWorld());
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(block.getWorld())) {
            MovecraftLocation mloc = new MovecraftLocation(block.getX(), block.getY(), block.getZ());
            if (MathUtils.locIsNearCraftFast(tcraft, mloc) && tcraft.getCruising() && !tcraft.isNotProcessing()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // prevent hoppers on cruising crafts
    @Listener(order = FIRST)
    public void onHopperEvent(InventoryMoveItemEvent event) {
        if (!(event.getSource().getHolder() instanceof Hopper)) {
            return;
        }
        Hopper block = (Hopper) event.getSource().getHolder();
        CraftManager.getInstance().getCraftsInWorld(block.getWorld());
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(block.getWorld())) {
            MovecraftLocation mloc = new MovecraftLocation(block.getX(), block.getY(), block.getZ());
            if (MathUtils.locIsNearCraftFast(tcraft, mloc) && tcraft.getCruising() && !tcraft.isNotProcessing()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Listener(order = LAST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // replace blocks with fire occasionally, to prevent fast crafts from simply ignoring fire
        if (!Settings.FireballPenetration ||
                event.isCancelled() ||
                event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) {
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
    }

    @Listener(order = FIRST)
    public void onBlockDispense(BlockDispenseEvent e) {
        CraftManager.getInstance().getCraftsInWorld(e.getBlock().getWorld());
        for (Craft craft : CraftManager.getInstance().getCraftsInWorld(e.getBlock().getWorld())) {
            if (craft != null &&
                    !craft.isNotProcessing() &&
                    MathUtils.locIsNearCraftFast(craft, MathUtils.sponge2MovecraftLoc(e.getBlock().getLocation()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @Listener
    public void explodeEvent(EntityExplodeEvent e) {
        // Remove any blocks from the list that were adjacent to water, to prevent spillage
        if (!Settings.DisableSpillProtection) {
            e.blockList().removeIf(b -> b.getY() > b.getWorld().getSeaLevel() &&
                    (b.getRelative(-1, 0, -1).isLiquid() ||
                            b.getRelative(-1, 1, -1).isLiquid() ||
                            b.getRelative(-1, 0, 0).isLiquid() ||
                            b.getRelative(-1, 1, 0).isLiquid() ||
                            b.getRelative(-1, 0, 1).isLiquid() ||
                            b.getRelative(-1, 1, 1).isLiquid() ||
                            b.getRelative(0, 0, -1).isLiquid() ||
                            b.getRelative(0, 1, -1).isLiquid() ||
                            b.getRelative(0, 0, 0).isLiquid() ||
                            b.getRelative(0, 1, 0).isLiquid() ||
                            b.getRelative(0, 0, 1).isLiquid() ||
                            b.getRelative(0, 1, 1).isLiquid() ||
                            b.getRelative(1, 0, -1).isLiquid() ||
                            b.getRelative(1, 1, -1).isLiquid() ||
                            b.getRelative(1, 0, 0).isLiquid() ||
                            b.getRelative(1, 1, 0).isLiquid() ||
                            b.getRelative(1, 0, 1).isLiquid() ||
                            b.getRelative(1, 1, 1).isLiquid()));
        }

        if (Settings.DurabilityOverride != null) {
            e.blockList().removeIf(b -> Settings.DurabilityOverride.containsKey(b.getTypeId()) &&
                    (new Random(b.getX() + b.getY() + b.getZ() + (System.currentTimeMillis() >> 12)))
                            .nextInt(100) < Settings.DurabilityOverride.get(b.getTypeId()));
        }
        if (e.getEntity() == null)
            return;
        for (Player p : e.getEntity().getWorld().getPlayers()) {
            Entity tnt = e.getEntity();

            if (e.getEntityType() == EntityTypes.PRIMED_TNT && Settings.TracerRateTicks != 0) {
                long minDistSquared = 60 * 60;
                long maxDistSquared = Sponge.getServer().getViewDistance() * 16;
                maxDistSquared = maxDistSquared - 16;
                maxDistSquared = maxDistSquared * maxDistSquared;
                // is the TNT within the view distance (rendered world) of the player, yet further than 60 blocks?
                if (p.getLocation().distanceSquared(tnt.getLocation()) < maxDistSquared && p.getLocation().distanceSquared(tnt.getLocation()) >= minDistSquared) {  // we use squared because its faster
                    final Location loc = tnt.getLocation();
                    final Player fp = p;
                    final World fw = e.getEntity().getWorld();

                    // then make a glowstone to look like the explosion, place it a little later so it isn't right in the middle of the volley
                    Task.builder()
                            .delayTicks(5)
                            .execute(() -> fp.sendBlockChange(loc.getBlockPosition(), BlockTypes.GLOWSTONE.getDefaultState()))
                            .submit(Movecraft.getInstance());

                    // then remove it
                    Task.builder()
                            .delayTicks(160)
                            .execute(() -> fp.sendBlockChange(loc.getBlockPosition(), BlockTypes.AIR.getDefaultState()))
                            .submit(Movecraft.getInstance());
                }
            }
        }
    }
    */
}