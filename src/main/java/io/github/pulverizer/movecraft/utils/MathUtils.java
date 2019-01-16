package io.github.pulverizer.movecraft.utils;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.world.Location;

public class MathUtils {



    /**
     * checks if <code>location</code> is within the bounding box <code>box</code> restricted by minimum values on x and z
     * @param box the bounding box to check within
     * @param minX the minimum x coordinate to search
     * @param minZ the minimum z coordinate to search
     * @param location the location to check
     * @return True if the player is within the given bounding box
     */

    public static boolean playerIsWithinBoundingPolygon(final int[][][] box, final int minX, final int minZ, final MovecraftLocation location) {
        if (location.getX() >= minX && location.getX() < (minX + box.length)) {
            // PLayer is within correct X boundary
            if (location.getZ() >= minZ && location.getZ() < (minZ + box[location.getX() - minX].length)) {
                // Player is within valid Z boundary
                int minY, maxY;
                try {
                    minY = box[location.getX() - minX][location.getZ() - minZ][0];
                    maxY = box[location.getX() - minX][location.getZ() - minZ][1];
                } catch (NullPointerException e) {
                    return false;
                }
                return location.getY() >= minY && location.getY() <= (maxY + 2);
            }
        }
        return false;
    }

    /**
     * checks if the given bukkit <code>location</code> is within <code>hitbox</code>
     * @param hitBox the bounding box to check within
     * @param location the location to check
     * @return True if the player is within the given bounding box
     */

    public static boolean locationInHitbox(final HashHitBox hitBox, final Location location) {
        return hitBox.inBounds(location.getX(),location.getY(),location.getZ());
    }

    /**
     * Checks if a given <code>Location</code> is within some distance, <code>distance</code>, from a given <code>HitBox</code>
     * @param hitBox the hitbox to check
     * @param location the location to check
     * @return True if <code>location</code> is less or equal to 3 blocks from <code>craft</code>
     */

    public static boolean locationNearHitBox(final HashHitBox hitBox, final Location location, double distance) {
        return !hitBox.isEmpty() &&
                location.getX() >= hitBox.getMinX() - distance &&
                location.getZ() >= hitBox.getMinZ() - distance &&
                location.getX() <= hitBox.getMaxX() + distance &&
                location.getZ() <= hitBox.getMaxZ() + distance &&
                location.getY() >= hitBox.getMinY() - distance &&
                location.getY() <= hitBox.getMaxY() + distance;
    }

    /**
     * Checks if a given <code>Location</code> is within 3 blocks from a given <code>Craft</code>
     * @param craft the craft to check
     * @param location the location to check
     * @return True if <code>location</code> is less or equal to 3 blocks from <code>craft</code>
     */

    public static boolean locIsNearCraftFast(final Craft craft, final MovecraftLocation location) {
        // optimized to be as fast as possible, it checks the easy ones first, then the more computationally intensive later
        return locationNearHitBox(craft.getHitBox(), location.toSponge(craft.getW()), 3);
    }

    /**
     * Creates a <code>MovecraftLocation</code> representation of a bukkit <code>Location</code> object aligned to the block grid
     * @param bukkitLocation the location to convert
     * @return a new <code>MovecraftLocation</code> representing the given location
     */

    public static MovecraftLocation sponge2MovecraftLoc(final Location bukkitLocation) {
        return new MovecraftLocation(bukkitLocation.getBlockX(), bukkitLocation.getBlockY(), bukkitLocation.getBlockZ());
    }

    /**
     * Rotates a MovecraftLocation towards a supplied <code>Rotation</code>.
     * The resulting MovecraftRotation is based on a center of (0,0,0).
     * @param rotation the direction to rotate
     * @param movecraftLocation the location to rotate
     * @return a rotated Movecraft location
     */

    public static MovecraftLocation rotateVec(final Rotation rotation, final MovecraftLocation movecraftLocation) {
        double theta;
        if (rotation == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        int x = (int) Math.round((movecraftLocation.getX() * Math.cos(theta)) + (movecraftLocation.getZ() * (-1 * Math.sin(theta))));
        int z = (int) Math.round((movecraftLocation.getX() * Math.sin(theta)) + (movecraftLocation.getZ() * Math.cos(theta)));

        return new MovecraftLocation(x, movecraftLocation.getY(), z);
    }

    @Deprecated
    public static double[] rotateVec(Rotation rotation, double x, double z) {
        double theta;
        if (rotation == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        double newX = Math.round((x * Math.cos(theta)) + (z * (-1 * Math.sin(theta))));
        double newZ = Math.round((x * Math.sin(theta)) + (z * Math.cos(theta)));

        return new double[]{newX, newZ};
    }

    @Deprecated
    public static double[] rotateVecNoRound(Rotation r, double x, double z) {
        double theta;
        if (r == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        double newX = (x * Math.cos(theta)) + (z * (-1 * Math.sin(theta)));
        double newZ = (x * Math.sin(theta)) + (z * Math.cos(theta));

        return new double[]{newX, newZ};
    }

    @Deprecated
    public static int positiveMod(int mod, int divisor) {
        if (mod < 0) {
            mod += divisor;
        }
        return mod;
    }
}