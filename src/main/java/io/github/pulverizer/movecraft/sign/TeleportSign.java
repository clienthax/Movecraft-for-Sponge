package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;

/**
 * Add Permissions:
 * - Create Sign
 *
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.2 - 17 Apr 2020
 */
public final class TeleportSign {
    private static final String HEADER = "Teleport:";

    public static void onSignClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (craft == null || player.getUniqueId() != craft.getPilot()) {
            player.sendMessage(Text.of("You are not piloting a craft."));
            return;
        }
        String[] numbers = sign.lines().get(1).toPlain().split(",");
        int tX = Integer.parseInt(numbers[0]);
        int tY = Integer.parseInt(numbers[1]);
        int tZ = Integer.parseInt(numbers[2]);

        if (!player.hasPermission("movecraft." + craft.getType().getName() + ".movement.teleport") && (craft.getType().requiresSpecificPerms() || !player.hasPermission("movecraft.movement.teleport"))) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        if (craft.getType().getCanTeleport()) {
            int dx = tX - block.getLocation().get().getBlockPosition().getX();
            int dy = tY - block.getLocation().get().getBlockPosition().getY();
            int dz = tZ - block.getLocation().get().getBlockPosition().getZ();
            craft.translate(new Vector3i(dx, dy, dz), false);
        }
    }
}