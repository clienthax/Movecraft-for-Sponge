package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.config.Settings;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;

import java.util.Random;

public class ParticleUpdateCommand extends UpdateCommand {
    private Location location;
    private int smokeStrength;
    private Random rand = new Random();
    private static int silhouetteBlocksSent; //TODO: remove this

    public ParticleUpdateCommand(Location location, int smokeStrength) {
        this.location = location;
        this.smokeStrength = smokeStrength;
    }

    @Override
    public void doUpdate() {
        // put in smoke or effects
        if (smokeStrength == 1) {
            location.playEffect(location, ParticleTypes.SMOKE, 4);
        }
        if (Settings.SilhouetteViewDistance > 0 && silhouetteBlocksSent < Settings.SilhouetteBlockCount) {
            if (sendSilhouetteToPlayers())
                silhouetteBlocksSent++;
        }

    }

    private boolean sendSilhouetteToPlayers() {
        if (rand.nextInt(100) < 15) {

            for (Player p : location.getExtent().getPlayers()) { // this is necessary because signs do not get updated client side correctly without refreshing the chunks, which causes a memory leak in the clients
                double distSquared = location.distanceSquared(p.getLocation());
                if ((distSquared < Settings.SilhouetteViewDistance * Settings.SilhouetteViewDistance) && (distSquared > 32 * 32)) {
                    p.spawnParticle(ParticleTypes.HAPPY_VILLAGER, location, 9);
                }
            }
            return true;
        }
        return false;
    }
}
