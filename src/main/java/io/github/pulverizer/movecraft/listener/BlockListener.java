package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.data.ChangeDataHolderEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

import static org.spongepowered.api.event.Order.FIRST;
import static org.spongepowered.api.event.Order.LAST;

public class BlockListener {

    final BlockType[] fragileBlocks = new BlockType[]{BlockTypes.BED, BlockTypes.PISTON_HEAD, BlockTypes.PISTON_EXTENSION, BlockTypes.TORCH, BlockTypes.REDSTONE_WIRE, BlockTypes.STANDING_SIGN, BlockTypes.WOODEN_DOOR, BlockTypes.LADDER, BlockTypes.WALL_SIGN, BlockTypes.LEVER, BlockTypes.STONE_PRESSURE_PLATE, BlockTypes.IRON_DOOR, BlockTypes.WOODEN_PRESSURE_PLATE, BlockTypes.UNLIT_REDSTONE_TORCH, BlockTypes.REDSTONE_TORCH, BlockTypes.STONE_BUTTON, BlockTypes.TRAPDOOR, BlockTypes.TRIPWIRE_HOOK, BlockTypes.TRIPWIRE, BlockTypes.WOODEN_BUTTON, BlockTypes.LIGHT_WEIGHTED_PRESSURE_PLATE, BlockTypes.HEAVY_WEIGHTED_PRESSURE_PLATE, BlockTypes.DAYLIGHT_DETECTOR, BlockTypes.DAYLIGHT_DETECTOR_INVERTED, BlockTypes.CARPET, BlockTypes.UNPOWERED_REPEATER, BlockTypes.POWERED_REPEATER, BlockTypes.UNPOWERED_COMPARATOR, BlockTypes.UNPOWERED_COMPARATOR};
    private long lastDamagesUpdate = 0;

    @Listener(order = FIRST)
    public void onBlockBreak(final ChangeBlockEvent.Break e, @Root Player player) {
        if (e.isCancelled()) {
            return;
        }
        List<Transaction<BlockSnapshot>> blocks = e.filter(bt -> bt.getBlockType() == BlockTypes.WALL_SIGN);
        for (Transaction<BlockSnapshot> transaction : blocks) {
            BlockSnapshot block = transaction.getOriginal();
            if (Settings.ProtectPilotedCrafts) {
                MovecraftLocation mloc = MathUtils.sponge2MovecraftLoc(block.getLocation().get());
                World blockWorld = block.getLocation().get().getExtent();
                CraftManager.getInstance().getCraftsInWorld(blockWorld);
                for (Craft craft : CraftManager.getInstance().getCraftsInWorld(blockWorld)) {
                    if (craft == null || craft.getDisabled()) {
                        continue;
                    }
                    for (MovecraftLocation tloc : craft.getHitBox()) {
                        if (tloc.equals(mloc)) {
                                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("BLOCK IS PART OF A PILOTED CRAFT")));
                            e.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    // prevent items from dropping from moving crafts
    @Listener(order = FIRST)
    public void onItemSpawn(final SpawnEntityEvent event) {

        List<Entity> entities = new ArrayList<>();
        entities.addAll(event.filterEntities(entity -> entity instanceof Item));

        if (entities.isEmpty())
            return;

        for (Entity entity : entities) {
            for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(entity.getWorld())) {
                if ((!tcraft.isNotProcessing()) && MathUtils.locationInHitbox(tcraft.getHitBox(), entity.getLocation())) {
                    event.setCancelled(true);
                    return;
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

    /* TODO: RE-ADD NEXT!!!!
    Keys.POWER.registerEvent(BlockState.class, event -> {});
    // process certain redstone on cruising crafts
    @Listener(order = FIRST)
    public void onRedstoneEvent(ChangeDataHolderEvent.ValueChange event) {
        if (event.getTargetHolder() == Keys.POWER)
            return;

        BlockSnapshot block = event.getBlock();
        CraftManager.getInstance().getCraftsInWorld(block.getWorld());
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(block.getWorld())) {
            MovecraftLocation mloc = new MovecraftLocation(block.getX(), block.getY(), block.getZ());
            if (MathUtils.locIsNearCraftFast(tcraft, mloc) &&
                    tcraft.getCruising() &&
                    (block.getTypeId() == 29 ||
                    block.getTypeId() == 33 ||
                    block.getTypeId() == 23 &&
                    !tcraft.isNotProcessing())) {
                event.setNewCurrent(event.getOldCurrent()); // don't allow piston movement on cruising crafts
                return;
            }
        }
    }

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

    // prevent fragile items from dropping on cruising crafts
    @Listener(order = FIRST)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();

        final int[] fragileBlocks = new int[]{26, 34, 50, 55, 63, 64, 65, 68, 69, 70, 71, 72, 75, 76, 77, 93, 94, 96, 131, 132, 143, 147, 148, 149, 150, 151, 171, 193, 194, 195, 196, 197};
        CraftManager.getInstance().getCraftsInWorld(block.getWorld());
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(block.getWorld())) {
            MovecraftLocation mloc = new MovecraftLocation(block.getX(), block.getY(), block.getZ());
            if (!MathUtils.locIsNearCraftFast(tcraft, mloc)) {
                continue;
            }
            if (Arrays.binarySearch(fragileBlocks, block.getTypeId()) >= 0) {
                MaterialData m = block.getState().getData();
                BlockFace face = BlockFace.DOWN;
                boolean faceAlwaysDown = false;
                if (block.getTypeId() == 149 || block.getTypeId() == 150 || block.getTypeId() == 93 || block.getTypeId() == 94)
                    faceAlwaysDown = true;
                if (m instanceof Attachable && !faceAlwaysDown) {
                    face = ((Attachable) m).getAttachedFace();
                }
                if (!event.getBlock().getRelative(face).getType().isSolid()) {
//						if(event.getEventName().equals("BlockPhysicsEvent")) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @Listener(order = LAST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // replace blocks with fire occasionally, to prevent fast craft from simply ignoring fire
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
                    MathUtils.locIsNearCraftFast(craft, MathUtils.bukkit2MovecraftLoc(e.getBlock().getLocation()))) {
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