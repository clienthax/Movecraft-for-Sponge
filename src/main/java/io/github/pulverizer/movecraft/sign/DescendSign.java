package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.World;

public final class DescendSign {

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getWorld();
        for(Vector3i location: event.getCraft().getHitBox()){
            BlockSnapshot block = MovecraftLocation.toSponge(world, location).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){

                if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
                    return;

                Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
                ListValue<Text> lines = sign.lines();
                if (lines.get(0).toPlain().equalsIgnoreCase("Descend: ON")) {
                    lines.set(0, Text.of("Descend: OFF"));
                    sign.offer(lines);
                }
            }
        }
    }

    @Listener
    public final void onSignClick(InteractBlockEvent.Secondary.MainHand event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (lines.get(0).toPlain().equalsIgnoreCase("Descend: OFF")) {
            if (CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()) == null) {
                return;
            }

            Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());
            if (!craft.getType().getCanCruise() || player.getUniqueId() != craft.getPilot()) {
                return;
            }

            //craft.resetSigns(true, true, false);
            lines.set(0, Text.of("Descend: ON"));
            sign.offer(lines);

            craft.setCruiseDirection(Direction.DOWN);
            craft.setLastCruiseUpdateTick(Sponge.getServer().getRunningTimeTicks());
            craft.setState(CraftState.CRUISING);

            return;
        }
        if (lines.get(0).toPlain().equalsIgnoreCase("Descend: ON")) {
            Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());
            if (craft != null && craft.getType().getCanCruise() && player.getUniqueId() == craft.getPilot()) {
                lines.set(0, Text.of("Descend: OFF"));
                sign.offer(lines);
                craft.setState(CraftState.STOPPED);
            }
        }
    }
}