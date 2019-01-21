package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.Movecraft;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Objects;

/**
 * Class that stores the data about a single blocks changes to the map in an unspecified world. The world is retrieved contextually from the submitting craft.
 */
public class EntityUpdateCommand extends UpdateCommand {
    private final Entity entity;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public EntityUpdateCommand(Entity entity, double x, double y, double z, float yaw, float pitch) {
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Entity getEntity() {
        return entity;
    }

    @Override
    public void doUpdate() {
        if (!(entity instanceof Player)) {
            Location<World> entityLocation = entity.getLocation();
            entity.setLocationAndRotation(entityLocation.add(x, y, z), entity.getRotation().add(pitch, yaw , 0));
            return;
        }
        Movecraft.getInstance().getWorldHandler().addPlayerLocation((Player) entity,x,y,z,yaw,pitch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity.getUniqueId(), x, y, z, pitch, yaw);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof EntityUpdateCommand)){
            return false;
        }
        EntityUpdateCommand other = (EntityUpdateCommand) obj;
        return this.x == other.x &&
                this.y == other.y &&
                this.z == other.z &&
                this.pitch == other.pitch &&
                this.yaw == other.yaw &&
                this.entity.equals(other.entity);
    }
}