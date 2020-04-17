package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;

/**
 * Permissions Checked
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.2 - 17 Apr 2020
 */
public final class CannonDirectorSign {
    private static final String HEADER = "Cannon Director";

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

        if (!craft.getType().allowCannonDirectorSign()) {
            player.sendMessage(Text.of("ERROR: Cannon Director Signs not allowed on this craft!"));
            return;
        }

        if(event instanceof InteractBlockEvent.Primary && player.getUniqueId() == craft.getCannonDirector()){
            craft.setCannonDirector(null);
            player.sendMessage(Text.of("You are no longer directing the cannons of this craft."));
            return;
        }

        if (!player.hasPermission("movecraft." + craft.getType().getName() + ".crew.directors.cannons") && (craft.getType().requiresSpecificPerms() || !player.hasPermission("movecraft.crew.directors.cannons"))) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        craft.setCannonDirector(player.getUniqueId());
        player.sendMessage(Text.of("You are now directing the cannons of this craft."));
    }
}