package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.world.World;

public class AscendSign {

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){
                Sign sign = (Sign) block.getExtendedState();
                if (sign.lines().get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
                    sign.setLine(0, "Ascend: OFF");
                    sign.update();
                }
            }
        }
    }


    @Listener
    public void onSignClickEvent(InteractBlockEvent event){
        if (!(event instanceof InteractBlockEvent.Secondary)) {
            return;
        }
        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN){
            return;
        }

        Player player = null;
        if (event.getSource() instanceof Player) {
            player = ((Player) event.getSource()).getPlayer().orElse(null);
        }
        Craft c = CraftManager.getInstance().getCraftByPlayer(player);

        Sign sign = (Sign) block.getExtendedState();
        if (sign.lines().get(0).toPlain().equalsIgnoreCase("Ascend: OFF")) {
            if (c == null) {
                return;
            }
            if (!c.getType().getCanCruise()) {
                return;
            }
            sign.setLine(0, "Ascend: ON");
            sign.update(true);

            c.setCruiseDirection((byte) 0x42);
            c.setLastCruisUpdate(System.currentTimeMillis());
            c.setCruising(true);

            if (!c.getType().getMoveEntities()) {
                CraftManager.getInstance().addReleaseTask(c);
            }
            return;
        }
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
            return;
        }
        if (c == null || !c.getType().getCanCruise()) {
            return;
        }
        sign.setLine(0, "Ascend: OFF");
        sign.update(true);
        c.setCruising(false);
    }
}