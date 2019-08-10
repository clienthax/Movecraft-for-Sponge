package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.event.CraftRotateEvent;
import io.github.pulverizer.movecraft.utils.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.mapUpdater.update.CraftRotateCommand;
import io.github.pulverizer.movecraft.mapUpdater.update.EntityUpdateCommand;
import io.github.pulverizer.movecraft.mapUpdater.update.UpdateCommand;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RotationTask extends AsyncTask {
    private final Vector3i originPoint;
    private final Rotation rotation;
    private final World world;
    private final boolean isSubCraft;
    private boolean failed = false;
    private String failMessage;
    private Set<UpdateCommand> updates = new HashSet<>();

    private final HashHitBox oldHitBox;
    private final HashHitBox newHitBox;

    public RotationTask(Craft craft, Vector3i originPoint, Rotation rotation, World world, boolean isSubCraft) {
        super(craft, "Rotation");
        this.originPoint = originPoint;
        this.rotation = rotation;
        this.world = world;
        this.isSubCraft = isSubCraft;
        this.newHitBox = new HashHitBox();
        this.oldHitBox = new HashHitBox(craft.getHitBox());
    }

    @Override
    protected void execute() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        if (oldHitBox.isEmpty() || craft.getState() == CraftState.DISABLED)
            return;

        // check for fuel, burn some from a furnace if needed. Blocks of coal are supported, in addition to coal and charcoal
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && getCraft().getState() != CraftState.SINKING) {

            boolean fuelBurned = getCraft().burnFuel(fuelBurnRate);

            if (!fuelBurned) {
                failed = true;
                failMessage = "Translation Failed - Craft out of fuel";
            }
        }

        // if a subcraft, find the parent craft. If not a subcraft, it is it's own parent
        Set<Craft> craftsInWorld = CraftManager.getInstance().getCraftsInWorld(getCraft().getWorld());
        Craft parentCraft = getCraft();
        for (Craft craft : craftsInWorld) {
            if ( craft != getCraft() && craft.getHitBox().intersects(oldHitBox)) {
                parentCraft = craft;
                break;
            }
        }

        for(Vector3i originalLocation : oldHitBox){
            Vector3i newLocation = MathUtils.rotateVec(rotation,originalLocation.sub(originPoint)).add(originPoint);
            newHitBox.add(newLocation);

            BlockType oldMaterial = MovecraftLocation.toSponge(world, originalLocation).getBlockType();
            //prevent chests collision
            if ((oldMaterial.equals(BlockTypes.CHEST) || oldMaterial.equals(BlockTypes.TRAPPED_CHEST)) &&
                    !checkChests(oldMaterial, newLocation)) {
                failed = true;
                failMessage = String.format("Rotation Failed - Craft is obstructed" + " @ %d,%d,%d", newLocation.getX(), newLocation.getY(), newLocation.getZ());
                break;
            }


            BlockType newMaterial = MovecraftLocation.toSponge(world, newLocation).getBlockType();
            if ((newMaterial == BlockTypes.AIR) || (newMaterial == BlockTypes.PISTON_EXTENSION) || craft.getType().getPassthroughBlocks().contains(newMaterial)) {
                continue;
            }

            if (!oldHitBox.contains(newLocation)) {
                failed = true;
                failMessage = String.format("Rotation Failed - Craft is obstructed" + " @ %d,%d,%d", newLocation.getX(), newLocation.getY(), newLocation.getZ());
                break;
            }
        }

        if (failed) {
            if (this.isSubCraft && parentCraft != getCraft()) {
                parentCraft.setProcessing(false);
            }
            return;
        }

        //call event
        CraftRotateEvent event = new CraftRotateEvent(craft, oldHitBox, newHitBox);
        Sponge.getEventManager().post(event);
        if(event.isCancelled()){
            failed = true;
            failMessage = event.getFailMessage();
            return;
        }


        updates.add(new CraftRotateCommand(getCraft(),originPoint, rotation));
        //add entities in the craft
        final Vector3d tOP = originPoint.toDouble().add(0.5, 0, 0.5);

        //prevents torpedo and rocket passengers
        if (craft.getType().getMoveEntities() && craft.getState() != CraftState.SINKING) {

            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info("Craft moves Entities.");

            AtomicBoolean processedEntities = new AtomicBoolean(false);

            Task.builder()
                    .execute(() -> {
                        for(Entity entity : craft.getWorld().getIntersectingEntities(new AABB(oldHitBox.getMinX() - 0.5, oldHitBox.getMinY() - 0.5, oldHitBox.getMinZ() - 0.5, oldHitBox.getMaxX() + 1.5, oldHitBox.getMaxY() + 1.5, oldHitBox.getMaxZ()+1.5))){

                            if (entity.getType() == EntityTypes.PLAYER || entity.getType() == EntityTypes.PRIMED_TNT || entity.getType() == EntityTypes.ITEM || !craft.getType().getOnlyMovePlayers()) {

                                Location<World> adjustedPLoc = entity.getLocation().sub(tOP);

                                double[] rotatedCoords = MathUtils.rotateVecNoRound(rotation, adjustedPLoc.getX(), adjustedPLoc.getZ());
                                float newYaw = rotation == Rotation.CLOCKWISE ? 90F : -90F;
                                EntityUpdateCommand eUp = new EntityUpdateCommand(entity, new Vector3d(rotatedCoords[0] + tOP.getX(), entity.getLocation().getY(), rotatedCoords[1] + tOP.getZ()), newYaw);
                                updates.add(eUp);

                                if (Settings.Debug) {
                                    Movecraft.getInstance().getLogger().info("Submitting Entity Update: " + entity.getType().getName());
                                    if (entity instanceof Item)
                                        Movecraft.getInstance().getLogger().info("Item Type: " + ((Item) entity).getItemType().getName());
                                }
                            }
                        }

                        processedEntities.set(true);
                    })
                    .submit(Movecraft.getInstance());



            synchronized (this) {
                while (!processedEntities.get()) this.wait(1);
            }
        }

        if (getCraft().getState() == CraftState.CRUISING) {
            if (rotation == Rotation.ANTICLOCKWISE) {

                switch (getCraft().getCruiseDirection()) {
                    case NORTH:
                        getCraft().setCruiseDirection(Direction.WEST);
                        break;

                    case SOUTH:
                        getCraft().setCruiseDirection(Direction.EAST);
                        break;

                    case EAST:
                        getCraft().setCruiseDirection(Direction.NORTH);
                        break;

                    case WEST:
                        getCraft().setCruiseDirection(Direction.SOUTH);
                        break;
                }
            } else if (rotation == Rotation.CLOCKWISE) {

                switch (getCraft().getCruiseDirection()) {
                    case NORTH:
                        getCraft().setCruiseDirection(Direction.EAST);
                        break;

                    case SOUTH:
                        getCraft().setCruiseDirection(Direction.WEST);
                        break;

                    case EAST:
                        getCraft().setCruiseDirection(Direction.SOUTH);
                        break;

                    case WEST:
                        getCraft().setCruiseDirection(Direction.NORTH);
                        break;
                }
            }
        }

        // if you rotated a subcraft, update the parent with the new blocks
        if (this.isSubCraft) {
            // also find the furthest extent from center and notify the player of the new direction
            int farthestX = 0;
            int farthestZ = 0;
            for (Vector3i loc : newHitBox) {
                if (Math.abs(loc.getX() - originPoint.getX()) > Math.abs(farthestX))
                    farthestX = loc.getX() - originPoint.getX();
                if (Math.abs(loc.getZ() - originPoint.getZ()) > Math.abs(farthestZ))
                    farthestZ = loc.getZ() - originPoint.getZ();
            }

            Player pilot = Sponge.getServer().getPlayer(getCraft().getPilot()).orElse(null);

            if (pilot != null) {
                if (Math.abs(farthestX) > Math.abs(farthestZ)) {
                    if (farthestX > 0) {
                        pilot.sendMessage(Text.of("The farthest extent now faces East"));
                    } else {
                        pilot.sendMessage(Text.of("The farthest extent now faces West"));
                    }
                } else {
                    if (farthestZ > 0) {
                        pilot.sendMessage(Text.of("The farthest extent now faces South"));
                    } else {
                        pilot.sendMessage(Text.of("The farthest extent now faces North"));
                    }
                }
            }

            craftsInWorld = CraftManager.getInstance().getCraftsInWorld(getCraft().getWorld());
            for (Craft craft : craftsInWorld) {
                if (newHitBox.intersects(craft.getHitBox()) && craft != getCraft()) {
                    //newHitBox.addAll(CollectionUtils.filter(craft.getHitBox(),newHitBox));
                    //craft.setHitBox(newHitBox);
                    craft.getHitBox().removeAll(oldHitBox);
                    craft.getHitBox().addAll(newHitBox);
                    break;
                }
            }
        }
        long endTime = System.currentTimeMillis();

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Rotation Task Took: " + (endTime - startTime) + "ms");

    }

    private static HitBox rotateHitBox(HitBox hitBox, Vector3i originPoint, Rotation rotation){
        MutableHitBox output = new HashHitBox();
        for(Vector3i location : hitBox){
            output.add(MathUtils.rotateVec(rotation,originPoint.sub(originPoint)).add(originPoint));
        }
        return output;
    }
    public Vector3i getOriginPoint() {
        return originPoint;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public Set<UpdateCommand> getUpdates() {
        return updates;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public boolean getIsSubCraft() {
        return isSubCraft;
    }

    private boolean checkChests(BlockType mBlock, Vector3i newLoc) {
        BlockType testMaterial;
        Vector3i aroundNewLoc;

        aroundNewLoc = newLoc.add(1, 0, 0);
        testMaterial = craft.getWorld().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        if (testMaterial.equals(mBlock)) {
            if (!oldHitBox.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.add(-1, 0, 0);
        testMaterial = craft.getWorld().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        if (testMaterial.equals(mBlock)) {
            if (!oldHitBox.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.add(0, 0, 1);
        testMaterial = craft.getWorld().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        if (testMaterial.equals(mBlock)) {
            if (!oldHitBox.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.add(0, 0, -1);
        testMaterial = craft.getWorld().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        return !testMaterial.equals(mBlock) || oldHitBox.contains(aroundNewLoc);
    }

    public HashHitBox getNewHitBox() {
        return newHitBox;
    }
}