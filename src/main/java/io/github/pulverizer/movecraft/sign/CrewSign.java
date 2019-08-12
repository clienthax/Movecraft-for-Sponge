package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.event.SignTranslateEvent;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.entity.living.humanoid.player.RespawnPlayerEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.RespawnLocation;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Map;
import java.util.UUID;

import static org.spongepowered.api.event.Order.FIRST;

public class CrewSign {

    public static void onSignChange(ChangeSignEvent event, Player player) {
        if (!event.getText().lines().get(0).toPlain().equalsIgnoreCase("Crew:"))
            return;

        ListValue<Text> lines = event.getText().lines();
        lines.set(1, Text.of(player.getName()));
        event.getText().set(lines);
    }

    public static void onSignTranslate(SignTranslateEvent event, Craft craft, World world, Vector3i location) {

        if (!world.getTileEntity(location).isPresent())
            return;

        Sign sign = (Sign) world.getTileEntity(location).get();
        ListValue<Text> lines = sign.lines();

        if (!Settings.AllowCrewSigns || !lines.get(0).toPlain().equalsIgnoreCase("Crew:")) {
            return;
        }

        String crewName = lines.get(1).toPlain();
        if (!Sponge.getServer().getPlayer(crewName).isPresent())
            return;

        Player crewPlayer = Sponge.getServer().getPlayer(crewName).get();

        if (!world.getBlockType(location.sub(0,1,0)).equals(BlockTypes.BED)) {
            return;
        }
        craft.getCrewSigns().put(crewPlayer.getUniqueId(), location);
    }

    public static void onSignRightClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

        if (!Settings.AllowCrewSigns) {
            return;
        }

        if (!player.get(Keys.IS_SNEAKING).isPresent())
            return;

        if (!player.get(Keys.IS_SNEAKING).get() || block.getState().getType() != BlockTypes.STANDING_SIGN || block.getState().getType() != BlockTypes.WALL_SIGN)
            return;


        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

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
        if(CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()) != null){
            player.sendMessage(Text.of("You can't set your priority crew sign to a piloted craft."));
            return;
        }
        Location<World> location = block.getLocation().get();
        player.sendMessage(Text.of("Priority crew bed set!"));
        if (player.get(Keys.RESPAWN_LOCATIONS).isPresent()) {
            player.get(Keys.RESPAWN_LOCATIONS).get().clear();
        }

        Map<UUID, RespawnLocation> respawnLocationList = player.get(Keys.RESPAWN_LOCATIONS).get();
        RespawnLocation respawnLocation = RespawnLocation.builder().location(location).build();
        respawnLocationList.put(location.getExtent().getUniqueId(), respawnLocation);

        player.offer(Keys.RESPAWN_LOCATIONS, respawnLocationList);
    }

    @Listener(order = FIRST)
    public final void onPlayerRespawn(RespawnPlayerEvent event) {

        if (!event.isDeath())
            return;

        Player player = event.getTargetEntity();

        //TODO: Fix this? A player is removed from the craft upon death so this will return null atm?
        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());
        if (craft == null) {
            return;
        }
        if(craft.getState() == CraftState.SINKING || craft.getState() == CraftState.DISABLED || !craft.getCrewSigns().containsKey(player.getUniqueId())) {
            return;
        }
        player.sendMessage(Text.of("Respawning at crew bed!"));
        Transform<World> respawnTransform = event.getToTransform();
        respawnTransform.setLocation(new Location<World>(craft.getWorld(), craft.getCrewSigns().get(player.getUniqueId())));
        event.setToTransform(respawnTransform);
    }

    public static void onCraftDetect(CraftDetectEvent event, World world, HashHitBox hitBox){

        for(Vector3i location: hitBox){

            if (world.getBlockType(location) != BlockTypes.WALL_SIGN && world.getBlockType(location) != BlockTypes.STANDING_SIGN || !world.getTileEntity(location).isPresent())
                continue;

            Sign sign = (Sign) world.getTileEntity(location).get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Crew:")) {

                if (Sponge.getServer().getPlayer(lines.get(1).toPlain()).isPresent())
                    event.getCraft().getCrewSigns().put(Sponge.getServer().getPlayer(lines.get(1).toPlain()).get().getUniqueId(), location);
            }
        }
    }
}