package io.github.pulverizer.movecraft.sign;

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

public final class DescendSign {

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getWorld();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
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
            if (CraftManager.getInstance().getCraftByPlayer(player) == null) {
                return;
            }

            Craft c = CraftManager.getInstance().getCraftByPlayer(player);
            if (!c.getType().getCanCruise()) {
                return;
            }

            //c.resetSigns(true, true, false);
            lines.set(0, Text.of("Descend: ON"));
            sign.offer(lines);

            c.setCruiseDirection(Direction.DOWN);
            c.setLastCruiseUpdateTime(System.currentTimeMillis());
            c.setCruising(true);

            if (!c.getType().getMoveEntities()) {
                CraftManager.getInstance().addReleaseTask(c);
            }
            return;
        }
        if (lines.get(0).toPlain().equalsIgnoreCase("Descend: ON")) {
            Craft c = CraftManager.getInstance().getCraftByPlayer(player);
            if (c != null && c.getType().getCanCruise()) {
                lines.set(0, Text.of("Descend: OFF"));
                sign.offer(lines);
                c.setCruising(false);
            }
        }
    }
}