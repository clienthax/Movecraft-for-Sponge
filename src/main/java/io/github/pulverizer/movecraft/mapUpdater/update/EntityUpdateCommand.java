package io.github.pulverizer.movecraft.mapUpdater.update;

import com.flowpowered.math.vector.Vector3d;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import org.spongepowered.api.entity.Entity;

import java.util.Objects;

/**
 * Class that stores the data about a single blocks changes to the map in an unspecified world. The world is retrieved contextually from the submitting craft.
 */
public class EntityUpdateCommand extends UpdateCommand {
    private final Entity entity;
    private final Vector3d displacement;
    private final float yaw;

    public EntityUpdateCommand(Entity entity, Vector3d displacement, float yaw) {
        this.entity = entity;
        this.displacement = displacement;
        this.yaw = yaw;
    }

    public Entity getEntity() {
        return entity;
    }

    @Override
    public void doUpdate() {
        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Attempting to move entity of type: " + entity.getType().getName());
        try {
            Movecraft.getInstance().getWorldHandler().addEntityLocation(entity, displacement, yaw);
        } catch (Exception e) {
            Movecraft.getInstance().getLogger().info(e.getStackTrace().toString());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity.getUniqueId(), displacement, yaw);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof EntityUpdateCommand)){
            return false;
        }
        EntityUpdateCommand other = (EntityUpdateCommand) obj;
        return this.displacement == other.displacement &&
                this.yaw == other.yaw &&
                this.entity.equals(other.entity);
    }
}