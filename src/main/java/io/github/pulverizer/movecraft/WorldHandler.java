package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import io.github.pulverizer.movecraft.utils.MathUtils;
import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

public class WorldHandler {
    private static final net.minecraft.util.Rotation[] ROTATION;

    static {
        ROTATION = new net.minecraft.util.Rotation[3];
        ROTATION[Rotation.NONE.ordinal()] = net.minecraft.util.Rotation.NONE;
        ROTATION[Rotation.CLOCKWISE.ordinal()] = net.minecraft.util.Rotation.CLOCKWISE_90;
        ROTATION[Rotation.ANTICLOCKWISE.ordinal()] = net.minecraft.util.Rotation.COUNTERCLOCKWISE_90;
    }

    private static final NextTickProvider tickProvider = new NextTickProvider();

    public WorldHandler() {
    }

    /**
     * Moves the Entity to the Location in the World that the Entity currently resides in and applies the Rotation to the Entity.
     *
     * @param entity      Entity to be moved.
     * @param newLocation Vector3d location to move the Entity to.
     * @param rotation    New Rotation of the Entity.
     */
    public void moveEntity(Entity entity, Vector3d newLocation, float rotation) {
        boolean entityMoved = entity.setLocationAndRotation(new Location<>(entity.getWorld(), newLocation), entity.getRotation().add(0, rotation, 0));

        if (Settings.Debug && !entityMoved)
            Movecraft.getInstance().getLogger().info("Failed to move Entity of type: " + entity.getType().getName());
    }

    public void rotateCraft(Craft craft, Vector3i originPoint, Rotation rotation) {
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        HashMap<Vector3i, Vector3i> rotatedPositions = new HashMap<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        for (Vector3i newLocation : craft.getHitBox()) {
            rotatedPositions.put(MathUtils.rotateVec(counterRotation, newLocation.sub(originPoint)).add(originPoint), newLocation);
        }
        //*******************************************
        //*         Step two: Get the tiles         *
        //*******************************************
        World nativeWorld = craft.getWorld();
        HashSet<TileHolder> tiles = gatherTiles(nativeWorld, rotatedPositions.keySet());

        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks and rotate them
        Map<Vector3i, BlockState> blockMap = new HashMap<>();
        for (Vector3i position : rotatedPositions.keySet()) {
            blockMap.put(position, (BlockState) ((WorldServer) nativeWorld).getBlockState(locationToBlockPos(position)).withRotation(ROTATION[rotation.ordinal()]));
        }
        //create the new block
        blockMap.forEach((position, blockState) -> setBlockFast(nativeWorld, rotatedPositions.get(position), blockState));
        //*******************************************
        //*    Step four: replace all the tiles     *
        //*******************************************
        //TODO: go by chunks
        for (TileHolder tileHolder : tiles) {
            moveTileEntity(nativeWorld, rotatedPositions.get(tileHolder.getTilePosition()), tileHolder.getTile());
            if (tileHolder.getNextTick() == null)
                continue;
            final long currentTime = ((WorldServer) nativeWorld).getWorldTime();
            ((WorldServer) nativeWorld).scheduleBlockUpdate(locationToBlockPos(rotatedPositions.get(tileHolder.getTilePosition())), tileHolder.getTile().getBlockType(), (int) (tileHolder.getNextTick().scheduledTime - currentTime), tileHolder.getNextTick().priority);
        }

        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        //TODO: add support for pass-through
        Collection<Vector3i> deletePositions = CollectionUtils.filter(rotatedPositions.keySet(), rotatedPositions.values());
        for (Vector3i position : deletePositions) {
            setBlockFast(nativeWorld, position, BlockTypes.AIR.getDefaultState());
        }
        //*******************************************
        //*   Step six: Process fire spread         *
        //*******************************************
        processFireSpread(nativeWorld, blockMap.keySet());
    }

    public void translateCraft(Craft craft, Vector3i translateVector) {
        //TODO: Add support for rotations
        //A craftTranslateCommand should only occur if the craft is moving to a valid position
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        Set<Vector3i> positions = new HashSet<>(craft.getHitBox().asSet());
        //*******************************************
        //*         Step two: Get the tiles         *
        //*******************************************
        World nativeWorld = craft.getWorld();
        HashSet<TileHolder> tiles = gatherTiles(nativeWorld, positions);
        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks and translate the positions
        Map<Vector3i, BlockState> blockMap = new HashMap<>();
        positions.forEach(position -> blockMap.put(position.add(translateVector), nativeWorld.getBlock(position)));

        //create the new blocks
        blockMap.forEach((newPosition, blockState) -> setBlockFast(nativeWorld, newPosition, blockState));
        //*******************************************
        //*    Step four: replace all the tiles     *
        //*******************************************
        replaceTilesTranslate(nativeWorld, tiles, translateVector);
        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        Collection<Vector3i> deletePositions = CollectionUtils.filter(positions, blockMap.keySet());
        deletePositions.forEach(position -> setBlockFast(nativeWorld, position, BlockTypes.AIR.getDefaultState()));

        //*******************************************
        //*   Step six: Process fire spread         *
        //*******************************************
        processFireSpread(nativeWorld, blockMap.keySet());
    }

    private HashSet<TileHolder> gatherTiles(World world, Set<Vector3i> positions) {
        HashSet<TileHolder> tiles = new HashSet<>();

        //get the tiles
        positions.forEach(position -> {
            if (world.getBlockType(position) == BlockTypes.AIR)
                return;

            TileEntity tile = removeTileEntity(world, position);
            if (tile == null)
                return;
            //get the nextTick to move with the tile

            //world.getChunkAtWorldCoords(position).getTileEntities().remove(position);
            tiles.add(new TileHolder(tile, tickProvider.getNextTick((WorldServer) world, position), position));
        });

        return tiles;
    }

    //TODO Rewrite to handle translations AND rotations
    private void replaceTilesTranslate(World nativeWorld, Set<TileHolder> tiles, Vector3i translateVector) {
        //TODO: go by chunks
        tiles.forEach(tileHolder -> {
            moveTileEntity(nativeWorld, tileHolder.getTilePosition().add(translateVector), tileHolder.getTile());
            if (tileHolder.getNextTick() == null)
                return;
            final long currentTime = ((WorldServer) nativeWorld).getWorldTime();
            ((WorldServer) nativeWorld).scheduleBlockUpdate(tileHolder.getNextTick().position.add(locationToBlockPos(translateVector)), tileHolder.getTile().getBlockType(), (int) (tileHolder.getNextTick().scheduledTime - currentTime), tileHolder.getNextTick().priority);
        });
    }

    private void processFireSpread(World world, Set<Vector3i> positions) {
        positions.forEach(position -> {
            IBlockState type = ((WorldServer) world).getBlockState(locationToBlockPos(position));

            if (!(type.getBlock() instanceof BlockFire)) {
                return;
            }
            BlockFire fire = (BlockFire) type.getBlock();
            fire.randomTick((WorldServer) world, locationToBlockPos(position), type, ((WorldServer) world).rand);
        });
    }

    private TileEntity removeTileEntity(World world, Vector3i position) {
        WorldServer worldServer = (WorldServer) world;
        BlockPos blockPos = new BlockPos(position.getX(), position.getY(), position.getZ());

        TileEntity tile = null;

        // Optimized version of WorldServer#getTileEntity()
        if (worldServer.processingLoadedTiles) {
            for (TileEntity tileEntity : worldServer.addedTileEntityList) {
                if (!tileEntity.isInvalid() && tileEntity.getPos().equals(blockPos)) {
                    tile = tileEntity;
                    break;
                }
            }
        }

        if (tile == null) {
            tile = worldServer.getChunk(blockPos).getTileEntity(blockPos, Chunk.EnumCreateEntityType.IMMEDIATE);
        }

        if (tile == null) {
            for (TileEntity tileEntity : worldServer.addedTileEntityList) {
                if (!tileEntity.isInvalid() && tileEntity.getPos().equals(blockPos)) {
                    tile = tileEntity;
                    break;
                }
            }
        }
        // END

        if (tile == null) {
            return null;
        }

        //cleanup
        //Optimized version of WorldServer#removeTileEntity();
        tile.invalidate();
        worldServer.addedTileEntityList.remove(tile);
        worldServer.loadedTileEntityList.remove(tile);

        if (!worldServer.processingLoadedTiles) {
            worldServer.tickableTileEntities.remove(tile);

            worldServer.getChunk(blockPos).getTileEntityMap().remove(blockPos);
        }
        // END

        return tile;
    }

    public static BlockPos locationToBlockPos(Vector3i loc) {
        return new BlockPos(loc.getX(), loc.getY(), loc.getZ());
    }

    public void setBlockFast(World world, Vector3i position, BlockState blockState) {
        WorldServer worldServer = (WorldServer) world;
        BlockPos blockPos = locationToBlockPos(position);
        IBlockState iBlockState = (IBlockState) blockState;

        Chunk chunk = worldServer.getChunk(blockPos);
        ExtendedBlockStorage chunkSection = chunk.getBlockStorageArray()[position.getY() >> 4];
        if (chunkSection == null) {
            // Put a GLASS block to initialize the section. It will be replaced next with the real block.
            chunk.setBlockState(blockPos, Blocks.GLASS.getDefaultState());
            chunkSection = chunk.getBlockStorageArray()[position.getY() >> 4];
        }

        chunkSection.set(position.getX() & 15, position.getY() & 15, position.getZ() & 15, iBlockState);
        worldServer.notifyBlockUpdate(blockPos, iBlockState, iBlockState, 3);

        // Set the chunk to save
        // Should just need to mark dirty
        // chunk.setLastSaveTime(worldServer.getTotalWorldTime() - 600L);
        chunk.markDirty();
    }

    public void setBlockFast(Location<World> location, Rotation rotation, BlockState blockState) {
        //TODO Clean this up
        blockState = (BlockState) ((IBlockState) blockState).withRotation(ROTATION[rotation.ordinal()]);

        setBlockFast(location.getExtent(), location.getBlockPosition(), blockState);
    }

    /*public void disableShadow(BlockType type) {
        Method method;
        try {
            Block tempBlock = CraftMagicNumbers.getBlock(type.getId());
            method = Block.class.getDeclaredMethod("e", int.class);
            method.setAccessible(true);
            method.invoke(tempBlock, 0);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalArgumentException | IllegalAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }*/

    private void moveTileEntity(World world, Vector3i newPosition, TileEntity tile) {
        // Cast Sponge's World to Minecraft's WorldServer
        WorldServer worldServer = (WorldServer) world;
        // Convert Vector3i to Minecraft's BlockPos
        BlockPos blockPos = locationToBlockPos(newPosition);

        // Get the chunk
        Chunk chunk = worldServer.getChunk(blockPos);

        // Same as Spigot's invalidateBlockCache()
        tile.blockMetadata = -1;
        tile.blockType = null;
        // END

        // Optimised version of Chunk#addTileEntity(BlockPos, TileEntity)
        tile.setWorld(worldServer);
        tile.setPos(blockPos);
        tile.validate();

        chunk.getTileEntityMap().put(blockPos, tile);
        // END
    }

    private class TileHolder {
        private final TileEntity tile;
        private final NextTickListEntry nextTick;
        private final Vector3i tilePosition;

        public TileHolder(TileEntity tile, NextTickListEntry nextTick, Vector3i tilePosition) {
            this.tile = tile;
            this.nextTick = nextTick;
            this.tilePosition = tilePosition;
        }

        public TileEntity getTile() {
            return tile;
        }

        public NextTickListEntry getNextTick() {
            return nextTick;
        }

        public Vector3i getTilePosition() {
            return tilePosition;
        }
    }
}
