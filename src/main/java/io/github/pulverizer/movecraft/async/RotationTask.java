package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.event.CraftRotateEvent;
import io.github.pulverizer.movecraft.map_updater.MapUpdateManager;
import io.github.pulverizer.movecraft.utils.*;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.map_updater.update.CraftRotateCommand;
import io.github.pulverizer.movecraft.map_updater.update.EntityUpdateCommand;
import io.github.pulverizer.movecraft.map_updater.update.UpdateCommand;
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
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RotationTask extends AsyncTask {
    private final Vector3i originPoint;
    private final Rotation rotation;
    private final World world;
    private final boolean isSubCraft;
    private final Set<UpdateCommand> updates = new HashSet<>();

    private final HashHitBox oldHitBox;
    private final HashHitBox newHitBox;

    public RotationTask(Craft craft, Vector3i originPoint, Rotation rotation, World world) {
        super(craft, "Rotation");
        this.originPoint = originPoint;
        this.rotation = rotation;
        this.world = world;
        this.isSubCraft = craft.isSubCraft();
        this.newHitBox = new HashHitBox();
        this.oldHitBox = new HashHitBox(craft.getHitBox());
    }

    @Override
    protected void execute() throws InterruptedException {

        if (oldHitBox.isEmpty() || craft.isDisabled())
            return;

        // check for fuel, use some if needed
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && !getCraft().isSinking()) {

            boolean fuelBurned = getCraft().useFuel(fuelBurnRate);

            if (!fuelBurned) {
                fail("Translation Failed - Craft out of fuel");
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

            BlockType oldMaterial = world.getBlockType(originalLocation);
            //prevent chests collision
            if ((oldMaterial.equals(BlockTypes.CHEST) || oldMaterial.equals(BlockTypes.TRAPPED_CHEST)) &&
                    !checkChests(oldMaterial, newLocation)) {
                fail(String.format("Rotation Failed - Craft is obstructed" + " @ %d,%d,%d", newLocation.getX(), newLocation.getY(), newLocation.getZ()));
                break;
            }


            BlockType newMaterial = world.getBlockType(newLocation);
            if ((newMaterial == BlockTypes.AIR) || (newMaterial == BlockTypes.PISTON_EXTENSION) || craft.getType().getPassthroughBlocks().contains(newMaterial)) {
                continue;
            }

            if (!oldHitBox.contains(newLocation)) {
                fail(String.format("Rotation Failed - Craft is obstructed" + " @ %d,%d,%d", newLocation.getX(), newLocation.getY(), newLocation.getZ()));
                break;
            }
        }

        if (failed()) {
            if (this.isSubCraft && parentCraft != getCraft()) {
                parentCraft.setProcessing(false);
            }
            return;
        }

        //call event
        CraftRotateEvent event = new CraftRotateEvent(craft, oldHitBox, newHitBox);
        Sponge.getEventManager().post(event);
        if(event.isCancelled()){
            fail(event.getFailMessage());
            return;
        }


        updates.add(new CraftRotateCommand(getCraft(),originPoint, rotation));
        //add entities in the craft
        final Vector3d tOP = originPoint.toDouble().add(0.5, 0, 0.5);

        //prevents torpedo and rocket passengers
        if (craft.getType().getMoveEntities() && !craft.isSinking()) {

            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info("Craft moves Entities.");

            AtomicBoolean processedEntities = new AtomicBoolean(false);

            Task.builder()
                    .execute(() -> {
                        for(Entity entity : craft.getWorld().getIntersectingEntities(new AABB(oldHitBox.getMinX() - 0.5, oldHitBox.getMinY() - 0.5, oldHitBox.getMinZ() - 0.5, oldHitBox.getMaxX() + 1.5, oldHitBox.getMaxY() + 1.5, oldHitBox.getMaxZ()+1.5))){

                            if (entity.getType() == EntityTypes.PLAYER || entity.getType() == EntityTypes.PRIMED_TNT || entity.getType() == EntityTypes.ITEM || !craft.getType().onlyMovePlayers()) {

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
    }
    
    @Override
    public void postProcess() {
        if (failed()) {
            craft.setProcessing(false);

        } else {

            MapUpdateManager.getInstance().scheduleUpdates(getUpdates());
            craft.setHitBox(getNewHitBox());
        }
    }

    private static HitBox rotateHitBox(HitBox hitBox, Vector3i originPoint, Rotation rotation){
        MutableHitBox output = new HashHitBox();
        for(Vector3i location : hitBox){
            output.add(MathUtils.rotateVec(rotation,originPoint.sub(originPoint)).add(originPoint));
        }
        return output;
    }

    @Override
    protected Optional<Player> getNotificationPlayer() {
        Optional<Player> player = Sponge.getServer().getPlayer(craft.getPilot());

        if (!player.isPresent()) {
            player = Sponge.getServer().getPlayer(craft.getCommander());
        }

        return player;
    }

    public Vector3i getOriginPoint() {
        return originPoint;
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