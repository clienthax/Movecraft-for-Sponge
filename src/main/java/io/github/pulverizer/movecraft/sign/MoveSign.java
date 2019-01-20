package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;

public final class MoveSign {
    private static final String HEADER = "Move:";

    @Listener
    public final void onSignClick(InteractBlockEvent.Secondary.MainHand event) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(HEADER)) {
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(event.getPlayer()) == null) {
            return;
        }

        String[] numbers = ChatColor.stripColor(sign.getLine(1)).split(",");
        int dx = Integer.parseInt(numbers[0]);
        int dy = Integer.parseInt(numbers[1]);
        int dz = Integer.parseInt(numbers[2]);
        int maxMove = CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().maxStaticMove();

        if (dx > maxMove)
            dx = maxMove;
        if (dx < 0 - maxMove)
            dx = 0 - maxMove;
        if (dy > maxMove)
            dy = maxMove;
        if (dy < 0 - maxMove)
            dy = 0 - maxMove;
        if (dz > maxMove)
            dz = maxMove;
        if (dz < 0 - maxMove)
            dz = 0 - maxMove;

        if (!event.getPlayer().hasPermission("movecraft." + CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().getCraftName() + ".move")) {
            event.getPlayer().sendMessage(
                    I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().getCanStaticMove()) {
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).translate(dx, dy, dz);
            //timeMap.put(event.getPlayer(), System.currentTimeMillis());
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).setLastCruiseUpdate(System.currentTimeMillis());
        }
    }
}