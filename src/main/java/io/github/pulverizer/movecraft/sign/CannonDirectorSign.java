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

        Craft foundCraft = null;
        World blockWorld = block.getLocation().get().getExtent();
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(blockWorld)) {
            if (MathUtils.locationInHitbox(tcraft.getHitBox(), block.getLocation().get()) && !tcraft.getCrewList().isEmpty()) {
                foundCraft = tcraft;
                break;
            }
        }

        if (foundCraft == null) {
            if (player != null) {
                player.sendMessage(Text.of("ERROR: Sign must be a part of a piloted craft!"));
            }
            return;
        }

        if (!foundCraft.getType().allowCannonDirectorSign()) {
            if (player != null) {
                player.sendMessage(Text.of("ERROR: Cannon Director Signs not allowed on this craft!"));
            }
            return;
        }
        if(event instanceof InteractBlockEvent.Primary && player.getUniqueId() == foundCraft.getCannonDirector()){
            foundCraft.setCannonDirector(null);
            if (player != null) {
                player.sendMessage(Text.of("You are no longer directing the cannons of this craft."));
            }
            return;
        }


        foundCraft.setCannonDirector(player.getUniqueId());
        if(player != null) {
            player.sendMessage(Text.of("You are now directing the cannons of this craft."));
        }
        if (foundCraft.getAADirector() == player.getUniqueId())
            foundCraft.setAADirector(null);

        if (foundCraft.getPilot() == player.getUniqueId())
            foundCraft.setPilot(null);

    }
}