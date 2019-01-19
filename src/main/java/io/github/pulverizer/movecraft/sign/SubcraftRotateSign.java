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
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

public final class SubcraftRotateSign {
    private static final String HEADER = "Subcraft Rotate";
    private final Set<UUID> rotatingPlayers = new HashSet<>();

    @Listener
    public final void onSignClick(InteractBlockEvent event) {
        if(!(event instanceof InteractBlockEvent.Primary) && !(event instanceof InteractBlockEvent.Secondary)) {
            return;
        }

        Rotation rotation;
        if (event instanceof InteractBlockEvent.Secondary) {
            rotation = Rotation.CLOCKWISE;
        }else if(event instanceof InteractBlockEvent.Primary){
            rotation = Rotation.ANTICLOCKWISE;
        }else{
            return;
        }
        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) event.getTargetBlock().getExtendedState();
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }

        Player player = null;
        if (event.getSource() instanceof Player && ((Player) event.getSource()).getPlayer().isPresent()) {
            player = ((Player) event.getSource()).getPlayer().get();
        }

        if (player == null)
            return;

        if (!event.getTargetBlock().getLocation().isPresent())
            return;

        if(rotatingPlayers.contains(player.getUniqueId())){
            player.sendMessage(Text.of("You are already rotating!"));
            event.setCancelled(true);
            return;
        }
        // rotate subcraft
        String craftTypeStr = sign.lines().get(1).toPlain();
        CraftType type = CraftManager.getInstance().getCraftTypeFromString(craftTypeStr);
        if (type == null) {
            return;
        }
        if (sign.lines().get(2).toPlain().equalsIgnoreCase("") && sign.lines().get(3).toPlain().equalsIgnoreCase("")) {
            sign.setLine(2, "_\\ /_");
            sign.setLine(3, "/ \\");
            sign.update(false, false);
        }

        if (!player.hasPermission("movecraft." + craftTypeStr + ".pilot") || !player.hasPermission("movecraft." + craftTypeStr + ".rotate")) {
            player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Insufficient Permissions")));
            return;
        }

        final Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
        if(craft!=null) {
            if (!craft.isNotProcessing()) {
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Parent Craft is busy")));
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

        final Location<World> loc = event.getTargetBlock().getLocation().get();
        final Craft subCraft = new Craft(type, loc.getExtent());
        MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        subCraft.detect(null, player, startPoint);
        rotatingPlayers.add(player.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                subCraft.rotate(rotation, startPoint, true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        rotatingPlayers.remove(player.getUniqueId());
                        CraftManager.getInstance().removeCraft(subCraft);
                    }
                }.runTaskLater(Movecraft.getInstance(), 3);
            }
        }.runTaskLater(Movecraft.getInstance(), 3);
        event.setCancelled(true);
    }

}