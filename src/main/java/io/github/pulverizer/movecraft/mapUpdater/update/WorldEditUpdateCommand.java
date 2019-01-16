package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.MovecraftLocation;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.block.tileentity.carrier.Dispenser;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.world.World;

public class WorldEditUpdateCommand extends UpdateCommand {
    private final BaseBlock worldEditBaseBlock;
    private World world;
    private MovecraftLocation location;
    private BlockType type;

    public WorldEditUpdateCommand(BaseBlock worldEditBaseBlock, World world, MovecraftLocation location, BlockType type) {
        this.worldEditBaseBlock = worldEditBaseBlock;
        this.world = world;
        this.location = location;
        this.type = type;
    }

    @Override
    public void doUpdate() {
        world.getBlock(location.getX(), location.getY(), location.getZ()).setType(type);
        // put inventory into dispensers if its a repair
        if (type == BlockTypes.DISPENSER) {
            DispenserBlock dispBlock = new DispenserBlock(worldEditBaseBlock.getData());
            dispBlock.setNbtData(worldEditBaseBlock.getNbtData());
            int numFireCharges = 0;
            int numTNT = 0;
            int numWater = 0;
            for (BaseItemStack bi : dispBlock.getItems()) {
                if (bi != null) {
                    if (bi.getType() == 46)
                        numTNT += bi.getAmount();
                    if (bi.getType() == 385)
                        numFireCharges += bi.getAmount();
                    if (bi.getType() == 326)
                        numWater += bi.getAmount();
                }
            }
            Dispenser disp = (Dispenser) world.getBlockAt(location.getX(), location.getY(), location.getZ()).getState();
            if (numFireCharges > 0) {
                ItemStack fireItems = new ItemStack(ItemTypes.FIRE_CHARGE, numFireCharges);
                disp.getInventory().addItem(fireItems);
            }
            if (numTNT > 0) {
                ItemStack TNTItems = new ItemStack(ItemTypes.TNT, numTNT);
                disp.getInventory().addItem(TNTItems);
            }
            if (numWater > 0) {
                ItemStack WaterItems = new ItemStack(ItemTypes.WATER_BUCKET, numWater);
                disp.getInventory().addItem(WaterItems);
            }
        }
        if (worldEditBaseBlock instanceof SignBlock) {
            BlockState state = world.getBlock(location.getX(), location.getY(), location.getZ());
            if (state instanceof Sign) {
                Sign s = (Sign) state;
                SignBlock signBlock = (SignBlock) worldEditBaseBlock;
                for (int line = 0; line < signBlock.getText().length; line++) {
                    s.setLine(line, signBlock.getText()[line]);
                }
                s.update(false, false);
            }
        }
        //might have issues due to repair order
    }
}
