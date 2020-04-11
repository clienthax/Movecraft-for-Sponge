package io.github.pulverizer.movecraft.event;

import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;

public class CraftDetectEvent extends CraftEvent {

    private Cause cause;

    public CraftDetectEvent(Craft craft) {
        super(craft);

        cause = Cause.builder()
                .append(craft.getCommander())
                .build(EventContext.empty());
    }

    @Override
    public Cause getCause() {
        return cause;
    }
}