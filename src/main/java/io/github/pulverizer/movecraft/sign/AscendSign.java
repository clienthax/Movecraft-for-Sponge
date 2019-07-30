package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.CraftState;
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

public class AscendSign {

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getWorld();
        for(Vector3i location: event.getCraft().getHitBox()){
            BlockSnapshot block = MovecraftLocation.toSponge(world, location).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){

                if (!MovecraftLocation.toSponge(world, location).getTileEntity().isPresent())
                    return;

                Sign sign = (Sign) MovecraftLocation.toSponge(world, location).getTileEntity().get();
                ListValue<Text> lines = sign.lines();
                if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
                    lines.set(0, Text.of("Ascend: OFF"));
                    sign.offer(lines);
                }
            }
        }
    }


    @Listener
    public void onSignClickEvent(InteractBlockEvent.Secondary.MainHand event, @Root Player player){

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN){
            return;
        }

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent() || craft.getPilot() != player.getUniqueId())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: OFF")) {
            if (!craft.getType().getCanCruise()) {
                //TODO: Find a better message.
                player.sendMessage(Text.of("You are not piloting a craft!"));
                return;
            }

            event.setCancelled(true);

            lines.set(0, Text.of("Ascend: ON"));
            sign.offer(lines);

            craft.setCruiseDirection(Direction.UP);
            craft.setLastCruiseUpdateTick(Sponge.getServer().getRunningTimeTicks());
            craft.setState(CraftState.CRUISING);

            return;
        }

        if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
            if (!craft.getType().getCanCruise()) {
                //TODO: Find a better message.
                player.sendMessage(Text.of("You are not piloting a craft!"));
                return;
            }

            event.setCancelled(true);
            lines.set(0, Text.of("Ascend: OFF"));
            sign.offer(lines);
            craft.setState(CraftState.STOPPED);
        }
    }
}