package io.github.pulverizer.movecraft.events;

import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.event.cause.Cause;

/**
 * Called whenever a craft is released
 * @see Craft
 */
@SuppressWarnings("unused")
public class CraftReleaseEvent extends CraftEvent{
    private final Reason reason;

    public CraftReleaseEvent(Craft craft, Reason reason) {
        super(craft);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public Cause getCause() {
        return null;
    }

    public enum Reason{
        DISCONNECT,SUB_CRAFT,PLAYER,FORCE
    }
}