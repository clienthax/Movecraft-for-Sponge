package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.utils.HitBox;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.text.Text;

import java.util.*;

public class PlayerListener {
    private final Map<Craft, Long> timeToReleaseAfter = new WeakHashMap<>();

    @Listener
    public void onPLayerLogout(ClientConnectionEvent.Disconnect event) {
        CraftManager.getInstance().removeCraftByPlayer(event.getTargetEntity());
    }

    /*
    @Listener
    //TODO: Change to still fly, if (!crew.size().isEmpty())
    public void onPlayerDeath(DamageEntityEvent e, @Root(typeFilter = {Fireball.class, PrimedTNT.class}) EntityDamageSource damageSource) {  // changed to death so when you shoot up an airship and hit the pilot, it still sinks
        if (e.getTargetEntity() instanceof Player) {
            Player p = (Player) e.getTargetEntity();
            if (e.willCauseDeath())
                CraftManager.getInstance().removeCraft(CraftManager.getInstance().getCraftByPlayer(p));
        }
    }
    */

    @Listener
    public void onPlayerMove(MoveEntityEvent event, @Root Player player) {
        final Craft c = CraftManager.getInstance().getCraftByPlayer(player);
        if (c == null) {
            return;
        }

        if(MathUtils.locationNearHitBox(c.getHitBox(), player.getLocation(), 2)){
            timeToReleaseAfter.remove(c);
            return;
        }

        if(timeToReleaseAfter.containsKey(c) && timeToReleaseAfter.get(c) < System.currentTimeMillis()){
            CraftManager.getInstance().removeCraft(c);
            timeToReleaseAfter.remove(c);
            return;
        }

        if (c.isNotProcessing() && c.getType().getMoveEntities() && !timeToReleaseAfter.containsKey(c)) {
            if (Settings.ManOverBoardTimeout != 0) {
                player.sendMessage(Text.of("You have left your craft.")); //TODO: Re-add /manoverboard
            } else {
                player.sendMessage(Text.of("You have released your craft."));
            }
            if (c.getHitBox().size() > 11000) {
                player.sendMessage(Text.of("Craft is too big to check its borders. Make sure this area is safe to release your craft in."));
            }
            timeToReleaseAfter.put(c, System.currentTimeMillis() + 30000); //30 seconds to release
        }
    }
}