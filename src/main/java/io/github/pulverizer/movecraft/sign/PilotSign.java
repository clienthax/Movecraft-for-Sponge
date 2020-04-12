package io.github.pulverizer.movecraft.sign;

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
 * Permissions to be reviewed
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.0 - 12 Apr 2020
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
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(blockWorld)) {
            if (MathUtils.locationInHitbox(tcraft.getHitBox(), block.getLocation().get()) && !tcraft.getCrewList().isEmpty()) {
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
            foundCraft.setPilot(null);
            player.sendMessage(Text.of("You are no longer the pilot of this craft."));
            event.setCancelled(true);
            return;
        }

        if(player != null && foundCraft.isCrewMember(player.getUniqueId())) {
            foundCraft.setPilot(player.getUniqueId());
            player.sendMessage(Text.of("You are now the pilot of this craft."));
        }

        if (foundCraft.getCannonDirector() == player.getUniqueId())
            foundCraft.setCannonDirector(null);

        if (foundCraft.getAADirector() == player.getUniqueId())
            foundCraft.setAADirector(null);

        event.setCancelled(true);
    }
}