package io.github.pulverizer.movecraft.mapUpdater.update;

import com.flowpowered.math.vector.Vector3d;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.world.Location;

import java.util.Objects;

/**
 * Class that stores the data about a single entity change to the map in an unspecified world. The world is retrieved contextually from the submitting craft.
 */
public class EntityUpdateCommand extends UpdateCommand {
    private final Entity entity;
    private final Vector3d newLocation;
    private final float yaw;

    public EntityUpdateCommand(Entity entity, Vector3d newLocation, float yaw) {
        this.entity = entity;
        this.newLocation = newLocation;
        this.yaw = yaw;
    }

    public Entity getEntity() {
        return entity;
    }

    @Override
    public boolean doUpdate() {
        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Attempting to move entity of type: " + entity.getType().getName());

        boolean entityMoved = entity.setLocationAndRotation(new Location<>(entity.getWorld(), newLocation), entity.getRotation().add(0, yaw, 0));

        if (entityMoved) {
            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info("Moved Entity of type: " + entity.getType().getName());

            return true;

        } else {
            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info("Failed to move Entity of type: " + entity.getType().getName());

            return false;

        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity.getUniqueId(), newLocation, yaw);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof EntityUpdateCommand)){
            return false;
        }
        EntityUpdateCommand other = (EntityUpdateCommand) obj;
        return this.newLocation == other.newLocation &&
                this.yaw == other.yaw &&
                this.entity.equals(other.entity);
    }
}