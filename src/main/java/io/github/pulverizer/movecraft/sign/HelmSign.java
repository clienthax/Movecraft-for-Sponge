package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.network.PlayerConnection;
import org.spongepowered.api.text.Text;

/**
 * Permissions Checked
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.4 - 20 Apr 2020
 */
public final class HelmSign {

    public static void onSignChange(ChangeSignEvent event, Player player){

        if (Settings.RequireCreateSignPerm && !player.hasPermission("movecraft.createsign.cruise")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            event.setCancelled(true);
            return;
        }

        ListValue<Text> lines = event.getText().lines();

        lines.set(0, Text.of("\\  ||  /"));
        lines.set(1, Text.of("==      =="));
        lines.set(2, Text.of("/  ||  \\"));
        event.getText().set(lines);
    }

    public static void onSignClick(InteractBlockEvent event, Player player, BlockSnapshot block) {
        Rotation rotation;
        if (event instanceof InteractBlockEvent.Secondary) {
            rotation = Rotation.CLOCKWISE;
        }else if(event instanceof InteractBlockEvent.Primary){
            rotation = Rotation.ANTICLOCKWISE;
        }else{
            return;
        }

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (!lines.get(0).toPlain().equalsIgnoreCase("\\  ||  /") ||
                !lines.get(1).toPlain().equalsIgnoreCase("==      ==") ||
                !lines.get(2).toPlain().equalsIgnoreCase("/  ||  \\")) {
            return;
        }

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (craft == null || player.getUniqueId() != craft.getPilot()) {
            player.sendMessage(Text.of("You are not piloting a craft."));
            return;
        }

        if (!player.hasPermission("movecraft." + craft.getType().getName().toLowerCase() + ".movement.rotate") && (craft.getType().requiresSpecificPerms() || !player.hasPermission("movecraft.movement.rotate"))) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        if (!MathUtils.locationInHitbox(craft.getHitBox(), player.getLocation())) {
            return;
        }

        if (craft.getType().rotateAtMidpoint()) {
            craft.rotate(craft.getHitBox().getMidPoint(), rotation);
        } else {
            craft.rotate(sign.getLocation().getBlockPosition(), rotation);
        }

        event.setCancelled(true);

        //TODO: Lower speed while turning

    }
}