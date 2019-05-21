package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.BlockChangeFlags;

import java.util.HashMap;
import java.util.Map;

public final class InteractListener {
    private static final Map<Player, Long> timeMap = new HashMap<>();

    @Listener
    public final void onPlayerInteract(InteractBlockEvent.Primary event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (!block.getState().getType().equals(BlockTypes.WOODEN_BUTTON) && !block.getState().getType().equals(BlockTypes.STONE_BUTTON)) {
            return;
        }
        // if they left click a button which is pressed, unpress it
        if (block.get(Keys.POWERED).orElse(false)) {
            block = block.with(Keys.POWERED, false).get();
            block.getLocation().get().restoreSnapshot(block, true, BlockChangeFlags.ALL);
        }
    }

    @Listener
    @Include({InteractItemEvent.Primary.class, InteractItemEvent.Secondary.MainHand.class})
    public void onPlayerInteractStick(InteractItemEvent event, @Root Player player) {

        Craft c = CraftManager.getInstance().getCraftByPlayer(player);
        // if not in command of craft, don't process pilot tool clicks
        if (c == null)
            return;

        if (event instanceof InteractItemEvent.Secondary) {
            Craft craft = CraftManager.getInstance().getCraftByPlayer(player);

            if (player.getItemInHand(HandTypes.MAIN_HAND).get().getType() != Settings.PilotTool) {
                return;
            }

            if (craft == null) {
                return;
            }

            event.setCancelled(true);

            Long time = timeMap.get(player);
            if (time != null) {
                long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                // if the craft should go slower underwater, make time
                // pass more slowly there
                if (craft.getType().getHalfSpeedUnderwater() && craft.getHitBox().getMinY() < craft.getWorld().getSeaLevel())
                    ticksElapsed = ticksElapsed >> 1;

                if (Math.abs(ticksElapsed) < craft.getType().getTickCooldown()) {
                    return;
                }
            }

            if (!MathUtils.locationNearHitBox(craft.getHitBox(),player.getLocation(),2)) {
                return;
            }

            if (!player.hasPermission("movecraft." + craft.getType().getCraftName() + ".move")) {
                player.sendMessage(Text.of("Insufficient Permissions"));
                return;
            }
            if (craft.getDirectControl()) {
                // right click moves up or down if using direct
                // control
                int DY = 1;
                if (player.get(Keys.IS_SNEAKING).get())
                    DY = -1;

                craft.translate(0, DY, 0);
                timeMap.put(player, System.currentTimeMillis());
                craft.setLastCruiseUpdateTime(System.currentTimeMillis());
                return;
            }
            // Player is onboard craft and right clicking
            float rotation = (float) Math.PI * (float) player.getRotation().getY() / 180f;

            float nx = -(float) Math.sin(rotation);
            float nz = (float) Math.cos(rotation);

            int dx = (Math.abs(nx) >= 0.5 ? 1 : 0) * (int) Math.signum(nx);
            int dz = (Math.abs(nz) > 0.5 ? 1 : 0) * (int) Math.signum(nz);
            int dy;

            float p = (float) player.getRotation().getX();

            dy = -(Math.abs(p) >= 25 ? 1 : 0) * (int) Math.signum(p);

            if (Math.abs(player.getRotation().getX()) >= 75) {
                dx = 0;
                dz = 0;
            }

            craft.translate(dx, dy, dz);
            timeMap.put(player, System.currentTimeMillis());
            craft.setLastCruiseUpdateTime(System.currentTimeMillis());
            return;
        }
        if (event instanceof InteractItemEvent.Primary) {
            if (player.getItemInHand(HandTypes.MAIN_HAND).get().getType() != Settings.PilotTool) {
                return;
            }
            Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
            if (craft == null) {
                return;
            }

            if (craft.getDirectControl()) {
                craft.setDirectControl(false);
                player.sendMessage(Text.of("Leaving Direct Control Mode"));
                event.setCancelled(true);
                return;
            }
            if (!player.hasPermission("movecraft." + craft.getType().getCraftName() + ".move")
                    || !craft.getType().getCanDirectControl()) {
                player.sendMessage(Text.of("Insufficient Permissions"));
                return;
            }
            craft.setDirectControl(true);
            craft.setPilotLockedX(player.getLocation().getBlockX() + 0.5);
            craft.setPilotLockedY(player.getLocation().getY());
            craft.setPilotLockedZ(player.getLocation().getBlockZ() + 0.5);
            player.sendMessage(Text.of("Entering Direct Control Mode"));
            event.setCancelled(true);
        }

    }

}