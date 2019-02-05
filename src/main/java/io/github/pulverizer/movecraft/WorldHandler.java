package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import org.spongepowered.api.world.World;

import java.util.*;

public class WorldHandler {

    public WorldHandler() {}

    public void moveEntity(Entity entity, Vector3d newLocation, float yaw){
        boolean entityMoved = entity.setLocationAndRotation(new Location<>(entity.getWorld(), newLocation), entity.getRotation().add(0, yaw, 0));

        if (Settings.Debug && entityMoved)
            Movecraft.getInstance().getLogger().info("Moved Entity of type: " + entity.getType().getName());
        else if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Failed to move Entity of type: " + entity.getType().getName());
    }

    public void rotateCraft(Craft craft, MovecraftLocation originPoint, Rotation rotation) {

        World nativeWorld = craft.getW();

        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        HashMap<Vector3i,Vector3i> rotatedBlockPositions = new HashMap<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        for(MovecraftLocation newLocation : craft.getHitBox()){
            rotatedBlockPositions.put(MathUtils.rotateVec(counterRotation, newLocation.subtract(originPoint)).add(originPoint), newLocation);
        }

        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks and rotate them
        HashMap<Vector3i,BlockSnapshot> blockData = new HashMap<>();
        for(Vector3i blockPosition : rotatedBlockPositions.keySet()){
            blockData.put(blockPosition,nativeWorld.createSnapshot(blockPosition));
        }
        //create the new block
        for(Map.Entry<Vector3i,BlockSnapshot> entry : blockData.entrySet()) {
            setBlockFast(nativeWorld, rotatedBlockPositions.get(entry.getKey()), rotation, entry.getValue());
        }

        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        //TODO: add support for pass-through
        Collection<Vector3i> deleteBlockPositions =  CollectionUtils.filter(rotatedBlockPositions.keySet(),rotatedBlockPositions.values());
        for(Vector3i blockPosition : deleteBlockPositions){
            setBlockFast(nativeWorld, blockPosition, BlockSnapshot.builder().blockState(BlockTypes.AIR.getDefaultState()).world(nativeWorld.getProperties()).position(blockPosition).build());
        }
        //*******************************************
        //*       Step seven: Send to players       *
        //*******************************************
        List<Chunk> chunks = new ArrayList<>();
        for(Vector3i blockPosition : rotatedBlockPositions.values()) {
            nativeWorld.getChunkAtBlock(blockPosition).ifPresent(
                    chunk -> {
                        if (!chunks.contains(chunk)) {
                            chunks.add(chunk);
                        }
                    }
            );
        }
        for(Vector3i blockPosition : deleteBlockPositions){
            nativeWorld.getChunkAtBlock(blockPosition).ifPresent(
                    chunk -> {
                        if (!chunks.contains(chunk)) {
                            chunks.add(chunk);
                        }
                    }
            );
        }
    }

    public void translateCraft(Craft craft, Vector3i translateBlockVector, HashHitBox newHitBox) {
        //TODO: Add support for rotations

        World nativeWorld = craft.getW();

        //A craftTranslateCommand should only occur if the craft is moving to a valid position
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        List<Vector3i> blockPositions = new ArrayList<>();
        for(MovecraftLocation movecraftLocation : craft.getHitBox()) {
            blockPositions.add(movecraftLocation);
        }

        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks
        List<BlockSnapshot> blocks = new ArrayList<>();
        for(Vector3i blockPosition : blockPositions){
            blocks.add(nativeWorld.createSnapshot(blockPosition));
        }
        //translate the blockPositions
        List<Vector3i> newBlockPositions = new ArrayList<>();
        for(Vector3i blockPosition : blockPositions){
            newBlockPositions.add(blockPosition.add(translateBlockVector));
        }

        //create the new block
        for(int i = 0; i<newBlockPositions.size(); i++) {
            setBlockFast(nativeWorld, newBlockPositions.get(i), blocks.get(i));
        }
        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        Collection<Vector3i> deleteBlockPositions =  CollectionUtils.filter(blockPositions, newBlockPositions);
        for(Vector3i blockPosition : deleteBlockPositions){
            setBlockFast(nativeWorld, blockPosition, BlockSnapshot.builder().blockState(BlockTypes.AIR.getDefaultState()).world(nativeWorld.getProperties()).position(blockPosition).build());
        }

        //*******************************************
        //*       Step seven: Send to players       *
        //*******************************************
        List<Chunk> chunks = new ArrayList<>();
        for(Vector3i blockPosition : newBlockPositions) {
            nativeWorld.getChunkAtBlock(blockPosition).ifPresent(
                    chunk -> {
                        if (!chunks.contains(chunk)) {
                            chunks.add(chunk);
                        }
                    }
            );
        }
        for(Vector3i blockPosition : deleteBlockPositions){
            nativeWorld.getChunkAtBlock(blockPosition).ifPresent(
                    chunk -> {
                        if (!chunks.contains(chunk)) {
                            chunks.add(chunk);
                        }
                    }
            );
        }
        //sendToPlayers(chunks.toArray(new Chunk[0]));

        craft.setHitBox(newHitBox);
    }

    private void setBlockFast(World world, Vector3i blockPosition, BlockSnapshot block) {

        if (!world.getChunkAtBlock(blockPosition).isPresent()) {

            Movecraft.getInstance().getLogger().error("Error 404: Chunk not Found");
            return;
        }

        Chunk chunk = world.getChunkAtBlock(blockPosition).get();
        chunk.loadChunk(true);
        chunk.getLocation(blockPosition).restoreSnapshot(block, true, BlockChangeFlags.NONE);

    }
    private void setBlockFast(World world, Vector3i blockPosition, Rotation rotation, BlockSnapshot block) {

        BlockSnapshot rotatedBlock = rotateBlock(rotation, block);

        if (!world.getChunkAtBlock(blockPosition).isPresent()) {

            Movecraft.getInstance().getLogger().error("Error 404: Chunk not Found");
            return;
        }

        Chunk chunk = world.getChunkAtBlock(blockPosition).get();
        chunk.loadChunk(true);
        chunk.getLocation(blockPosition).restoreSnapshot(rotatedBlock, true, BlockChangeFlags.NONE);

    }

    public void setBlockFast(Location<World> location, BlockSnapshot block){
        location.restoreSnapshot(block, true, BlockChangeFlags.NONE);
    }

    public void setBlockFast(Location<World> location, Rotation rotation, BlockSnapshot block) {

        BlockSnapshot rotatedBlock = rotateBlock(rotation, block);
        location.restoreSnapshot(rotatedBlock, true, BlockChangeFlags.NONE);
    }

    public BlockSnapshot rotateBlock(Rotation rotation, BlockSnapshot block) {

        if (rotation == Rotation.NONE || !block.supports(Keys.DIRECTION) || !block.get(Keys.DIRECTION).isPresent())
            return block;

        BlockSnapshot rotatedBlock = block;
        Direction oldBlockDirection = block.get(Keys.DIRECTION).get();
        Direction newBlockDirection = Direction.NONE;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.NORTH)
            newBlockDirection = Direction.EAST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.NORTH_NORTHEAST)
            newBlockDirection = Direction.EAST_SOUTHEAST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.NORTHEAST)
            newBlockDirection = Direction.SOUTHEAST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.EAST_NORTHEAST)
            newBlockDirection = Direction.SOUTH_SOUTHEAST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.EAST)
            newBlockDirection = Direction.SOUTH;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.EAST_SOUTHEAST)
            newBlockDirection = Direction.SOUTH_SOUTHWEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.SOUTHEAST)
            newBlockDirection = Direction.SOUTHWEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.SOUTH_SOUTHEAST)
            newBlockDirection = Direction.WEST_SOUTHWEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.SOUTH)
            newBlockDirection = Direction.WEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.SOUTH_SOUTHWEST)
            newBlockDirection = Direction.WEST_NORTHWEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.SOUTHWEST)
            newBlockDirection = Direction.NORTHWEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.WEST_SOUTHWEST)
            newBlockDirection = Direction.NORTH_NORTHWEST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.WEST)
            newBlockDirection = Direction.NORTH;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.WEST_NORTHWEST)
            newBlockDirection = Direction.NORTH_NORTHEAST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.NORTHWEST)
            newBlockDirection = Direction.NORTHEAST;

        if (rotation == Rotation.CLOCKWISE && oldBlockDirection == Direction.NORTH_NORTHWEST)
            newBlockDirection = Direction.EAST_NORTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.NORTH)
            newBlockDirection = Direction.WEST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.NORTH_NORTHWEST)
            newBlockDirection = Direction.WEST_SOUTHWEST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.NORTHWEST)
            newBlockDirection = Direction.SOUTHWEST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.WEST_NORTHWEST)
            newBlockDirection = Direction.SOUTH_SOUTHWEST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.WEST)
            newBlockDirection = Direction.SOUTH;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.WEST_SOUTHWEST)
            newBlockDirection = Direction.SOUTH_SOUTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.SOUTHWEST)
            newBlockDirection = Direction.SOUTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.SOUTH_SOUTHWEST)
            newBlockDirection = Direction.EAST_SOUTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.SOUTH)
            newBlockDirection = Direction.EAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.SOUTH_SOUTHEAST)
            newBlockDirection = Direction.EAST_NORTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.SOUTHEAST)
            newBlockDirection = Direction.NORTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.EAST_SOUTHEAST)
            newBlockDirection = Direction.NORTH_NORTHEAST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.EAST)
            newBlockDirection = Direction.NORTH;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.EAST_NORTHEAST)
            newBlockDirection = Direction.NORTH_NORTHWEST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.NORTHEAST)
            newBlockDirection = Direction.NORTHWEST;

        if (rotation == Rotation.ANTICLOCKWISE && oldBlockDirection == Direction.NORTH_NORTHEAST)
            newBlockDirection = Direction.WEST_NORTHWEST;

        if (newBlockDirection == Direction.NONE)
            return block;

        rotatedBlock = rotatedBlock.with(Keys.DIRECTION, newBlockDirection).get();

        return rotatedBlock;
    }

    /* Temp Disabled
    public void disableShadow(BlockType blockType) {
        Method method;
        try {
            Block tempBlock = CraftMagicNumbers.getBlock(blockType.getId());
            method = Block.class.getDeclaredMethod("e", int.class);
            method.setAccessible(true);
            method.invoke(tempBlock, 0);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalArgumentException | IllegalAccessException | SecurityException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }
    */

    private static MovecraftLocation sponge2MovecraftLoc(Location<World> worldLocation) {
        return new MovecraftLocation(worldLocation.getBlockX(), worldLocation.getBlockY(), worldLocation.getBlockZ());
    }
}