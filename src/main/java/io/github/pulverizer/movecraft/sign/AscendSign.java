package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
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
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){

                if (!location.toSponge(world).getTileEntity().isPresent())
                    return;

                Sign sign = (Sign) location.toSponge(world).getTileEntity().get();
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

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player);

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: OFF")) {
            if (craft == null || !craft.getType().getCanCruise()) {
                player.sendMessage(Text.of("You are not piloting a craft!"));
                return;
            }

            event.setCancelled(true);

            lines.set(0, Text.of("Ascend: ON"));
            sign.offer(lines);

            craft.setCruiseDirection(Direction.UP);
            craft.setLastCruiseUpdateTime(System.currentTimeMillis());
            craft.setState(CraftState.CRUISING);

            if (!craft.getType().getMoveEntities()) {
                CraftManager.getInstance().addReleaseTask(craft);
            }
            return;
        }
        if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
            if (craft == null || !craft.getType().getCanCruise()) {
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