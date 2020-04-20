package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.text.Text;

/**
 * Permissions Checked
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.4 - 20 Apr 2020
 */
public class AntiAircraftDirectorSign {
    private static final String HEADER = "AA Director";

    public static void onSignClick(InteractBlockEvent event, Player player, BlockSnapshot block) {

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }

        event.setCancelled(true);

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (craft == null) {
            player.sendMessage(Text.of("You are not the member of a crew."));
            return;
        }

        if (!craft.getType().allowAADirectorSign()) {
            player.sendMessage(Text.of("ERROR: AA Director Signs not allowed on this craft!"));
            return;
        }

        if (event instanceof InteractBlockEvent.Primary && craft.isAADirector(player.getUniqueId())) {
            craft.resetCrewRole(player.getUniqueId());
            player.sendMessage(Text.of("You are no longer directing the AA of this craft."));
            return;
        }

        if (!player.hasPermission("movecraft." + craft.getType().getName() + ".crew.directors.aa") && (craft.getType().requiresSpecificPerms() || !player.hasPermission("movecraft.crew.directors.aa"))) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        if (craft.addAADirector(player.getUniqueId())) {
            player.sendMessage(Text.of("You can now direct the AA of this craft."));
        } else {
            player.sendMessage(Text.of("You are not in the crew of this craft."));
        }
    }
}