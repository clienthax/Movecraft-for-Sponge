package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;

public final class MoveSign {
    private static final String HEADER = "Move:";

    public static void onSignClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        if (!lines.get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()) == null) {
            return;
        }

        String[] numbers = lines.get(1).toPlain().split(",");
        int dx = Integer.parseInt(numbers[0]);
        int dy = Integer.parseInt(numbers[1]);
        int dz = Integer.parseInt(numbers[2]);
        int maxMove = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).getType().maxStaticMove();

        if (dx > maxMove)
            dx = maxMove;
        if (dx < -maxMove)
            dx = -maxMove;
        if (dy > maxMove)
            dy = maxMove;
        if (dy < -maxMove)
            dy = -maxMove;
        if (dz > maxMove)
            dz = maxMove;
        if (dz < -maxMove)
            dz = -maxMove;

        if (!player.hasPermission("movecraft." + CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).getType().getName() + ".movement.staticmove")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).getType().getCanStaticMove()) {
            CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).translate(new Vector3i(dx, dy, dz), false);
            //timeMap.put(player, System.currentTimeMillis());
        }
    }
}