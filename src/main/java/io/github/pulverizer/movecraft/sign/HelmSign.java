package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;
import org.spongepowered.api.text.Text;

public final class HelmSign {

    @Listener
    public final void onSignChange(ChangeSignEvent event){
        ListValue<Text> lines = event.getText().lines();
        if (!lines.get(0).toPlain().equalsIgnoreCase("[helm]")) {
            return;
        }
        lines.set(0, Text.of("\\  ||  /"));
        lines.set(1, Text.of("==      =="));
        lines.set(2, Text.of("/  ||  \\"));
        event.getText().set(lines);
    }

    @Listener
    @Include({InteractBlockEvent.Primary.class, InteractBlockEvent.Secondary.MainHand.class})
    public final void onSignClick(InteractBlockEvent event, @Root Player player) {
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

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (!lines.get(0).toPlain().equalsIgnoreCase("\\  ||  /") ||
                !lines.get(1).toPlain().equalsIgnoreCase("==      ==") ||
                !lines.get(2).toPlain().equalsIgnoreCase("/  ||  \\")) {
            return;
        }

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
        if (craft == null) {
            return;
        }
        if (!player.hasPermission("movecraft." + craft.getType().getCraftName() + ".rotate")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        if (!MathUtils.locationInHitbox(craft.getHitBox(), player.getLocation())) {
            return;
        }

        if (craft.getType().rotateAtMidpoint()) {
            CraftManager.getInstance().getCraftByPlayer(player).rotate(rotation, craft.getHitBox().getMidPoint());
        } else {
            CraftManager.getInstance().getCraftByPlayer(player).rotate(rotation, MathUtils.sponge2MovecraftLoc(sign.getLocation()));
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