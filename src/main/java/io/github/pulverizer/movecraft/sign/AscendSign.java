package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.World;

public class AscendSign {

    public static void onCraftDetect(CraftDetectEvent event, World world, HashHitBox hitBox){

        for(Vector3i location: hitBox) {

            if(world.getBlockType(location) != BlockTypes.WALL_SIGN && world.getBlockType(location) != BlockTypes.STANDING_SIGN || !world.getTileEntity(location).isPresent())
                continue;

            Sign sign = (Sign) world.getTileEntity(location).get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
                lines.set(0, Text.of("Ascend: OFF"));
                sign.offer(lines);
            }
        }
    }

    public static void onSignClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent() || craft == null || craft.getPilot() != player.getUniqueId())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: OFF")) {
            if (!craft.getType().getCanCruise()) {
                //TODO: Find a better message.
                player.sendMessage(Text.of("You are not piloting a craft!"));
                return;
            }

            event.setCancelled(true);

            lines.set(0, Text.of("Ascend: ON"));
            sign.offer(lines);

            craft.setCruiseDirection(Direction.UP);
            craft.setLastCruiseUpdateTick(Sponge.getServer().getRunningTimeTicks());
            craft.setState(CraftState.CRUISING);

            return;
        }

        if (lines.get(0).toPlain().equalsIgnoreCase("Ascend: ON")) {
            if (!craft.getType().getCanCruise()) {
                //TODO: Find a better message.
                player.sendMessage(Text.of("You are not piloting a craft!"));
                return;
            }

            event.setCancelled(true);
            lines.set(0, Text.of("Ascend: OFF"));
            sign.offer(lines);
            craft.setState(CraftState.STOPPED);
        }
    }
}