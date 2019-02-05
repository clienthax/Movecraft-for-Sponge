package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.config.Settings;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Random;

public class ParticleUpdateCommand extends UpdateCommand {
    private Location<World> location;
    private int smokeStrength;
    private Random rand = new Random();
    private static int silhouetteBlocksSent; //TODO: remove this

    public ParticleUpdateCommand(Location<World> location, int smokeStrength) {
        this.location = location;
        this.smokeStrength = smokeStrength;
    }

    @Override
    public boolean doUpdate() {
        // put in smoke or effects
        if (smokeStrength == 1) {
            location.getExtent().spawnParticles(ParticleEffect.builder().type(ParticleTypes.SMOKE).build(), location.getPosition());
            return true;
        }
        if (Settings.SilhouetteViewDistance > 0 && silhouetteBlocksSent < Settings.SilhouetteBlockCount) {
            if (sendSilhouetteToPlayers())
                silhouetteBlocksSent++;
            return true;
        }

        return false;
    }

    private boolean sendSilhouetteToPlayers() {
        if (rand.nextInt(100) < 15) {

            ParticleEffect particle = ParticleEffect.builder().type(ParticleTypes.HAPPY_VILLAGER).build();

            for (Entity entity : location.getExtent().getEntities(entity -> entity instanceof Player)) { // this is necessary because signs do not get updated client side correctly without refreshing the chunks, which causes a memory leak in the clients

                Player p = (Player) entity;
                double distSquared = location.getPosition().distanceSquared(p.getLocation().getPosition());
                if ((distSquared < Settings.SilhouetteViewDistance * Settings.SilhouetteViewDistance) && (distSquared > (p.getViewDistance() -1 )*(p.getViewDistance() -1 ))) {
                    p.spawnParticles(particle, location.getPosition().toDouble(), 9);
                }
            }
            return true;
        }
        return false;
    }
}
