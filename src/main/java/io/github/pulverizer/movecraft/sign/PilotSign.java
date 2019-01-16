package io.github.pulverizer.movecraft.sign;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;

public final class PilotSign implements Listener {
    private static final String HEADER = "Pilot:";
    @EventHandler
    public final void onSignChange(ChangeSignEvent event){
        if (event.getLine(0).equalsIgnoreCase(HEADER)) {
            String pilotName = ChatColor.stripColor(event.getLine(1));
            if (pilotName.isEmpty()) {
                event.setLine(1, event.getPlayer().getName());
            }
        }
    }
}