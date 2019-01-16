package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;

public final class RelativeMoveSign implements Listener {
    private static final String HEADER = "RMove:";

    @EventHandler
    public final void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != BlockTypes.STANDING_SIGN && block.getType() != BlockTypes.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(HEADER)) {
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(event.getPlayer()) == null) {
            return;
        }
        String[] numbers = ChatColor.stripColor(sign.getLine(1)).split(",");
        int dLeftRight = Integer.parseInt(numbers[0]); // negative =
        // left,
        // positive =
        // right
        int dy = Integer.parseInt(numbers[1]);
        int dBackwardForward = Integer.parseInt(numbers[2]); // negative
        // =
        // backwards,
        // positive
        // =
        // forwards
        int maxMove = CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().maxStaticMove();

        if (dLeftRight > maxMove)
            dLeftRight = maxMove;
        if (dLeftRight < -maxMove)
            dLeftRight = -maxMove;
        if (dy > maxMove)
            dy = maxMove;
        if (dy < -maxMove)
            dy = -maxMove;
        if (dBackwardForward > maxMove)
            dBackwardForward = maxMove;
        if (dBackwardForward < -maxMove)
            dBackwardForward = -maxMove;
        int dx = 0;
        int dz = 0;
        switch (sign.getRawData()) {
            case 0x3:
                // North
                dx = dLeftRight;
                dz = -dBackwardForward;
                break;
            case 0x2:
                // South
                dx = -dLeftRight;
                dz = dBackwardForward;
                break;
            case 0x4:
                // East
                dx = dBackwardForward;
                dz = dLeftRight;
                break;
            case 0x5:
                // West
                dx = -dBackwardForward;
                dz = -dLeftRight;
                break;
        }

        if (!event.getPlayer().hasPermission("movecraft." + CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().getCraftName() + ".move")) {
            event.getPlayer().sendMessage(
                    I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().getCanStaticMove()) {
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).translate(dx, dy, dz);
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).setLastCruisUpdate(System.currentTimeMillis());
        }
    }
}