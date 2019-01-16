package io.github.pulverizer.movecraft;

import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;

public abstract class WorldHandler {
    public abstract void rotateCraft(Craft craft, MovecraftLocation originLocation, Rotation rotation);
    public abstract void translateCraft(Craft craft, MovecraftLocation newLocation);
    public abstract void setBlockFast(Location location, BlockSnapshot block);
    public abstract void setBlockFast(Location location, Rotation rotation, BlockSnapshot block);
    public abstract void disableShadow(BlockType type);
    public void addPlayerLocation(Player player, double x, double y, double z, float yaw, float pitch){
        Location playerLoc = player.getLocation();
        player.setLocationAndRotation(new Location<>(player.getWorld(), x + playerLoc.getX(),y + playerLoc.getY(),z + playerLoc.getZ()), player.getRotation().add(pitch, yaw, 0));
    }
}