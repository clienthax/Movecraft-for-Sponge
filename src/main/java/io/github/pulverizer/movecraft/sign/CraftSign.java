package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftType;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
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
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public final class CraftSign {

    @Listener
    public void onSignChange(ChangeSignEvent event, @Root Player player){

        ListValue<Text> lines = event.getText().lines();

        if (CraftManager.getInstance().getCraftTypeFromString(lines.get(0).toPlain()) == null) {
            return;
        }
        if (!Settings.RequireCreatePerm) {
            return;
        }
        if (!player.hasPermission("movecraft." + lines.get(0).toPlain() + ".create")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            event.setCancelled(true);
        }
    }

    @Listener
    public final void onSignClick(InteractBlockEvent.Secondary.MainHand event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        CraftType type = CraftManager.getInstance().getCraftTypeFromString(lines.get(0).toPlain());
        if (type == null)
            return;


        // Valid sign prompt for ship command.
        if (!player.hasPermission("movecraft." + lines.get(0).toPlain() + ".pilot")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }
        // Attempt to run detection
        Location<World> loc = block.getLocation().get();

        if (type.getCruiseOnPilot()) {
            final Craft craft = new Craft(type, player.getUniqueId(), loc);

            //get Cruise Direction
            Direction cruiseDirection = block.get(Keys.DIRECTION).get().getOpposite();
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

            craft.setCruiseDirection(cruiseDirection);
            craft.setLastCruiseUpdateTime(System.currentTimeMillis());
            craft.setState(CraftState.CRUISING);

            Task.builder()
                    .execute(() -> CraftManager.getInstance().removeCraft(craft))
                    .delayTicks(20*15)
                    .submit(Movecraft.getInstance());

        } else {
            if (CraftManager.getInstance().getCraftByPlayer(player) == null) {
                final Craft craft = new Craft(type, player.getUniqueId(), loc);
            } else {
                Craft oldCraft = CraftManager.getInstance().getCraftByPlayer(player);
                if (oldCraft.isNotProcessing()) {
                    CraftManager.getInstance().removeCraft(oldCraft);
                    final Craft craft = new Craft(type, player.getUniqueId(), loc);
                }
            }
        }
        event.setCancelled(true);

    }
}