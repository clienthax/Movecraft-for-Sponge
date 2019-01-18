package io.github.pulverizer.movecraft.mapUpdater.update;

import com.google.common.collect.Sets;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.WorldHandler;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.SignTranslateEvent;
import io.github.pulverizer.movecraft.utils.*;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;

import java.util.*;



public class CraftRotateCommand extends UpdateCommand {
    private final Craft craft;
    private final Rotation rotation;
    private final MovecraftLocation originLocation;

    public CraftRotateCommand(final Craft craft, final MovecraftLocation originLocation, final Rotation rotation) {
        this.craft = craft;
        this.rotation = rotation;
        this.originLocation = originLocation;
    }

    @Override
    public void doUpdate() {
        final Logger logger = Movecraft.getInstance().getLogger();
        if (craft.getHitBox().isEmpty()) {
            logger.warn("Attempted to move craft with empty HashHitBox!");
            CraftManager.getInstance().removeCraft(craft);
            return;
        }
        long time = System.nanoTime();
        final Set<BlockType> passthroughBlocks = new HashSet<>(craft.getType().getPassthroughBlocks());
        if(craft.getSinking()){
            passthroughBlocks.add(BlockTypes.WATER);
            passthroughBlocks.add(BlockTypes.FLOWING_WATER);
            passthroughBlocks.add(BlockTypes.LEAVES);
            passthroughBlocks.add(BlockTypes.LEAVES2);
            passthroughBlocks.add(BlockTypes.GRASS);
            passthroughBlocks.add(BlockTypes.DOUBLE_PLANT);
        }
        if (!passthroughBlocks.isEmpty()) {
            MutableHitBox originalLocations = new HashHitBox();
            final Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
            for (MovecraftLocation movecraftLocation : craft.getHitBox()) {
                originalLocations.add(MathUtils.rotateVec(counterRotation, movecraftLocation.subtract(originLocation)).add(originLocation));
            }

            final HitBox to = CollectionUtils.filter(craft.getHitBox(), originalLocations);

            for (MovecraftLocation location : to) {
                BlockSnapshot material = location.toSponge(craft.getW()).createSnapshot();
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhaseBlocks().put(location, material);
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
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(minX, maxY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, minY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, maxY, minZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, maxY, maxZ), new MovecraftLocation(minX, maxY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, maxY, maxZ), new MovecraftLocation(maxX, minY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, maxY, maxZ), new MovecraftLocation(maxX, maxY, minZ))};
            //Valid exterior starts as the 6 surface planes of the HitBox with the locations that lie in the HitBox removed
            final Set<MovecraftLocation> validExterior = new HashSet<>();
            for (HitBox hitBox : surfaces) {
                validExterior.addAll(CollectionUtils.filter(hitBox, craft.getHitBox()).asSet());
            }
            //Check to see which locations in the from set are actually outside of the craft
            for (MovecraftLocation location :validExterior ) {
                if (craft.getHitBox().contains(location) || exterior.contains(location)) {
                    continue;
                }
                //use a modified BFS for multiple origin elements
                Set<MovecraftLocation> visited = new HashSet<>();
                Queue<MovecraftLocation> queue = new LinkedList<>();
                queue.add(location);
                while (!queue.isEmpty()) {
                    MovecraftLocation node = queue.poll();
                    //If the node is already a valid member of the exterior of the HitBox, continued search is unitary.
                    for (MovecraftLocation neighbor : CollectionUtils.neighbors(invertedHitBox, node)) {
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
            for (MovecraftLocation location : CollectionUtils.filter(invertedHitBox, exterior)) {
                BlockSnapshot material = location.toSponge(craft.getW()).createSnapshot();
                if (!passthroughBlocks.contains(material.getState().getType())) {
                    continue;
                }
                craft.getPhaseBlocks().put(location, material);
            }

            //translate the craft

            handler.rotateCraft(craft, originLocation, rotation);
            //trigger sign events
            for (MovecraftLocation location : craft.getHitBox()) {
                BlockSnapshot block = location.toSponge(craft.getW()).createSnapshot();
                if (block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN) {
                    Sign sign = (Sign) block.getState();
                    Sponge.getEventManager().post(new SignTranslateEvent(block, craft, sign.getLines()));
                    sign.update();
                }
            }

            //place confirmed blocks if they have been un-phased
            for (MovecraftLocation location : exterior) {
                if (!craft.getPhaseBlocks().containsKey(location)) {
                    continue;
                }
                handler.setBlockFast(location.toSponge(craft.getW()), craft.getPhaseBlocks().get(location));
                craft.getPhaseBlocks().remove(location);
            }
            for(MovecraftLocation location : originalLocations.boundingHitBox()){
                if(!to.inBounds(location) && craft.getPhaseBlocks().containsKey(location)){
                    handler.setBlockFast(location.toSponge(craft.getW()), craft.getPhaseBlocks().remove(location));
                }
            }

            for (MovecraftLocation location : interior) {
                final BlockSnapshot material = location.toSponge(craft.getW()).createSnapshot();
                if (passthroughBlocks.contains(material.getState().getType())) {
                    craft.getPhaseBlocks().put(location, material);
                    handler.setBlockFast(location.toSponge(craft.getW()), BlockTypes.AIR.getDefaultState().snapshotFor(location.toSponge(craft.getW())));

                }
            }
        }else{
            //translate the craft

            Movecraft.getInstance().getWorldHandler().rotateCraft(craft, originLocation, rotation);
            //trigger sign events
            for (MovecraftLocation location : craft.getHitBox()) {
                BlockSnapshot block = location.toSponge(craft.getW()).createSnapshot();
                if (block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN) {
                    Sign sign = (Sign) block.getState();
                    Bukkit.getServer().getPluginManager().callEvent(new SignTranslateEvent(block, craft, sign.getLines()));
                    sign.update();
                }
            }
        }
        if (!craft.isNotProcessing())
            craft.setProcessing(false);
        time = System.nanoTime() - time;
        if (Settings.Debug)
            logger.info("Total time: " + (time / 1e9) + " seconds. Moving with cooldown of " + craft.getTickCooldown() + ". Speed of: " + String.format("%.2f", craft.getSpeed()));
        craft.addMoveTime(time / 1e9f);
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
