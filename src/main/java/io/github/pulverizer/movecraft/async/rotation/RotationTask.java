package io.github.pulverizer.movecraft.async.rotation;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.events.CraftRotateEvent;
import io.github.pulverizer.movecraft.events.CraftTranslateEvent;
import io.github.pulverizer.movecraft.utils.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import io.github.pulverizer.movecraft.async.AsyncTask;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import io.github.pulverizer.movecraft.mapUpdater.update.CraftRotateCommand;
import io.github.pulverizer.movecraft.mapUpdater.update.EntityUpdateCommand;
import io.github.pulverizer.movecraft.mapUpdater.update.UpdateCommand;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Furnace;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RotationTask extends AsyncTask {
    private final MovecraftLocation originPoint;
    private final Rotation rotation;
    private final World w;
    private final boolean isSubCraft;
    private boolean failed = false;
    private String failMessage;
    private Set<UpdateCommand> updates = new HashSet<>();

    private final HashHitBox oldHitBox;
    private final HashHitBox newHitBox;

    public RotationTask(Craft c, MovecraftLocation originPoint, Rotation rotation, World w, boolean isSubCraft) {
        super(c);
        this.originPoint = originPoint;
        this.rotation = rotation;
        this.w = w;
        this.isSubCraft = isSubCraft;
        this.newHitBox = new HashHitBox();
        this.oldHitBox = new HashHitBox(c.getHitBox());
    }

    public RotationTask(Craft c, MovecraftLocation originPoint, Rotation rotation, World w) {
        this(c,originPoint,rotation,w,false);
    }

    @Override
    protected void excecute() {

        if(oldHitBox.isEmpty())
            return;
        Player craftPilot = CraftManager.getInstance().getPlayerFromCraft(getCraft());

        if (getCraft().getDisabled() && (!getCraft().getSinking())) {
            failed = true;
            failMessage = I18nSupport.getInternationalisedString("Craft is disabled!");
        }

        // check for fuel, burn some from a furnace if needed. Blocks of coal are supported, in addition to coal and charcoal
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && !getCraft().getSinking()) {
            if (getCraft().getBurningFuel() < fuelBurnRate) {
                Furnace fuelHolder = null;
                for (MovecraftLocation bTest : oldHitBox) {
                    BlockSnapshot b = getCraft().getW().createSnapshot(bTest.getX(), bTest.getY(), bTest.getZ());
                    if (b.getState().getType() == BlockTypes.FURNACE || b.getState().getType() == BlockTypes.LIT_FURNACE) {
                        fuelHolder = b.getLocation()
                                .flatMap(Location::getTileEntity)
                                .filter(Furnace.class::isInstance)
                                .map(Furnace.class::cast)
                                .filter(furnace -> furnace.getInventory().contains(ItemTypes.COAL) || furnace.getInventory().contains(ItemTypes.COAL_BLOCK))
                                .get();
                    }
                }
                if (fuelHolder == null) {
                    failed = true;
                    failMessage = I18nSupport.getInternationalisedString("Translation - Failed Craft out of fuel");
                } else {
                    Inventory inventory = fuelHolder.getInventory();
                    if (inventory.contains(ItemTypes.COAL)) {
                        inventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL)).poll(1);
                        getCraft().setBurningFuel(getCraft().getBurningFuel() + 7.0);
                    } else {
                        inventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL_BLOCK)).poll(1);
                        getCraft().setBurningFuel(getCraft().getBurningFuel() + 79.0);

                    }
                }
            } else {
                getCraft().setBurningFuel(getCraft().getBurningFuel() - fuelBurnRate);
            }
        }
        // if a subcraft, find the parent craft. If not a subcraft, it is it's own parent
        Set<Craft> craftsInWorld = CraftManager.getInstance().getCraftsInWorld(getCraft().getW());
        Craft parentCraft = getCraft();
        for (Craft craft : craftsInWorld) {
            if ( craft != getCraft() && craft.getHitBox().intersects(oldHitBox)) {
                parentCraft = craft;
                break;
            }
        }

        for(MovecraftLocation originalLocation : oldHitBox){
            MovecraftLocation newLocation = MathUtils.rotateVec(rotation,originalLocation.subtract(originPoint)).add(originPoint);
            newHitBox.add(newLocation);

            BlockType oldMaterial = originalLocation.toSponge(w).getBlockType();
            //prevent chests collision
            if ((oldMaterial.equals(BlockTypes.CHEST) || oldMaterial.equals(BlockTypes.TRAPPED_CHEST)) &&
                    !checkChests(oldMaterial, newLocation)) {
                failed = true;
                failMessage = String.format(I18nSupport.getInternationalisedString("Rotation - Craft is obstructed") + " @ %d,%d,%d", newLocation.getX(), newLocation.getY(), newLocation.getZ());
                break;
            }

            Location plugLoc = newLocation.toSponge(w);

            BlockType newMaterial = newLocation.toSponge(w).getBlockType();
            if ((newMaterial == BlockTypes.AIR) || (newMaterial == BlockTypes.PISTON_EXTENSION) || craft.getType().getPassthroughBlocks().contains(newMaterial)) {
                continue;
            }

            if (!oldHitBox.contains(newLocation)) {
                failed = true;
                failMessage = String.format(I18nSupport.getInternationalisedString("Rotation - Craft is obstructed") + " @ %d,%d,%d", newLocation.getX(), newLocation.getY(), newLocation.getZ());
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
        //rotate entities in the craft
        Location tOP = new Location<>(getCraft().getW(), originPoint.getX(), originPoint.getY(), originPoint.getZ());
        tOP.add(0.5, 0, 0.5);

        if (craft.getType().getMoveEntities() && !(craft.getSinking() && craft.getType().getOnlyMovePlayers())) {

            HashHitBox craftHitBox = craft.getHitBox();

            for(Entity entity : craft.getW().getIntersectingEntities(new AABB(craftHitBox.getMinX(), craftHitBox.getMinY(), craftHitBox.getMinZ(), craftHitBox.getMaxX(), craftHitBox.getMaxY(), craftHitBox.getMaxZ()))) {
                if ((entity.getType() == EntityTypes.PLAYER && !craft.getSinking()) || !craft.getType().getOnlyMovePlayers()) {
                    // Player is onboard this craft

                    Location adjustedPLoc = entity.getLocation().sub(tOP.getX(), tOP.getY(), tOP.getZ());

                    double[] rotatedCoords = MathUtils.rotateVecNoRound(rotation, adjustedPLoc.getX(), adjustedPLoc.getZ());
                    float newYaw = rotation == Rotation.CLOCKWISE ? 90F : -90F;
                    EntityUpdateCommand eUp = new EntityUpdateCommand(entity, rotatedCoords[0] + tOP.getX() - entity.getLocation().getX(), 0, rotatedCoords[1] + tOP.getZ() - entity.getLocation().getZ(), newYaw, 0);
                    updates.add(eUp);
                }
            }
        }

        if (getCraft().getCruising()) {
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
            for (MovecraftLocation loc : newHitBox) {
                if (Math.abs(loc.getX() - originPoint.getX()) > Math.abs(farthestX))
                    farthestX = loc.getX() - originPoint.getX();
                if (Math.abs(loc.getZ() - originPoint.getZ()) > Math.abs(farthestZ))
                    farthestZ = loc.getZ() - originPoint.getZ();
            }
            if (Math.abs(farthestX) > Math.abs(farthestZ)) {
                if (farthestX > 0) {
                    if (getCraft().getNotificationPlayer() != null)
                        getCraft().getNotificationPlayer().sendMessage(Text.of("The farthest extent now faces East"));
                } else {
                    if (getCraft().getNotificationPlayer() != null)
                        getCraft().getNotificationPlayer().sendMessage(Text.of("The farthest extent now faces West"));
                }
            } else {
                if (farthestZ > 0) {
                    if (getCraft().getNotificationPlayer() != null)
                        getCraft().getNotificationPlayer().sendMessage(Text.of("The farthest extent now faces South"));
                } else {
                    if (getCraft().getNotificationPlayer() != null)
                        getCraft().getNotificationPlayer().sendMessage(Text.of("The farthest extent now faces North"));
                }
            }

            craftsInWorld = CraftManager.getInstance().getCraftsInWorld(getCraft().getW());
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

    private static HitBox rotateHitBox(HitBox hitBox, MovecraftLocation originPoint, Rotation rotation){
        MutableHitBox output = new HashHitBox();
        for(MovecraftLocation location : hitBox){
            output.add(MathUtils.rotateVec(rotation,originPoint.subtract(originPoint)).add(originPoint));
        }
        return output;
    }
    public MovecraftLocation getOriginPoint() {
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

    private boolean checkChests(BlockType mBlock, MovecraftLocation newLoc) {
        BlockType testMaterial;
        MovecraftLocation aroundNewLoc;

        aroundNewLoc = newLoc.translate(1, 0, 0);
        testMaterial = craft.getW().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        if (testMaterial.equals(mBlock)) {
            if (!oldHitBox.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(-1, 0, 0);
        testMaterial = craft.getW().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        if (testMaterial.equals(mBlock)) {
            if (!oldHitBox.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(0, 0, 1);
        testMaterial = craft.getW().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        if (testMaterial.equals(mBlock)) {
            if (!oldHitBox.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(0, 0, -1);
        testMaterial = craft.getW().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
        return !testMaterial.equals(mBlock) || oldHitBox.contains(aroundNewLoc);
    }

    public HashHitBox getNewHitBox() {
        return newHitBox;
    }
}