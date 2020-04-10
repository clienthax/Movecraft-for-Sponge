package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.config.CraftType;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

public final class SubcraftRotateSign {
    private static final String HEADER = "Subcraft Rotate";
    private static final Set<UUID> rotatingPlayers = new HashSet<>();

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

        if (!lines.get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }

        if(rotatingPlayers.contains(player.getUniqueId())){
            player.sendMessage(Text.of("You are already rotating!"));
            event.setCancelled(true);
            return;
        }
        // add subcraft
        String craftTypeStr = sign.lines().get(1).toPlain();
        CraftType type = CraftManager.getInstance().getCraftTypeFromString(craftTypeStr);
        if (type == null) {
            event.setCancelled(true);
            return;
        }
        if (lines.get(2).toPlain().equalsIgnoreCase("") && lines.get(3).toPlain().equalsIgnoreCase("")) {
            lines.set(2, Text.of("_\\ /_"));
            lines.set(3, Text.of("/ \\"));
            sign.offer(lines);
        }

        if (!player.hasPermission("movecraft." + craftTypeStr + ".pilot") || !player.hasPermission("movecraft." + craftTypeStr + ".add")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            event.setCancelled(true);
            return;
        }

        final Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());
        if(craft != null) {
            if (!craft.isNotProcessing()) {
                player.sendMessage(Text.of("Parent Craft is busy!"));
                event.setCancelled(true);
                return;
            }
            craft.setProcessing(true); // prevent the parent craft from moving or updating until the subcraft is done

            //TODO: This is bad practice! Never assume anything!
            Task.builder()
                    .delayTicks(10)
                    .execute(() -> craft.setProcessing(false))
                    .submit(Movecraft.getInstance());
        }

        final Location<World> loc = event.getTargetBlock().getLocation().get();
        final Craft subCraft = new Craft(type, player.getUniqueId(), loc);
        Vector3i startPoint = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        rotatingPlayers.add(player.getUniqueId());

        Task.builder()
                .delayTicks(3)
                .execute(() -> {
                    subCraft.translate(rotation, startPoint, true);
                    Task.builder()
                            .delayTicks(3)
                            .execute(() -> {
                                rotatingPlayers.remove(player.getUniqueId());
                                CraftManager.getInstance().removeCraft(subCraft);
                            })
                            .submit(Movecraft.getInstance()); })
                .submit(Movecraft.getInstance());

        event.setCancelled(true);
    }

}