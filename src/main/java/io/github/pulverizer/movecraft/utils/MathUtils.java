package io.github.pulverizer.movecraft.utils;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.world.Location;

public class MathUtils {

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
     * @param distance
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

    public static boolean locIsNearCraftFast(final Craft craft, final Vector3i location) {
        // optimized to be as fast as possible, it checks the easy ones first, then the more computationally intensive later
        return locationNearHitBox(craft.getHitBox(), MovecraftLocation.toSponge(craft.getWorld(), location), 3);
    }

    /**
     * Rotates a Vector3i towards a supplied <code>Rotation</code>.
     * The resulting MovecraftRotation is based on a center of (0,0,0).
     * @param rotation the direction to add
     * @param vector3i the location to add
     * @return a rotated Movecraft location
     */

    public static Vector3i rotateVec(final Rotation rotation, final Vector3i vector3i) {
        double theta;
        if (rotation == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        int x = (int) Math.round((vector3i.getX() * Math.cos(theta)) + (vector3i.getZ() * (-1 * Math.sin(theta))));
        int z = (int) Math.round((vector3i.getX() * Math.sin(theta)) + (vector3i.getZ() * Math.cos(theta)));

        return new Vector3i(x, vector3i.getY(), z);
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
    public static double[] rotateVecNoRound(Rotation rotation, double x, double z) {
        double newX;
        double newZ;

        if (rotation == Rotation.CLOCKWISE) {

            newX = 0 - z;
            newZ = x;

        } else {

            newX = z;
            newZ = 0 - x;

        }

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