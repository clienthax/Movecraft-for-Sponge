package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.world.World;

public final class CruiseSign implements Listener {

    @EventHandler
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            Block block = location.toBukkit(world).getBlock();
            if(block.getType() == BlockTypes.WALL_SIGN || block.getType() == BlockTypes.STANDING_SIGN){
                Sign sign = (Sign) block.getState();
                if (ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Cruise: ON")) {
                    sign.setLine(0, "Cruise: OFF");
                    sign.update();
                }
            }
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
        if (ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Cruise: OFF")) {
            if (CraftManager.getInstance().getCraftByPlayer(event.getPlayer()) == null) {
                return;
            }
            Craft c = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
            if (!c.getType().getCanCruise()) {
                return;
            }
            //c.resetSigns(false, true, true);
            sign.setLine(0, "Cruise: ON");
            sign.update(true);

            c.setCruiseDirection(sign.getRawData());
            c.setLastCruisUpdate(System.currentTimeMillis());
            c.setCruising(true);
            if (!c.getType().getMoveEntities()) {
                CraftManager.getInstance().addReleaseTask(c);
            }
            return;
        }
        if (ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Cruise: ON")
                && CraftManager.getInstance().getCraftByPlayer(event.getPlayer()) != null
                && CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().getCanCruise()) {
            sign.setLine(0, "Cruise: OFF");
            sign.update(true);
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).setCruising(false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignChange(ChangeSignEvent event) {
        Player player = event.getPlayer();
        if (!event.getLine(0).equalsIgnoreCase("Cruise: OFF") && !event.getLine(0).equalsIgnoreCase("Cruise: ON")) {
            return;
        }
        if (player.hasPermission("movecraft.cruisesign") || !Settings.RequireCreatePerm) {
            return;
        }
        player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
        event.setCancelled(true);
    }
}