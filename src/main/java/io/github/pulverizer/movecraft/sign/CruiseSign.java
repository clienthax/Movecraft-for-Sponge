package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
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

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){

                if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
                    return;

                Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
                ListValue<Text> lines = sign.lines();
                if (lines.get(0).toPlain().equalsIgnoreCase("Cruise: ON")) {
                    lines.set(0, Text.of("Cruise: OFF"));
                    sign.offer(lines);
                }
            }
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
        if (lines.get(0).toPlain().equalsIgnoreCase("Cruise: OFF")) {

            event.setCancelled(true);

            if (CraftManager.getInstance().getCraftByPlayer(player) == null) {
                return;
            }

            Craft c = CraftManager.getInstance().getCraftByPlayer(player);

            if (!c.getType().getCanCruise()) {
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

            c.setCruiseDirection(cruiseDirection);
            c.setLastCruiseUpdate(System.currentTimeMillis());
            c.setCruising(true);
            if (!c.getType().getMoveEntities()) {
                CraftManager.getInstance().addReleaseTask(c);
            }
            return;
        }
        if (lines.get(0).toPlain().equalsIgnoreCase("Cruise: ON")
                && CraftManager.getInstance().getCraftByPlayer(player) != null
                && CraftManager.getInstance().getCraftByPlayer(player).getType().getCanCruise()) {
            event.setCancelled(true);
            lines.set(0, Text.of("Cruise: OFF"));
            sign.offer(lines);
            CraftManager.getInstance().getCraftByPlayer(player).setCruising(false);
        }
    }

    @Listener
    public void onSignChange(ChangeSignEvent event, @Root Player player) {

        if (!event.getText().lines().get(0).toPlain().equalsIgnoreCase("Cruise: OFF") && !event.getText().lines().get(0).toPlain().equalsIgnoreCase("Cruise: ON")) {
            return;
        }
        if (player.hasPermission("movecraft.cruisesign") || !Settings.RequireCreatePerm) {
            return;
        }
        player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Insufficient Permissions")));
        event.setCancelled(true);
    }
}