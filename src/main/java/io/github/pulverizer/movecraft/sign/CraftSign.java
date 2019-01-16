package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftType;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.world.Location;

public final class CraftSign implements Listener {

    @EventHandler
    public void onSignChange(ChangeSignEvent event){

        if (CraftManager.getInstance().getCraftTypeFromString(event.getLine(0)) == null) {
            return;
        }
        if (!Settings.RequireCreatePerm) {
            return;
        }
        if (!event.getPlayer().hasPermission("movecraft." + ChatColor.stripColor(event.getLine(0)) + ".create")) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public final void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != BlockTypes.STANDING_SIGN && block.getType() != BlockTypes.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        CraftType type = CraftManager.getInstance().getCraftTypeFromString(ChatColor.stripColor(sign.getLine(0)));
        if (type == null) {
            return;
        }
        // Valid sign prompt for ship command.
        if (!event.getPlayer().hasPermission("movecraft." + ChatColor.stripColor(sign.getLine(0)) + ".pilot")) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }
        // Attempt to run detection
        Location loc = event.getClickedBlock().getLocation();
        MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        final Craft c = new ICraft(type, loc.getWorld());

        if (c.getType().getCruiseOnPilot()) {
            c.detect(null, event.getPlayer(), startPoint);
            c.setCruiseDirection(sign.getRawData());
            c.setLastCruisUpdate(System.currentTimeMillis());
            c.setCruising(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    CraftManager.getInstance().removeCraft(c);
                }
            }.runTaskLater(Movecraft.getInstance(), (20 * 15));
        } else {
            if (CraftManager.getInstance().getCraftByPlayer(event.getPlayer()) == null) {
                c.detect(event.getPlayer(), event.getPlayer(), startPoint);
            } else {
                Craft oldCraft = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
                if (oldCraft.isNotProcessing()) {
                    CraftManager.getInstance().removeCraft(oldCraft);
                    c.detect(event.getPlayer(), event.getPlayer(), startPoint);
                }
            }
        }
        event.setCancelled(true);

    }
}