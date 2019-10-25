package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.util.Direction;
import io.github.pulverizer.movecraft.utils.MathUtils;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

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

        HashSet<Vector3i> oldLocations = new HashSet<>();
        HashMap<Vector3i, BlockSnapshot> blocks = new HashMap<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        LinkedHashMap<Vector3i, HashMap<Integer, Integer>> updates = new LinkedHashMap<>();

        //get blocks and updates from old locations
        for(Vector3i newPosition : craft.getHitBox()) {
            Vector3i oldPosition = MathUtils.rotateVec(counterRotation, newPosition.sub(originPoint)).add(originPoint);
            oldLocations.add(oldPosition);

            blocks.put(newPosition, nativeWorld.createSnapshot(oldPosition));

            HashMap<Integer, Integer> blockUpdates = new HashMap<>();
            nativeWorld.getScheduledUpdates(oldPosition).forEach(update -> blockUpdates.put(update.getPriority(), update.getTicks()));

            updates.put(newPosition, blockUpdates);

            nativeWorld.getScheduledUpdates(oldPosition).forEach(sbu -> nativeWorld.removeScheduledUpdate(oldPosition, sbu));
        }

        //create the new blocks
        for(Map.Entry<Vector3i, BlockSnapshot> entry : blocks.entrySet()) {
            Vector3i location = entry.getKey();
            final BlockSnapshot blockSnapshot = entry.getValue();
            nativeWorld.restoreSnapshot(rotateBlock(rotation, blockSnapshot).withLocation(new Location<World>(nativeWorld, location)), true, BlockChangeFlags.NONE);
            final HashMap<Integer, Integer> blockUpdates = updates.get(location);
            blockUpdates.forEach((key, value) -> nativeWorld.addScheduledUpdate(location, key, value));
            // Prune the replaced location from old locations
            oldLocations.remove(location);
        }

        //remove the old blocks
        oldLocations.forEach(location -> BlockSnapshot.NONE.withLocation(new Location<World>(nativeWorld, location)).restore(true, BlockChangeFlags.NONE));
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

        //get the old blocks
        HashSet<Vector3i> oldLocations = new HashSet<>();
        HashMap<Vector3i, BlockSnapshot> blocks = new HashMap<>();
        LinkedHashMap<Vector3i, HashMap<Integer, Integer>> updates = new LinkedHashMap<>();

        for(Vector3i blockPosition : craft.getHitBox()) {
            oldLocations.add(blockPosition);

            blocks.put(blockPosition.add(translateBlockVector), nativeWorld.createSnapshot(blockPosition));

            HashMap<Integer, Integer> blockUpdates = new HashMap<>();
            nativeWorld.getScheduledUpdates(blockPosition)
                    .forEach(update -> blockUpdates.put(update.getPriority(), update.getTicks()));

            updates.put(blockPosition.add(translateBlockVector), blockUpdates);

            nativeWorld.getScheduledUpdates(blockPosition).forEach(sbu -> {
                nativeWorld.removeScheduledUpdate(blockPosition, sbu);
            });
        }

        //create the new blocks
        for(Map.Entry<Vector3i, BlockSnapshot> entry : blocks.entrySet()) {
            Vector3i location = entry.getKey();
            final BlockSnapshot blockSnapshot = entry.getValue();
            blockSnapshot.withLocation(new Location<>(nativeWorld, location)).restore(true, BlockChangeFlags.NONE);
            final HashMap<Integer, Integer> blockUpdates = updates.get(location);
            blockUpdates.forEach((key, value) -> nativeWorld.addScheduledUpdate(location, key, value));
            // Prune the replaced location from old locations
            oldLocations.remove(location);
        }

        //remove the old blocks
        oldLocations.forEach(location -> BlockSnapshot.NONE.withLocation(new Location<World>(nativeWorld, location)).restore(true, BlockChangeFlags.NONE));

        craft.setHitBox(newHitBox);
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

        Direction oldBlockDirection = block.get(Keys.DIRECTION).get();
        Direction newBlockDirection = Direction.NONE;

        if (oldBlockDirection == Direction.DOWN || oldBlockDirection == Direction.UP || oldBlockDirection == Direction.NONE)
            return block;

        if (rotation == Rotation.CLOCKWISE) {
            switch (oldBlockDirection) {
                case NORTH:
                    newBlockDirection = Direction.EAST;
                    break;

                case NORTH_NORTHEAST:
                    newBlockDirection = Direction.EAST_SOUTHEAST;
                    break;

                case NORTHEAST:
                    newBlockDirection = Direction.SOUTHEAST;
                    break;

                case EAST_NORTHEAST:
                    newBlockDirection = Direction.SOUTH_SOUTHEAST;
                    break;

                case EAST:
                    newBlockDirection = Direction.SOUTH;
                    break;

                case EAST_SOUTHEAST:
                    newBlockDirection = Direction.SOUTH_SOUTHWEST;
                    break;

                case SOUTHEAST:
                    newBlockDirection = Direction.SOUTHWEST;
                    break;

                case SOUTH_SOUTHEAST:
                    newBlockDirection = Direction.WEST_SOUTHWEST;
                    break;

                case SOUTH:
                    newBlockDirection = Direction.WEST;
                    break;

                case SOUTH_SOUTHWEST:
                    newBlockDirection = Direction.WEST_NORTHWEST;
                    break;

                case SOUTHWEST:
                    newBlockDirection = Direction.NORTHWEST;
                    break;

                case WEST_SOUTHWEST:
                    newBlockDirection = Direction.NORTH_NORTHWEST;
                    break;

                case WEST:
                    newBlockDirection = Direction.NORTH;
                    break;

                case WEST_NORTHWEST:
                    newBlockDirection = Direction.NORTH_NORTHEAST;
                    break;

                case NORTHWEST:
                    newBlockDirection = Direction.NORTHEAST;
                    break;

                case NORTH_NORTHWEST:
                    newBlockDirection = Direction.EAST_NORTHEAST;
                    break;

                default:
                    newBlockDirection = oldBlockDirection;
            }

        } else if (rotation == Rotation.ANTICLOCKWISE) {
            switch (oldBlockDirection) {

                case NORTH:
                    newBlockDirection = Direction.WEST;
                    break;

                case NORTH_NORTHWEST:
                    newBlockDirection = Direction.WEST_SOUTHWEST;
                    break;

                case NORTHWEST:
                    newBlockDirection = Direction.SOUTHWEST;
                    break;

                case WEST_NORTHWEST:
                    newBlockDirection = Direction.SOUTH_SOUTHWEST;
                    break;

                case WEST:
                    newBlockDirection = Direction.SOUTH;
                    break;

                case WEST_SOUTHWEST:
                    newBlockDirection = Direction.SOUTH_SOUTHEAST;
                    break;

                case SOUTHWEST:
                    newBlockDirection = Direction.SOUTHEAST;
                    break;

                case SOUTH_SOUTHWEST:
                    newBlockDirection = Direction.EAST_SOUTHEAST;
                    break;

                case SOUTH:
                    newBlockDirection = Direction.EAST;
                    break;

                case SOUTH_SOUTHEAST:
                    newBlockDirection = Direction.EAST_NORTHEAST;
                    break;

                case SOUTHEAST:
                    newBlockDirection = Direction.NORTHEAST;
                    break;

                case EAST_SOUTHEAST:
                    newBlockDirection = Direction.NORTH_NORTHEAST;
                    break;

                case EAST:
                    newBlockDirection = Direction.NORTH;
                    break;

                case EAST_NORTHEAST:
                    newBlockDirection = Direction.NORTH_NORTHWEST;
                    break;

                case NORTHEAST:
                    newBlockDirection = Direction.NORTHWEST;
                    break;

                case NORTH_NORTHEAST:
                    newBlockDirection = Direction.WEST_NORTHWEST;
                    break;

                default:
                    newBlockDirection = oldBlockDirection;
            }
        }

            if (newBlockDirection == Direction.NONE)
                return block;

            return block.with(Keys.DIRECTION, newBlockDirection).get();
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