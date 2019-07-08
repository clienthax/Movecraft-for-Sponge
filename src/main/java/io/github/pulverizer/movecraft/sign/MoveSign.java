package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;

public final class MoveSign {
    private static final String HEADER = "Move:";

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

        if (!player.hasPermission("movecraft." + CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).getType().getCraftName() + ".move")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).getType().getCanStaticMove()) {
            CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).translate(Rotation.NONE, new Vector3i(dx, dy, dz), false);
            //timeMap.put(player, System.currentTimeMillis());
            CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).setLastCruiseUpdateTime(System.currentTimeMillis());
        }
    }
}