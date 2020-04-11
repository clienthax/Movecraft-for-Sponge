package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.config.CraftType;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public final class CraftSign {

    public static void onSignChange(ChangeSignEvent event, Player player){

        if (!Settings.RequireCreatePerm)
            return;

        ListValue<Text> lines = event.getText().lines();

        if (!player.hasPermission("movecraft." + lines.get(0).toPlain() + ".create")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            event.setCancelled(true);
        }
    }

    public static void onSignClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        ListValue<Text> lines = ((Sign) block.getLocation().get().getTileEntity().get()).lines();

        CraftType type = CraftManager.getInstance().getCraftTypeFromString(lines.get(0).toPlain());
        if (type == null)
            return;

        // Valid sign prompt for ship command.
        if (!player.hasPermission("movecraft." + lines.get(0).toPlain() + ".crew.command")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        // Attempt to run detection
        Location<World> loc = block.getLocation().get();

        if (type.getCruiseOnPilot()) {
            //TODO: Don't set Commander! Just OG commander. Set to null for now as fix.
            final Craft craft = new Craft(type, null, loc);

            //get Cruise Direction
            Direction cruiseDirection = block.get(Keys.DIRECTION).get().getOpposite();
            if (cruiseDirection != Direction.NORTH && cruiseDirection != Direction.WEST && cruiseDirection != Direction.SOUTH && cruiseDirection != Direction.EAST) {
                player.sendMessage(Text.of("Invalid Cruise Direction"));
                return;
            }

            craft.setCruiseDirection(cruiseDirection);
            craft.setState(CraftState.CRUISING);

            //TODO: Replace with task in AsyncManager!
            Task.builder()
                    .execute(() -> CraftManager.getInstance().removeCraft(craft))
                    .delayTicks(20*15)
                    .submit(Movecraft.getInstance());

        } else {
            Craft oldCraft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());

            if (oldCraft == null) {
                new Craft(type, player.getUniqueId(), loc);
            } else {
                if (oldCraft.isNotProcessing()) {
                    CraftManager.getInstance().removeCraft(oldCraft);
                    new Craft(type, player.getUniqueId(), loc);
                }
            }
        }
        event.setCancelled(true);

    }
}