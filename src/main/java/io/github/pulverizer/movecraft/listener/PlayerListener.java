package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.utils.HitBox;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.explosive.fireball.Fireball;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.entity.damage.source.EntityDamageSource;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.text.Text;

import java.util.*;

public class PlayerListener {
    private final Map<Craft, Long> timeToReleaseAfter = new WeakHashMap<>();

    @Deprecated
    private String checkCraftBorders(Craft craft) {
        HitBox craftBlocks = craft.getHitBox();
        String ret = null;
        for (MovecraftLocation block : craft.getHitBox()) {
            int x, y, z;
            x = block.getX() + 1;
            y = block.getY();
            z = block.getZ();
            MovecraftLocation test = new MovecraftLocation(x, y, z);
            if (!craft.getHitBox().contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX() - 1;
            y = block.getY();
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY() + 1;
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY() - 1;
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY();
            z = block.getZ() + 1;
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY();
            z = block.getZ() + 1;
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX() + 1;
            y = block.getY() + 1;
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX() + 1;
            y = block.getY() - 1;
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX() - 1;
            y = block.getY() + 1;
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX() - 1;
            y = block.getY() - 1;
            z = block.getZ();
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY() + 1;
            z = block.getZ() + 1;
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY() - 1;
            z = block.getZ() + 1;
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY() + 1;
            z = block.getZ() - 1;
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
            x = block.getX();
            y = block.getY() - 1;
            z = block.getZ() - 1;
            test = new MovecraftLocation(x, y, z);
            if (!craftBlocks.contains(test))
                if (Arrays.binarySearch(craft.getType().getAllowedBlocks(), craft.getW().getBlockType(x, y, z)) >= 0) {
                    ret = "@ " + x + "," + y + "," + z;
                }
        }
        return ret;
    }

    @Listener
    public void onPLayerLogout(ClientConnectionEvent.Disconnect e) {
        CraftManager.getInstance().removeCraftByPlayer(e.getTargetEntity());
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
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("You have left your craft. You may return to your craft by typing /manoverboard any time before the timeout expires.")));
            } else {
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Craft Released - Player has left craft!")));
            }
            if (c.getHitBox().size() > 11000) {
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Craft is too big to check its borders. Make sure this area is safe to release your craft in.")));
            }
            timeToReleaseAfter.put(c, System.currentTimeMillis() + 30000); //30 seconds to release
        }
    }
}