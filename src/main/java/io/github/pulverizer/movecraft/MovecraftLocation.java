package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

/**
 * Represents a Block aligned coordinate triplet.
 */
final public class MovecraftLocation {

    /**
     * Converts the Vector3i to a Location.
     * @param location Vector3i to be converted.
     * @param world World to place the Location in.
     * @return New Location in the specified World.
     */
    public static Location<World> toSponge(World world, Vector3i location){
        return new Location<World>(world, location.getX(), location.getY(), location.getZ());
    }
}