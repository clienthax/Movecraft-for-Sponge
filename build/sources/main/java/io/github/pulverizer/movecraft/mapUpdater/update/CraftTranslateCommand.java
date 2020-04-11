package io.github.pulverizer.movecraft.mapUpdater.update;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.WorldHandler;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.SignTranslateEvent;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import io.github.pulverizer.movecraft.utils.HitBox;
import io.github.pulverizer.movecraft.utils.MutableHitBox;
import io.github.pulverizer.movecraft.utils.SolidHitBox;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class CraftTranslateCommand extends UpdateCommand {
    private final Craft craft;
    private final Vector3i displacement;
    private final HashHitBox newHitBox;
    private final World world;
    private final Vector3i min;
    private final Vector3i max;


    public CraftTranslateCommand(Craft craft, Vector3i displacement, HashHitBox newHitBox){
        this.craft = craft;
        this.displacement = displacement;
        this.newHitBox = newHitBox;
        this.world = craft.getWorld();

        // Calculate min
        int minX = craft.getHitBox().getMinX();
        int minY = craft.getHitBox().getMinY();
        int minZ = craft.getHitBox().getMinZ();

        if (displacement.getX() < 0) {
            minX += displacement.getX();
        }
        if (displacement.getY() < 0) {
            minY += displacement.getY();
        }
        if (displacement.getZ() < 0) {
            minZ += displacement.getZ();
        }
        this.min = new Vector3i(minX, minY, minZ);

        // Calculate max
        int maxX = craft.getHitBox().getMaxX();
        int maxY = craft.getHitBox().getMaxY();
        int maxZ = craft.getHitBox().getMaxZ();

        if (displacement.getX() > 0) {
            maxX += displacement.getX();
        }
        if (displacement.getY() > 0) {
            maxY += displacement.getY();
        }
        if (displacement.getZ() > 0) {
            maxZ += displacement.getZ();
        }
        this.max = new Vector3i(maxX, maxY, maxZ);
    }

    @Override
    public void doUpdate() {
        long time = System.currentTimeMillis();
        //long timeTaken = 0;

        final Logger logger = Movecraft.getInstance().getLogger();
        if(craft.getHitBox().isEmpty()){
            logger.warn("Attempted to move craft with empty HashHitBox!");
            CraftManager.getInstance().removeCraft(craft);
            return;
        }

        final Set<BlockType> passthroughBlocks = new HashSet<>(craft.getType().getPassthroughBlocks());
        if(craft.getState() == CraftState.SINKING){
            passthroughBlocks.add(BlockTypes.WATER);
            passthroughBlocks.add(BlockTypes.FLOWING_WATER);
            passthroughBlocks.add(BlockTypes.LEAVES);
            passthroughBlocks.add(BlockTypes.LEAVES2);
            passthroughBlocks.add(BlockTypes.GRASS);
            passthroughBlocks.add(BlockTypes.TALLGRASS);
            passthroughBlocks.add(BlockTypes.DOUBLE_PLANT);
        }

        if(passthroughBlocks.isEmpty()){

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 1A: " + timeTaken + " ms");

            //add the craft
            //OLD Movecraft.getInstance().getWorldHandler().translateCraft(craft, min, max, displacement);
            Movecraft.getInstance().getWorldHandler().translateCraft(craft, displacement);

            // update the craft hitbox
            craft.setHitBox(newHitBox);
            craft.setLastMoveVector(displacement);

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 2A: " + timeTaken + " ms");

            //trigger sign event
            for (Vector3i location : craft.getHitBox()) {
                if (world.getBlockType(location) == BlockTypes.WALL_SIGN || world.getBlockType(location) == BlockTypes.STANDING_SIGN) {
                    Sponge.getEventManager().post(new SignTranslateEvent(location, craft));
                }
            }


        } else {

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 1B: " + timeTaken + " ms");

            MutableHitBox originalLocations = new HashHitBox();
            for (Vector3i vector3i : craft.getHitBox()) {
                originalLocations.add((vector3i).sub(displacement));
            }

            final HitBox to = CollectionUtils.filter(craft.getHitBox(), originalLocations);
            for (Vector3i location : to) {
                BlockSnapshot material = craft.getWorld().createSnapshot(location);
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                }
            }

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 2B: " + timeTaken + " ms");

            //place phased blocks
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

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 3B: " + timeTaken + " ms");

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

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 4B: " + timeTaken + " ms");

            interior.addAll(CollectionUtils.filter(invertedHitBox, exterior));

            final WorldHandler handler = Movecraft.getInstance().getWorldHandler();
            for (Vector3i location : CollectionUtils.filter(invertedHitBox, exterior)) {
                BlockSnapshot material = craft.getWorld().createSnapshot(location);
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                }
            }

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 5B: " + timeTaken + " ms");

            //add the craft
            //OLD handler.translateCraft(craft, min, max, displacement, newHitBox);
            handler.translateCraft(craft, displacement);

            // update the craft hitbox
            craft.setHitBox(newHitBox);
            craft.setLastMoveVector(displacement);

            //trigger sign event
            for (Vector3i location : craft.getHitBox()) {
                BlockSnapshot block = craft.getWorld().createSnapshot(location);
                if (block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN) {
                    Sponge.getEventManager().post(new SignTranslateEvent(block.getPosition(), craft));
                }
            }

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 6B: " + timeTaken + " ms");

            //place confirmed blocks if they have been un-phased
            for (BlockSnapshot block : craft.getPhasedBlocks()) {

                if (exterior.contains(new Vector3i(block.getPosition().getX(), block.getPosition().getY(), block.getPosition().getZ()))) {

                    craft.getWorld().restoreSnapshot(block, true, BlockChangeFlags.NONE);
                    craft.getPhasedBlocks().remove(block);
                }

                if (originalLocations.contains(new Vector3i(block.getPosition().getX(), block.getPosition().getY(), block.getPosition().getZ())) && !craft.getHitBox().inBounds(new Vector3i(block.getPosition().getX(), block.getPosition().getY(), block.getPosition().getZ()))) {

                    craft.getWorld().restoreSnapshot(block, true, BlockChangeFlags.NONE);
                    craft.getPhasedBlocks().remove(block);
                }
            }

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 7B: " + timeTaken + " ms");

            for (Vector3i location : interior) {
                final BlockSnapshot material = craft.getWorld().createSnapshot(location);
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                    craft.getWorld().restoreSnapshot(location, BlockTypes.AIR.getDefaultState().snapshotFor(new Location<>(craft.getWorld(), location)), true, BlockChangeFlags.NONE);

                }
            }

            //timeTaken = System.currentTimeMillis() - time;
            //logger.info("Marker 8B: " + timeTaken + " ms");
        }

        if(Settings.Debug)
            time = System.currentTimeMillis() - time;
            logger.info("Total time: " + time + " ms. Moving with cooldown of " + craft.getTickCooldown() + ". Speed of: " + String.format("%.2f", craft.getSpeed()));
        craft.addMoveTime(time);
        craft.setLastMoveTick(Sponge.getServer().getRunningTimeTicks());
        craft.setProcessing(false);
    }

    public Craft getCraft(){
        return craft;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof CraftTranslateCommand)){
            return false;
        }
        CraftTranslateCommand other = (CraftTranslateCommand) obj;
        return other.craft.equals(this.craft) &&
                other.displacement.equals(this.displacement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(craft, displacement);
    }
}
