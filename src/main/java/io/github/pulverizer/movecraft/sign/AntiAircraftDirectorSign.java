package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.craft.crew.CrewManager;
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
 * @version 1.5 - 23 Apr 2020
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

        if (event instanceof InteractBlockEvent.Primary) {
            CrewManager.getInstance().resetRole(player);
            return;
        }

        CrewManager.getInstance().addAADirector(player);
    }
}