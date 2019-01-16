package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;

public final class HelmSign implements Listener {

    @EventHandler
    public final void onSignChange(ChangeSignEvent event){
        if (!ChatColor.stripColor(event.getLine(0)).equalsIgnoreCase("[helm]")) {
            return;
        }
        event.setLine(0, "\\  ||  /");
        event.setLine(1, "==      ==");
        event.setLine(2, "/  ||  \\");
    }

    @EventHandler
    public final void onSignClick(PlayerInteractEvent event) {
        Rotation rotation;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            rotation = Rotation.CLOCKWISE;
        }else if(event.getAction() == Action.LEFT_CLICK_BLOCK){
            rotation = Rotation.ANTICLOCKWISE;
        }else{
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != BlockTypes.STANDING_SIGN && block.getType() != BlockTypes.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!(ChatColor.stripColor(sign.getLine(0)).equals("\\  ||  /") &&
                ChatColor.stripColor(sign.getLine(1)).equals("==      ==") &&
                ChatColor.stripColor(sign.getLine(2)).equals("/  ||  \\"))) {
            return;
        }
        Craft craft = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
        if (craft == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("movecraft." + craft.getType().getCraftName() + ".rotate")) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }

        if (!MathUtils.locationInHitbox(craft.getHitBox(), event.getPlayer().getLocation())) {
            return;
        }

        if (craft.getType().rotateAtMidpoint()) {
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).rotate(rotation, craft.getHitBox().getMidPoint());
        } else {
            CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).rotate(rotation, MathUtils.bukkit2MovecraftLoc(sign.getLocation()));
        }

        //timeMap.put(event.getPlayer(), System.currentTimeMillis());
        event.setCancelled(true);
        //TODO: Lower speed while turning
            /*int curTickCooldown = CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getCurTickCooldown();
            int baseTickCooldown = CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).getType().getCruiseTickCooldown();
            if (curTickCooldown * 2 > baseTickCooldown)
                curTickCooldown = baseTickCooldown;
            else
                curTickCooldown = curTickCooldown * 2;*/
        //CraftManager.getInstance().getCraftByPlayer(event.getPlayer()).setCurTickCooldown(curTickCooldown); // lose half your speed when turning

    }
}