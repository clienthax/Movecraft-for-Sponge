package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

/**
 * Represents a Block aligned coordinate triplet.
 */
final public class MovecraftLocation extends Vector3i {
    private final int x, y, z;

    public MovecraftLocation(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    /**
     * Returns a MovecraftLocation that has undergone the given translation.
     * <p>
     * This does not change the MovecraftLocation that it is called upon and that should be accounted for in terms of Garbage Collection.
     *
     * @param dx X translation
     * @param dy Y translation
     * @param dz Z translation
     * @return New MovecraftLocation shifted by specified amounts.
     */
    public MovecraftLocation translate(int dx, int dy, int dz) {
        return new MovecraftLocation(x + dx, y + dy, z + dz);
    }

    /**
     * Compares this MovecraftLocation against another MovecraftLocation.
     */
    @Override
    public boolean equals(Object object) {
        if (object instanceof MovecraftLocation) {
            MovecraftLocation location = (MovecraftLocation) object;
            return location.x==this.x && location.y==this.y && location.z == this.z;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (x ^ (z << 12)) ^ (y << 24);
    }

    /**
     * Adds the coordinates of a MovecraftLocation onto the coordinates of this MovecraftLcation.
     * @return New MovecraftLocation.
     */
    public MovecraftLocation add(MovecraftLocation loc) {
        return new MovecraftLocation(getX() + loc.getX(), getY() + loc.getY(), getZ() + loc.getZ());
    }

    /**
     * Adds the coordinates of a Vector3i onto the coordinates of the MovecraftLocation.
     * @return New MovecraftLocation.
     */
    public MovecraftLocation add(Vector3i loc) {
        return new MovecraftLocation(getX() + loc.getX(), getY() + loc.getY(), getZ() + loc.getZ());
    }

    /**
     * Subtracts the coordinates of a MovecraftLocation from the coordinates of the MovecraftLocation.
     * @return New MovecraftLocation.
     */
    public MovecraftLocation subtract(MovecraftLocation loc) {
        return new MovecraftLocation(getX() - loc.getX(), getY() - loc.getY(), getZ() - loc.getZ());
    }

    /**
     * Subtracts the coordinates of a Vector3i from the coordinates of the MovecraftLocation.
     * @return New MovecraftLocation.
     */
    public MovecraftLocation subtract(Vector3i loc) {
        return new MovecraftLocation(getX() - loc.getX(), getY() - loc.getY(), getZ() - loc.getZ());
    }

    /**
     * Converts the MovecraftLocation to a Vector3i.
     * @return New Vector3i.
     */
    public Vector3i toVector3i() {
        return new Vector3i(getX(), getY(), getZ());
    }

    /**
     * Converts the MovecraftLocation to a Location.
     * @param world World to place the Location in.
     * @return New Location in the specified World.
     */
    public Location<World> toSponge(World world){
        return new Location<World>(world, this.x, this.y, this.z);
    }

    /**
     * Converts the MovecraftLocation to a Location.
     * @param location MovecraftLocation to be converted.
     * @param world World to place the Location in.
     * @return New Location in the specified World.
     */
    public static Location<World> toSponge(World world, MovecraftLocation location){
        return new Location<World>(world, location.x, location.y, location.z);
    }

    /**
     * Converts the MovecraftLocation to a readable String.
     * @return New String.
     */
    @Override
    public String toString(){
        return "(" + x + "," + y + "," + z +")";
    }
}