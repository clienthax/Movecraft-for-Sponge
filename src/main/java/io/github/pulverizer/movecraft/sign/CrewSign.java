package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.events.SignTranslateEvent;
import io.github.pulverizer.movecraft.config.Settings;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public class CrewSign {

    @Listener
    public final void onSignChange(ChangeSignEvent event, @Root Player player) {
        if (!event.getText().lines().get(0).toPlain().equalsIgnoreCase("Crew:")) {
            return;
        }
        ListValue<Text> lines = event.getText().lines();
        lines.set(1, Text.of(player.getName()));
        event.getTargetTile().offer(lines);
    }

    @Listener
    public final void onSignTranslate(SignTranslateEvent event) {
        Craft craft = event.getCraft();
        BlockSnapshot block = event.getBlock();

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (!Settings.AllowCrewSigns || !lines.get(0).toPlain().equalsIgnoreCase("Crew:")) {
            return;
        }

        String crewName = lines.get(1).toPlain();
        if (!Sponge.getServer().getPlayer(crewName).isPresent())
            return;

        Player crewPlayer = Sponge.getServer().getPlayer(crewName).get();

        Location<World> location = block.getLocation().get().sub(0,1,0);
        if (!location.getBlockType().equals(BlockTypes.BED)) {
            return;
        }
        craft.getCrewSigns().put(crewPlayer.getUniqueId(), location);
    }

    @Listener
    public final void onSignRightClick(InteractBlockEvent.Secondary.MainHand event, @Root Player player) {

        if (!Settings.AllowCrewSigns) {
            return;
        }

        if (!player.get(Keys.IS_SNEAKING).isPresent())
            return;

        BlockSnapshot block = event.getTargetBlock();

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (!player.get(Keys.IS_SNEAKING).get() || block.getState().getType() != BlockTypes.STANDING_SIGN || block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        if (!lines.get(0).toPlain().equalsIgnoreCase("Crew:")) {
            return;
        }
        if (!block.getLocation().get().sub(0,-1,0).getBlockType().equals(BlockTypes.BED)) {
            player.sendMessage(Text.of("You need to have a bed below your crew sign!"));
            return;
        }
        if (!lines.get(1).toPlain().equalsIgnoreCase(player.getName())) {
            player.sendMessage(Text.of("You don't own this crew sign!"));
            return;
        }
        if(CraftManager.getInstance().getCraftByPlayer(player) != null){
            player.sendMessage(Text.of("You can't set your priority crew sign to a piloted craft."));
            return;
        }
        Location location = sign.getLocation();
        player.sendMessage(Text.of("Priority crew bed set!"));
        player.get(Keys.RESPAWN_LOCATIONS).get().
        player.offer(Keys.RESPAWN_LOCATIONS, )setBedSpawnLocation(location, true);
    }

    @Listener(Order.FIRST)
    public final void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Craft craft = CraftManager.getInstance().getCraftByPlayer(player);
        if (craft == null) {
            return;
        }
        if(craft.getSinking() || craft.getDisabled() || !craft.getCrewSigns().containsKey(player.getUniqueId())) {
            return;
        }
        player.sendMessage("Respawning at crew bed!");
        event.setRespawnLocation(craft.getCrewSigns().get(player.getUniqueId()));
    }

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if (block.getState().getType() != BlockTypes.WALL_SIGN && block.getState().getType() != BlockTypes.STANDING_SIGN) {
                continue;
            }

            if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
                continue;

            Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Crew:")) {

                if (Sponge.getServer().getPlayer(lines.get(1).toPlain()).isPresent())
                    event.getCraft().getCrewSigns().put(Sponge.getServer().getPlayer(lines.get(1).toPlain()).get().getUniqueId(),block.getLocation().get());
            }
        }
    }
}