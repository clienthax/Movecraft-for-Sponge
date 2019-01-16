package io.github.pulverizer.movecraft.events;

import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.cause.Cause;

public class CraftCollisionEvent extends CraftEvent implements Cancellable {
    private final HashHitBox hitBox;
    private boolean isCancelled = false;

    public CraftCollisionEvent(Craft craft, HashHitBox hitBox) {
        super(craft);
        this.hitBox = hitBox;
    }

    public HashHitBox getHitBox() {
        return hitBox;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        isCancelled = cancel;
    }

    @Override
    public Cause getCause() {
        return null;
    }
}