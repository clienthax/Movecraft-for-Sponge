package io.github.pulverizer.movecraft.event;

import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;

public class CraftDetectEvent extends CraftEvent {

    private final Cause cause;

    public CraftDetectEvent(Craft craft) {
        super(craft);

        if (craft.getCommander() != null) {
            cause = Cause.builder()
                    .append(this)
                    .append(craft.getCommander())
                    .build(EventContext.empty());
        } else {
            cause = Cause.builder()
                    .append(this)
                    .build(EventContext.empty());
        }
    }

    @Override
    public Cause getCause() {
        return cause;
    }
}