package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;

public final class TeleportSign {
    private static final String HEADER = "Teleport:";
    @Listener
    public final void onSignClick(InteractBlockEvent.Secondary.MainHand event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        if (!block.getLocation().isPresent() && !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }

        if (CraftManager.getInstance().getCraftByPlayer(player) == null) {
            return;
        }
        String[] numbers = sign.lines().get(1).toPlain().split(",");
        int tX = Integer.parseInt(numbers[0]);
        int tY = Integer.parseInt(numbers[1]);
        int tZ = Integer.parseInt(numbers[2]);

        if (player != null && !player.hasPermission("movecraft." + CraftManager.getInstance().getCraftByPlayer(player).getType().getCraftName() + ".move")) {
            player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Insufficient Permissions")));
            return;
        }
        if (CraftManager.getInstance().getCraftByPlayer(player).getType().getCanTeleport()) {
            int dx = tX - block.getLocation().get().getBlockPosition().getX();
            int dy = tY - block.getLocation().get().getBlockPosition().getY();
            int dz = tZ - block.getLocation().get().getBlockPosition().getZ();
            CraftManager.getInstance().getCraftByPlayer(player).translate(dx, dy, dz);
        }
    }
}