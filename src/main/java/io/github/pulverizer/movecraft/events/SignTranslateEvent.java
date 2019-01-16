package io.github.pulverizer.movecraft.events;

import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.event.cause.Cause;


public class SignTranslateEvent extends CraftEvent{
    private final BlockSnapshot block;
    private String[] lines;

    public SignTranslateEvent(BlockSnapshot block, Craft craft, String[] lines) throws IndexOutOfBoundsException{
        super(craft);
        this.block = block;
        if(lines.length!=4)
            throw new IndexOutOfBoundsException();
        this.lines=lines;
    }

    public String[] getLines() {
        return lines;
    }

    public String getLine(int index) throws IndexOutOfBoundsException{
        if(index > 3 || index < 0)
            throw new IndexOutOfBoundsException();
        return lines[index];
    }

    public void setLine(int index, String line){
        if(index > 3 || index < 0)
            throw new IndexOutOfBoundsException();
        lines[index]=line;
    }

    public BlockSnapshot getBlock() {
        return block;
    }

    @Override
    public Cause getCause() {
        return null;
    }
}