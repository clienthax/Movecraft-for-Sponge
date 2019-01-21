package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.World;

import java.util.Objects;


public class BlockCreateCommand extends UpdateCommand {

    final private MovecraftLocation newBlockLocation;
    final private BlockSnapshot block;
    final private World world;

    public BlockCreateCommand(MovecraftLocation newBlockLocation, BlockSnapshot block, Craft craft) {
        this.newBlockLocation = newBlockLocation;
        this.block = block;
        this.world = craft.getW();
    }

    public BlockCreateCommand(World world, MovecraftLocation newBlockLocation, BlockSnapshot block) {
        this.newBlockLocation = newBlockLocation;
        this.block = block;
        this.world = world;
    }


    @Override
    @SuppressWarnings("deprecation")
    public void doUpdate() {
        // now do the block updates, move entities when you set the block they are on
        Movecraft.getInstance().getWorldHandler().setBlockFast(newBlockLocation.toSponge(world), block);
        //craft.incrementBlockUpdates();
        newBlockLocation.toSponge(world).addScheduledUpdate(1, 0);

        //TODO: Re-add sign updating
    }

    @Override
    public int hashCode() {
        return Objects.hash(newBlockLocation, block, world.getUniqueId());
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof BlockCreateCommand)){
            return false;
        }
        BlockCreateCommand other = (BlockCreateCommand) obj;
        return other.newBlockLocation.equals(this.newBlockLocation) &&
                other.block.equals(this.block) &&
                other.world.equals(this.world);
    }
}