package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.craft.CraftType;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.events.CraftReleaseEvent;
import io.github.pulverizer.movecraft.events.CraftRotateEvent;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import io.github.pulverizer.movecraft.utils.MathUtils;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.world.Location;

import java.util.*;

public final class SubcraftRotateSign {
    private static final String HEADER = "Subcraft Rotate";
    private final Set<UUID> rotatingPlayers = new HashSet<>();

    @Listener
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
        if (block.getType() != Material.SIGN_POST && block.getType() != Material.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(HEADER)) {
            return;
        }
        if(rotatingPlayers.contains(event.getPlayer().getUniqueId())){
            event.getPlayer().sendMessage("you are already rotating");
            event.setCancelled(true);
            return;
        }
        // rotate subcraft
        String craftTypeStr = ChatColor.stripColor(sign.getLine(1));
        CraftType type = CraftManager.getInstance().getCraftTypeFromString(craftTypeStr);
        if (type == null) {
            return;
        }
        if (ChatColor.stripColor(sign.getLine(2)).equals("")
                && ChatColor.stripColor(sign.getLine(3)).equals("")) {
            sign.setLine(2, "_\\ /_");
            sign.setLine(3, "/ \\");
            sign.update(false, false);
        }

        if (!event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".pilot") || !event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".rotate")) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }

        final Craft craft = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
        if(craft!=null) {
            if (!craft.isNotProcessing()) {
                event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Parent Craft is busy"));
                return;
            }
            craft.setProcessing(true); // prevent the parent craft from moving or updating until the subcraft is done
            new BukkitRunnable() {
                @Override
                public void run() {
                    craft.setProcessing(false);
                }
            }.runTaskLater(Movecraft.getInstance(), (10));
        }
        final Location loc = event.getClickedBlock().getLocation();
        final Craft subCraft = new Craft(type, loc.getWorld());
        MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        subCraft.detect(null, event.getPlayer(), startPoint);
        rotatingPlayers.add(event.getPlayer().getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                subCraft.rotate(rotation, startPoint, true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        rotatingPlayers.remove(event.getPlayer().getUniqueId());
                        CraftManager.getInstance().removeCraft(subCraft);
                    }
                }.runTaskLater(Movecraft.getInstance(), 3);
            }
        }.runTaskLater(Movecraft.getInstance(), 3);
        event.setCancelled(true);
    }

}