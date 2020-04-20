package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.utils.MathUtils;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;

/**
 * Permissions checked
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.4 - 20 Apr 2020
 */
public class PilotSign {
    private static final String HEADER = "Pilot";

    public static void onSignClick(InteractBlockEvent event, Player player, BlockSnapshot block) {

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }

        Craft foundCraft = null;
        World blockWorld = block.getLocation().get().getExtent();
        Vector3i blockPosition = block.getLocation().get().getBlockPosition();
        //TODO - Add compatibility for being the pilot of a subcraft
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(blockWorld)) {
            if (tcraft.getHitBox().contains(blockPosition)) {
                foundCraft = tcraft;
                break;
            }
        }

        if (foundCraft == null) {
            if (player != null) {player.sendMessage(Text.of("ERROR: Sign must be a part of a piloted craft!"));}
            event.setCancelled(true);
            return;
        }

        if(event instanceof InteractBlockEvent.Primary && player.getUniqueId() == foundCraft.getPilot()){
            foundCraft.resetCrewRole(player.getUniqueId());
            player.sendMessage(Text.of("You are no longer the pilot of this craft."));
            event.setCancelled(true);
            return;
        }

        if (!player.hasPermission("movecraft." + foundCraft.getType().getName() + ".crew.pilot") && (foundCraft.getType().requiresSpecificPerms() || !player.hasPermission("movecraft.crew.pilot"))) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        if(foundCraft.setPilot(player.getUniqueId())) {
            player.sendMessage(Text.of("You are now the pilot."));
        } else {
            player.sendMessage(Text.of("You are not in a crew!"));
        }

        event.setCancelled(true);
    }
}