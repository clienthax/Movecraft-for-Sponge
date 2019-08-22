package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.World;

public final class CruiseSign {

    public static void onCraftDetect(CraftDetectEvent event, World world, HashHitBox hitBox){

        for(Vector3i location: hitBox) {

            if (world.getBlockType(location) != BlockTypes.WALL_SIGN && world.getBlockType(location) != BlockTypes.STANDING_SIGN || !world.getTileEntity(location).isPresent())
                continue;

            Sign sign = (Sign) world.getTileEntity(location).get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Cruise: ON") || lines.get(0).toPlain().equalsIgnoreCase("Cruise:")) {
                lines.set(0, Text.of("Cruise: OFF"));
                sign.offer(lines);
            }
        }
    }

    public static void onSignClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        if (lines.get(0).toPlain().equalsIgnoreCase("Cruise: OFF")) {

            event.setCancelled(true);

            if (CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()) == null) {
                return;
            }

            Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

            if (!craft.getType().getCanCruise() || player.getUniqueId() != craft.getPilot()) {
                return;
            }

            //get Cruise Direction
            Direction cruiseDirection = block.get(Keys.DIRECTION).get();
            if (cruiseDirection != Direction.NORTH && cruiseDirection != Direction.WEST && cruiseDirection != Direction.SOUTH && cruiseDirection != Direction.EAST) {
                if (cruiseDirection == Direction.NORTH_NORTHEAST || cruiseDirection == Direction.NORTH_NORTHWEST) {
                    cruiseDirection = Direction.NORTH;
                } else if (cruiseDirection == Direction.SOUTH_SOUTHEAST || cruiseDirection == Direction.SOUTH_SOUTHWEST) {
                    cruiseDirection = Direction.SOUTH;
                } else if (cruiseDirection == Direction.WEST_NORTHWEST || cruiseDirection == Direction.WEST_SOUTHWEST) {
                    cruiseDirection = Direction.WEST;
                } else if (cruiseDirection == Direction.EAST_NORTHEAST || cruiseDirection == Direction.EAST_SOUTHEAST) {
                    cruiseDirection = Direction.EAST;
                } else {
                    player.sendMessage(Text.of("Invalid Cruise Direction!"));
                    return;
                }
            }

            //c.resetSigns(false, true, true);
            lines.set(0, Text.of("Cruise: ON"));
            sign.offer(lines);

            craft.setCruiseDirection(cruiseDirection);
            craft.setLastCruiseUpdateTick(Sponge.getServer().getRunningTimeTicks());
            craft.setState(CraftState.CRUISING);

            return;
        }

        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

        if (lines.get(0).toPlain().equalsIgnoreCase("Cruise: ON") && craft != null && craft.getType().getCanCruise() && player.getUniqueId() == craft.getPilot()) {
            event.setCancelled(true);
            lines.set(0, Text.of("Cruise: OFF"));
            sign.offer(lines);
            CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).setState(CraftState.STOPPED);
        }
    }

    public static void onSignChange(ChangeSignEvent event, Player player) {

        if (player.hasPermission("movecraft.cruisesign") || !Settings.RequireCreatePerm) {
            return;
        }
        player.sendMessage(Text.of("Insufficient Permissions"));
        event.setCancelled(true);
    }
}