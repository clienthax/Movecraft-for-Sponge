package io.github.pulverizer.movecraft.mapUpdater.update;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.world.World;

import java.util.Objects;


public class BlockCreateCommand extends UpdateCommand {

    final private Vector3i newBlockLocation;
    final private BlockSnapshot block;
    final private World world;

    public BlockCreateCommand(World world, Vector3i newBlockLocation, BlockSnapshot block) {
        this.newBlockLocation = newBlockLocation;
        this.block = block.withLocation(MovecraftLocation.toSponge(world, newBlockLocation));
        this.world = world;
    }



    public BlockCreateCommand(World world, Vector3i newBlockLocation, BlockType block) {
        this.newBlockLocation = newBlockLocation;
        this.block = block.getDefaultState().snapshotFor(MovecraftLocation.toSponge(world, newBlockLocation));
        this.world = world;
    }


    @Override
    public void doUpdate() {
        // now do the block updates, move entities when you set the block they are on
        Movecraft.getInstance().getWorldHandler().setBlock(MovecraftLocation.toSponge(world, newBlockLocation), block);
        //craft.incrementBlockUpdates();

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