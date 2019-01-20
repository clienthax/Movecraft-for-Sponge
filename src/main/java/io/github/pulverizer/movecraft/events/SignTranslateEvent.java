package io.github.pulverizer.movecraft.events;

import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.text.Text;


public class SignTranslateEvent extends CraftEvent{
    private final BlockSnapshot block;

    public SignTranslateEvent(BlockSnapshot block, Craft craft) throws IndexOutOfBoundsException{
        super(craft);
        this.block = block;
    }

    public BlockSnapshot getBlock() {
        return block;
    }

    @Override
    public Cause getCause() {
        return null;
    }

    @Override
    public Object getSource() {
        return null;
    }

    @Override
    public EventContext getContext() {
        return null;
    }
}