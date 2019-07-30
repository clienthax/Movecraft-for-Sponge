package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.ScheduledBlockUpdate;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.*;
import io.github.pulverizer.movecraft.utils.MathUtils;

import java.util.*;

/**
 * An interface for all interactions with the World.
 */

public class WorldHandler {

    /**
     * Constructs an Instance of the WorldHandler.
     */
    public WorldHandler() {}

    /**
     * Moves the Entity to the Location in the World that the Entity currently resides in and applies the Rotation to the Entity.
     * @param entity Entity to be moved.
     * @param newLocation Vector3d location to move the Entity to.
     * @param rotation New Rotation of the Entity.
     */
    public void moveEntity(Entity entity, Vector3d newLocation, float rotation){
        boolean entityMoved = entity.setLocationAndRotation(new Location<>(entity.getWorld(), newLocation), entity.getRotation().add(0, rotation, 0));

        if (Settings.Debug && !entityMoved)
            Movecraft.getInstance().getLogger().info("Failed to move Entity of type: " + entity.getType().getName());
    }

    /**
     * Rotates the Craft around the Location using the Rotation.
     * @param craft Craft to be rotated.
     * @param originPoint Vector3i that the Craft will be rotated around.
     * @param rotation Rotation tha the Craft will be rotated by.
     */
    public void rotateCraft(Craft craft, Vector3i originPoint, Rotation rotation) {

        World nativeWorld = craft.getWorld();

        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        HashMap<Vector3i, Vector3i> rotatedBlockPositions = new HashMap<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        HashMap<Vector3i, HashMap<Integer, Integer>> updates = new HashMap<>();
        for(Vector3i newLocation : craft.getHitBox()){
            rotatedBlockPositions.put(MathUtils.rotateVec(counterRotation, newLocation.sub(originPoint)).add(originPoint), newLocation);
        }

        //get the old blocks and add them
        HashMap<Vector3i,BlockSnapshot> blockData = new HashMap<>();
        for(Vector3i blockPosition : rotatedBlockPositions.keySet()){
            blockData.put(blockPosition,nativeWorld.createSnapshot(blockPosition));

            HashMap<Integer, Integer> blockUpdates = new HashMap<>();
            nativeWorld.getLocation(blockPosition).getScheduledUpdates().forEach(update -> blockUpdates.put(update.getPriority(), update.getTicks()));

            updates.put(blockPosition, blockUpdates);
        }

        //remove the old blocks from the world
        for (Vector3i blockPosition : rotatedBlockPositions.keySet()) {
            nativeWorld.getLocation(blockPosition).getScheduledUpdates().forEach(update -> nativeWorld.getLocation(blockPosition).removeScheduledUpdate(update));
            nativeWorld.getLocation(blockPosition).getTileEntity().ifPresent(tileEntity -> tileEntity.setValid(false));
            setBlock(nativeWorld, blockPosition, BlockSnapshot.builder().blockState(BlockTypes.AIR.getDefaultState()).world(nativeWorld.getProperties()).position(blockPosition).build());
        }

        //create the new blocks
        for(Map.Entry<Vector3i,BlockSnapshot> entry : blockData.entrySet()) {
            setBlock(nativeWorld, rotatedBlockPositions.get(entry.getKey()), rotation, entry.getValue());
            updates.get(entry.getKey()).forEach((priority , ticks) -> nativeWorld.getLocation(rotatedBlockPositions.get(entry.getKey())).addScheduledUpdate(priority, ticks));
        }
    }

    /**
     * Moves the Craft using the Vector3i.
     * @param craft Craft to be moved.
     * @param translateBlockVector 3D direction that the Craft is to be moved in.
     * @param newHitBox New HitBox of the Craft after moving.
     */
    public void translateCraft(Craft craft, Vector3i translateBlockVector, HashHitBox newHitBox) {

        World nativeWorld = craft.getWorld();

        //A craftTranslateCommand should only occur if the craft is moving to a valid position
        //*******************************************
        //*          Convert to Positions           *
        //*******************************************
        List<Vector3i> blockPositions = new ArrayList<>();
        for(Vector3i vector3i : craft.getHitBox()) {
            blockPositions.add(vector3i);
        }

        //get the old blocks
        List<BlockSnapshot> blocks = new ArrayList<>();
        HashMap<Vector3i, HashMap<Integer, Integer>> updates = new HashMap<>();
        for(Vector3i blockPosition : blockPositions){
            blocks.add(nativeWorld.createSnapshot(blockPosition));
        }

        //add the blockPositions
        List<Vector3i> newBlockPositions = new ArrayList<>();
        for(Vector3i blockPosition : blockPositions){
            newBlockPositions.add(blockPosition.add(translateBlockVector));
        }

        //remove the old blocks from the world
        for (Vector3i blockPosition : blockPositions) {
            HashMap<Integer, Integer> blockUpdates = new HashMap<>();
            nativeWorld.getLocation(blockPosition).getScheduledUpdates().forEach(update -> blockUpdates.put(update.getPriority(), update.getTicks()));

            Vector3i position = blockPosition.add(translateBlockVector);
            updates.put(position, blockUpdates);

            nativeWorld.getLocation(blockPosition).getScheduledUpdates().forEach(update -> nativeWorld.getLocation(blockPosition).removeScheduledUpdate(update));
            nativeWorld.getLocation(blockPosition).getTileEntity().ifPresent(tileEntity -> tileEntity.setValid(false));
            //setBlock(nativeWorld, blockPosition, BlockTypes.AIR.getDefaultState().snapshotFor(nativeWorld.getLocation(position)));
        }

        //create the new blocks
        for(int i = 0; i < newBlockPositions.size(); i++) {
            setBlock(nativeWorld, newBlockPositions.get(i), blocks.get(i).withLocation(nativeWorld.getLocation(newBlockPositions.get(i))));
            int finalI = i;
            updates.get(newBlockPositions.get(i)).forEach((priority , ticks) -> nativeWorld.getLocation(newBlockPositions.get(finalI)).addScheduledUpdate(priority, ticks));
        }

        blockPositions.removeAll(newBlockPositions);
        blockPositions.forEach(position -> setBlock(nativeWorld, position, BlockTypes.AIR.getDefaultState().snapshotFor(nativeWorld.getLocation(position))));

        craft.setHitBox(newHitBox);

    }

    /**
     * Sets the Block at the Location in the World to the BlockSnapshot.
     * @param world World in which the block will be placed.
     * @param blockPosition Vector3i location at which the BlockSnapshot will be placed.
     * @param block BlockSnapshot to be placed.
     */
    private void setBlock(World world, Vector3i blockPosition, BlockSnapshot block) {

        world.getLocation(blockPosition).restoreSnapshot(block, true, BlockChangeFlags.NONE);
    }

    /**
     * Rotates the BlockSnapshot and then sets the Block at the Location in the World to the BlockSnapshot.
     * @param world World in which the block will be placed.
     * @param blockPosition Vector3i location at which the BlockSnapshot will be placed.
     * @param rotation Rotation that the block will be rotated with.
     * @param block BlockSnapshot to be placed.
     */
    private void setBlock(World world, Vector3i blockPosition, Rotation rotation, BlockSnapshot block) {
        BlockSnapshot rotatedBlock = rotateBlock(rotation, block);
        world.getLocation(blockPosition).restoreSnapshot(rotatedBlock, true, BlockChangeFlags.NONE);
    }

    /**
     * Sets the Block at the Location to the BlockSnapshot.
     * @param location Location at which the BlockSnapshot will be placed.
     * @param block BlockSnapshot to be placed.
     */
    public void setBlock(Location<World> location, BlockSnapshot block){
        location.restoreSnapshot(block, true, BlockChangeFlags.NONE);
    }

    /**
     * Rotates the BlockSnapshot and then sets the Block at the Location to the BlockSnapshot.
     * @param location Location at which the BlockSnapshot will be placed.
     * @param rotation Rotation that the block will be rotated with.
     * @param block BlockSnapshot to be placed.
     */
    public void setBlock(Location<World> location, Rotation rotation, BlockSnapshot block) {

        BlockSnapshot rotatedBlock = rotateBlock(rotation, block);
        location.restoreSnapshot(rotatedBlock, true, BlockChangeFlags.NONE);
    }

    /**
     * Rotates a BlockSnapshot using the Rotation.
     * @param rotation Rotation that the BlockSnapshot will be rotated with.
     * @param block BlockSnapshot to be rotated.
     * @return New rotated BlockSnapshot.
     */
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
        } catch (NoSuchMethodException | InvocationTargetException | IllegalArgumentException | IllegalAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }
    */
}