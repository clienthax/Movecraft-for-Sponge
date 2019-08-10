package io.github.pulverizer.movecraft.mapUpdater.update;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class CraftTranslateCommand extends UpdateCommand {
    private final Craft craft;
    private final Vector3i displacement;
    private final HashHitBox newHitBox;

    public CraftTranslateCommand(Craft craft, Vector3i displacement, HashHitBox newHitBox){
        this.craft = craft;
        this.displacement = displacement;
        this.newHitBox = newHitBox;
    }

    @Override
    public void doUpdate() {
        final Logger logger = Movecraft.getInstance().getLogger();
        if(craft.getHitBox().isEmpty()){
            logger.warn("Attempted to move craft with empty HashHitBox!");
            CraftManager.getInstance().removeCraft(craft);
            return;
        }
        long time = System.nanoTime();
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
            //add the craft
            Movecraft.getInstance().getWorldHandler().translateCraft(craft,displacement, newHitBox);
            //trigger sign event
            for(Vector3i location : craft.getHitBox()){
                BlockSnapshot block = MovecraftLocation.toSponge(craft.getWorld(), location).createSnapshot();
                if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){
                    Sponge.getEventManager().post(new SignTranslateEvent(block, craft));
                }
            }
        } else {
            MutableHitBox originalLocations = new HashHitBox();
            for (Vector3i vector3i : craft.getHitBox()) {
                originalLocations.add((vector3i).sub(displacement));
            }
            final HitBox to = CollectionUtils.filter(craft.getHitBox(), originalLocations);
            for (Vector3i location : to) {
                BlockSnapshot material = MovecraftLocation.toSponge(craft.getWorld(), location).createSnapshot();
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                }
            }

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

            final WorldHandler handler = Movecraft.getInstance().getWorldHandler();
            for (Vector3i location : CollectionUtils.filter(invertedHitBox, exterior)) {
                BlockSnapshot material = MovecraftLocation.toSponge(craft.getWorld(), location).createSnapshot();
                if (!passthroughBlocks.contains(material.getState().getType())) {
                    continue;
                }
                craft.getPhasedBlocks().add(material);
            }
            //add the craft
            handler.translateCraft(craft, displacement, newHitBox);
            //trigger sign event
            for (Vector3i location : craft.getHitBox()) {
                BlockSnapshot block = craft.getWorld().createSnapshot(location);
                if (block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN) {
                    Sponge.getEventManager().post(new SignTranslateEvent(block, craft));
                }
            }

            //place confirmed blocks if they have been un-phased
            for (BlockSnapshot block : craft.getPhasedBlocks()) {

                if (exterior.contains(new Vector3i(block.getPosition().getX(), block.getPosition().getY(), block.getPosition().getZ()))) {

                    handler.setBlock(block.getLocation().get(), block);
                    craft.getPhasedBlocks().remove(block);
                }

                if (originalLocations.contains(new Vector3i(block.getPosition().getX(), block.getPosition().getY(), block.getPosition().getZ())) && !craft.getHitBox().inBounds(new Vector3i(block.getPosition().getX(), block.getPosition().getY(), block.getPosition().getZ()))) {

                    handler.setBlock(block.getLocation().get(), block);
                    craft.getPhasedBlocks().remove(block);
                }
            }

            for (Vector3i location : interior) {
                final BlockSnapshot material = MovecraftLocation.toSponge(craft.getWorld(), location).createSnapshot();
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhasedBlocks().add(material);
                    handler.setBlock(MovecraftLocation.toSponge(craft.getWorld(), location), BlockSnapshot.builder().blockState(BlockTypes.AIR.getDefaultState()).world(craft.getWorld().getProperties()).position(location).build());

                }
            }
        }
        if (!craft.isNotProcessing())
            craft.setProcessing(false);
        time = System.nanoTime() - time;
        if(Settings.Debug)
            logger.info("Total time: " + (time / 1e9) + " seconds. Moving with cooldown of " + craft.getTickCooldown() + ". Speed of: " + String.format("%.2f", craft.getSpeed()));
        craft.addMoveTime(time/1e9f);
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
