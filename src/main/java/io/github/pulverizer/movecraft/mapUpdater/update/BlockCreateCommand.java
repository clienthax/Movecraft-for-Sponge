package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.World;

import java.util.Objects;


public class BlockCreateCommand extends UpdateCommand {

    final private MovecraftLocation newBlockLocation;
    final private BlockType type;
    final private World world;

    public BlockCreateCommand(MovecraftLocation newBlockLocation, BlockType type, Craft craft) {
        this.newBlockLocation = newBlockLocation;
        this.type = type;
        this.world = craft.getW();
    }

    public BlockCreateCommand(World world, MovecraftLocation newBlockLocation, BlockType type) {
        this.newBlockLocation = newBlockLocation;
        this.type = type;
        this.world = world;
    }


    @Override
    @SuppressWarnings("deprecation")
    public void doUpdate() {
        // now do the block updates, move entities when you set the block they are on
        Movecraft.getInstance().getWorldHandler().setBlockFast(newBlockLocation.toSponge(world),type);
        //craft.incrementBlockUpdates();
        newBlockLocation.toSponge(world).getBlock().getState().update(false, false);

        //Do comperator stuff

        if (type == BlockTypes.UNPOWERED_COMPARATOR) { // for some reason comparators are flakey, have to do it twice sometimes
            //Block b = updateWorld.getBlockAt(newBlockLocation.getX(), newBlockLocation.getY(), newBlockLocation.getZ());
            BlockState b = newBlockLocation.toSponge(world).getBlock();
            if (b.getType() != BlockTypes.UNPOWERED_COMPARATOR) {
                b.setTypeIdAndData(type.getId(), dataID, false);
            }
        }
        if (type == BlockTypes.POWERED_COMPARATOR) { // for some reason comparators are flakey, have to do it twice sometimes
            BlockState b = newBlockLocation.toSponge(world).getBlock();
            if (b.getType() != BlockTypes.POWERED_COMPARATOR) {
                b.setTypeIdAndData(type, false);
            }
        }

        //TODO: Re-add sign updating
    }

    @Override
    public int hashCode() {
        return Objects.hash(newBlockLocation, type, world.getUniqueId());
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof BlockCreateCommand)){
            return false;
        }
        BlockCreateCommand other = (BlockCreateCommand) obj;
        return other.newBlockLocation.equals(this.newBlockLocation) &&
                other.type.equals(this.type) &&
                other.world.equals(this.world);
    }
}