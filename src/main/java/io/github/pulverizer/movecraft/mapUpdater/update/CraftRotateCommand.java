package io.github.pulverizer.movecraft.mapUpdater.update;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.*;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.event.SignTranslateEvent;
import io.github.pulverizer.movecraft.utils.*;
import io.github.pulverizer.movecraft.world.ChunkDataManager;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;

import java.util.*;



public class CraftRotateCommand extends UpdateCommand {
    private final Craft craft;
    private final Rotation rotation;
    private final Vector3i originLocation;

    public CraftRotateCommand(final Craft craft, final Vector3i originLocation, final Rotation rotation) {
        this.craft = craft;
        this.rotation = rotation;
        this.originLocation = originLocation;
    }

    @Override
    public void doUpdate() {
        long time = System.currentTimeMillis();

        final Logger logger = Movecraft.getInstance().getLogger();
        if (craft.getHitBox().isEmpty()) {
            logger.warn("Attempted to move craft with empty HashHitBox!");
            craft.release(null);
            return;
        }

        final Set<BlockType> passthroughBlocks = new HashSet<>(craft.getType().getPassthroughBlocks());
        if(craft.isSinking()){
            passthroughBlocks.add(BlockTypes.WATER);
            passthroughBlocks.add(BlockTypes.FLOWING_WATER);
            passthroughBlocks.add(BlockTypes.LEAVES);
            passthroughBlocks.add(BlockTypes.LEAVES2);
            passthroughBlocks.add(BlockTypes.GRASS);
            passthroughBlocks.add(BlockTypes.TALLGRASS);
            passthroughBlocks.add(BlockTypes.DOUBLE_PLANT);
        }

        if (!passthroughBlocks.isEmpty()) {
            MutableHitBox originalLocations = new HashHitBox();
            final Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
            for (Vector3i vector3i : craft.getHitBox()) {
                originalLocations.add(MathUtils.rotateVec(counterRotation, vector3i.sub(originLocation)).add(originLocation));
            }

            final HitBox to = CollectionUtils.filter(craft.getHitBox(), originalLocations);

            for (Vector3i location : to) {
                BlockSnapshot material = craft.getWorld().createSnapshot(location);
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                }
            }
            //The subtraction of the set of coordinates in the HitBox cube and the HitBox itself
            final HitBox invertedHitBox = CollectionUtils.filter(craft.getHitBox().boundingHitBox(), craft.getHitBox());
            //A set of locations that are confirmed to be "exterior" locations
            final MutableHitBox exterior = new HashHitBox();
            final MutableHitBox interior = new HashHitBox();

            //place phased blocks
            final int minX = craft.getHitBox().getMinX();
            final int maxX = craft.getHitBox().getMaxX();
            final int minY = craft.getHitBox().getMinY();
            final int maxY = craft.getHitBox().getMaxY();
            final int minZ = craft.getHitBox().getMinZ();
            final int maxZ = craft.getHitBox().getMaxZ();
            final HitBox[] surfaces = {
                    new SolidHitBox(new Vector3i(minX, minY, minZ), new Vector3i(minX, maxY, maxZ)),
                    new SolidHitBox(new Vector3i(minX, minY, minZ), new Vector3i(maxX, minY, maxZ)),
                    new SolidHitBox(new Vector3i(minX, minY, minZ), new Vector3i(maxX, maxY, minZ)),
                    new SolidHitBox(new Vector3i(maxX, maxY, maxZ), new Vector3i(minX, maxY, maxZ)),
                    new SolidHitBox(new Vector3i(maxX, maxY, maxZ), new Vector3i(maxX, minY, maxZ)),
                    new SolidHitBox(new Vector3i(maxX, maxY, maxZ), new Vector3i(maxX, maxY, minZ))};
            //Valid exterior starts as the 6 surface planes of the HitBox with the locations that lie in the HitBox removed
            final Set<Vector3i> validExterior = new HashSet<>();
            for (HitBox hitBox : surfaces) {
                validExterior.addAll(CollectionUtils.filter(hitBox, craft.getHitBox()).asSet());
            }
            //Check to see which locations in the from set are actually outside of the craft
            for (Vector3i location :validExterior ) {
                if (craft.getHitBox().contains(location) || exterior.contains(location)) {
                    continue;
                }
                //use a modified BFS for multiple origin elements
                Set<Vector3i> visited = new HashSet<>();
                Queue<Vector3i> queue = new LinkedList<>();
                queue.add(location);
                while (!queue.isEmpty()) {
                    Vector3i node = queue.poll();
                    //If the node is already a valid member of the exterior of the HitBox, continued search is unitary.
                    for (Vector3i neighbor : CollectionUtils.neighbors(invertedHitBox, node)) {
                        if (visited.contains(neighbor)) {
                            continue;
                        }
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
                exterior.addAll(visited);
            }
            interior.addAll(CollectionUtils.filter(invertedHitBox, exterior));

            for (Vector3i location : CollectionUtils.filter(invertedHitBox, exterior)) {
                BlockSnapshot block = craft.getWorld().createSnapshot(location);
                if (!passthroughBlocks.contains(block.getState().getType())) {
                    continue;
                }
                craft.getPhasedBlocks().add(block);
            }

            //add the craft
            rotateCraft();

            //trigger sign event
            for (Vector3i location : craft.getHitBox()) {
                BlockType blockType = craft.getWorld().getBlockType(location);
                if (blockType == BlockTypes.WALL_SIGN || blockType == BlockTypes.STANDING_SIGN) {
                    Sponge.getEventManager().post(new SignTranslateEvent(location, craft));
                }
            }

            //place confirmed blocks if they have been un-phased
            craft.getPhasedBlocks().forEach(block -> {
                if (exterior.contains(block.getPosition())) {

                    craft.getWorld().restoreSnapshot(block, true, BlockChangeFlags.NONE);
                    craft.getPhasedBlocks().remove(block);
                }

                if (originalLocations.contains(block.getPosition()) && !craft.getHitBox().inBounds(block.getPosition())) {

                    craft.getWorld().restoreSnapshot(block, true, BlockChangeFlags.NONE);
                    craft.getPhasedBlocks().remove(block);
                }
            });

            for (Vector3i location : interior) {
                final BlockSnapshot material = craft.getWorld().createSnapshot(location);
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                    craft.getWorld().restoreSnapshot(location, BlockTypes.AIR.getDefaultState().snapshotFor(new Location<>(craft.getWorld(), location)), true, BlockChangeFlags.NONE);

                }
            }
        }else{
            //add the craft
            rotateCraft();
            //trigger sign event
            for (Vector3i location : craft.getHitBox()) {
                BlockType blockType = craft.getWorld().getBlockType(location);
                if (blockType == BlockTypes.WALL_SIGN || blockType == BlockTypes.STANDING_SIGN) {
                    Sponge.getEventManager().post(new SignTranslateEvent(location, craft));
                }
            }
        }

        if (craft.isCruising()) {
            if (rotation == Rotation.ANTICLOCKWISE) {

                switch (getCraft().getHorizontalCruiseDirection()) {
                    case NORTH:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.WEST);
                        break;

                    case SOUTH:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.EAST);
                        break;

                    case EAST:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.NORTH);
                        break;

                    case WEST:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.SOUTH);
                        break;
                }
            } else if (rotation == Rotation.CLOCKWISE) {

                switch (getCraft().getHorizontalCruiseDirection()) {
                    case NORTH:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.EAST);
                        break;

                    case SOUTH:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.WEST);
                        break;

                    case EAST:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.SOUTH);
                        break;

                    case WEST:
                        getCraft().setCruising(craft.getVerticalCruiseDirection(), Direction.NORTH);
                        break;
                }
            }
        }

        time = System.currentTimeMillis() - time;
        craft.addMoveTime(time);
        craft.updateLastMoveTick();
        craft.setProcessing(false);

        if (Settings.Debug) logger.info("Total time: " + time + " ms. Moving with cooldown of " + craft.getTickCooldown() + ". Speed of: " + String.format("%.2f", craft.getActualSpeed()));

        // Should not be counted in processing time
        craft.updateSubcraftWithParentRotation(originLocation, rotation);
    }

    private void rotateCraft() {
        //TODO - hitbox should not be getting set before this method is called!

        // Get old positions
        HashSet<Vector3i> oldPositions = new HashSet<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        for (Vector3i newLocation : craft.getHitBox()) {
            oldPositions.add(MathUtils.rotateVec(counterRotation, newLocation.sub(originLocation)).add(originLocation));
        }

        // Set up the chunk data manager
        ChunkDataManager chunkDataManager = new ChunkDataManager(craft.getWorld(), oldPositions);

        // Get the tiles
        chunkDataManager.fetchTilesAndRotate(rotation, originLocation);

        // Get the blocks and rotate them
        chunkDataManager.fetchBlocksAndRotate(rotation, originLocation);

        // Create the new blocks
        chunkDataManager.setBlocks();

        // Place the tile entities
        chunkDataManager.placeTiles();

        // Destroy the leftovers
        chunkDataManager.destroyLeftovers();

        //Process fire spread
        chunkDataManager.processFireSpread();
    }

    public Craft getCraft() {
        return craft;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CraftRotateCommand)) {
            return false;
        }
        CraftRotateCommand other = (CraftRotateCommand) obj;
        return other.craft.equals(this.craft) &&
                other.rotation == this.rotation &&
                other.originLocation.equals(this.originLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(craft, rotation, originLocation);
    }
}
