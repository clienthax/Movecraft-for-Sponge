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
     * @param dx - X translation
     * @param dy - Y translation
     * @param dz - Z translation
     * @return New MovecraftLocation shifted by specified amount
     */
    public MovecraftLocation translate(int dx, int dy, int dz) {
        return new MovecraftLocation(x + dx, y + dy, z + dz);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MovecraftLocation) {
            MovecraftLocation location = (MovecraftLocation) o;
            return location.x==this.x && location.y==this.y && location.z == this.z;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (x ^ (z << 12)) ^ (y << 24);
    }

    public MovecraftLocation add(MovecraftLocation loc) {
        return new MovecraftLocation(getX() + loc.getX(), getY() + loc.getY(), getZ() + loc.getZ());
    }

    public MovecraftLocation add(Vector3i loc) {
        return new MovecraftLocation(getX() + loc.getX(), getY() + loc.getY(), getZ() + loc.getZ());
    }
    
    public MovecraftLocation subtract(MovecraftLocation loc) {
        return new MovecraftLocation(getX() - loc.getX(), getY() - loc.getY(), getZ() - loc.getZ());
    }

    public MovecraftLocation subtract(Vector3i loc) {
        return new MovecraftLocation(getX() - loc.getX(), getY() - loc.getY(), getZ() - loc.getZ());
    }

    public Vector3i toVector3i() {
        return new Vector3i(getX(), getY(), getZ());
    }

    public Location<World> toSponge(World world){
        return new Location<World>(world, this.x, this.y, this.z);
    }

    public static Location<World> toSponge(World world, MovecraftLocation location){
        return new Location<World>(world, location.x, location.y, location.z);
    }

    @Override
    public String toString(){
        return "(" + x + "," + y + "," + z +")";
    }
}