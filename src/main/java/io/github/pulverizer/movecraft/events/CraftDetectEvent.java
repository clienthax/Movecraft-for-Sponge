package io.github.pulverizer.movecraft.events;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.event.cause.Cause;

public class CraftDetectEvent extends CraftEvent {

    public CraftDetectEvent(Craft craft) {
        super(craft);
    }

    //TODO: Fix this!
    @Override
    public Cause getCause() {
        return null;
    }
}