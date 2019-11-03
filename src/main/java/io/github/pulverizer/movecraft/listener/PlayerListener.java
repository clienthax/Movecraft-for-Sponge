package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.text.Text;

import java.util.*;

public class PlayerListener {
    private final Map<Craft, Long> timeToReleaseAfter = new WeakHashMap<>();

    @Listener
    public void onPLayerLogout(ClientConnectionEvent.Disconnect event) {

        UUID playerID = event.getTargetEntity().getUniqueId();
        Craft craft = CraftManager.getInstance().getCraftByPlayer(playerID);

        craft.removeCrewMember(playerID);

        if (craft.getCrewList().isEmpty())
            CraftManager.getInstance().removeCraft(craft);
    }


    @Listener
    public void onPlayerDeath(DestructEntityEvent.Death event, @Getter("getTargetEntity") Player player) {

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (craft == null)
            return;

        if (craft.getCommander() == player.getUniqueId()) {
            craft.setCommander(craft.getNextInCommand());
        }

        craft.removeCrewMember(player.getUniqueId());

        //TODO: Change to not release but allow the ship to keep cruising and be sunk or claimed.
        if (craft.getCrewList().isEmpty())
            CraftManager.getInstance().removeCraft(craft);
    }

    @Listener
    public void onPlayerMove(MoveEntityEvent event, @Root Player player) {
        final Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (craft == null) {
            return;
        }

        if(MathUtils.locationNearHitBox(craft.getHitBox(), player.getPosition(), 2)){
            timeToReleaseAfter.remove(craft);
            return;
        }

        if(timeToReleaseAfter.containsKey(craft) && timeToReleaseAfter.get(craft) < System.currentTimeMillis()){
            CraftManager.getInstance().removeCraft(craft);
            timeToReleaseAfter.remove(craft);
            return;
        }

        if (craft.isNotProcessing() && craft.getType().getMoveEntities() && !timeToReleaseAfter.containsKey(craft)) {
            if (Settings.ManOverBoardTimeout != 0) {
                player.sendMessage(Text.of("You have left your craft.")); //TODO: Re-add /manoverboard
            } else {
                player.sendMessage(Text.of("You have released your craft."));
            }
            if (craft.getHitBox().size() > 11000) {
                player.sendMessage(Text.of("Craft is too big to check its borders. Make sure this area is safe to release your craft in."));
            }
            timeToReleaseAfter.put(craft, System.currentTimeMillis() + 30000); //30 seconds to release
        }
    }
}