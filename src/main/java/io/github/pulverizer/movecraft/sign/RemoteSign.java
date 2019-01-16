package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.localisation.I18nSupport;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.World;

import java.util.LinkedList;

public final class RemoteSign {
    private static final String HEADER = "Remote Sign";

    @Listener
    public final void onSignClick(InteractBlockEvent event) {
        if (!(event instanceof InteractBlockEvent.Primary) && !(event instanceof InteractBlockEvent.Secondary)) {
            return;
        }
        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) block.getState();
        if (!sign.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }
        Craft foundCraft = null;
        World blockWorld = block.getLocation().get().getExtent();
        for (Craft tcraft : CraftManager.getInstance().getCraftsInWorld(blockWorld)) {
            if (MathUtils.locationInHitbox(tcraft.getHitBox(), block.getLocation())) {
                // don't use a craft with a null player. This is
                // mostly to avoid trying to use subcrafts
                if (CraftManager.getInstance().getPlayerFromCraft(tcraft) != null) {
                    foundCraft = tcraft;
                    break;
                }
            }
        }

        Player player = null;
        if (event.getSource() instanceof Player) {
            player = ((Player) event.getSource()).getPlayer().orElse(null);
        }

        if (foundCraft == null) {
            if (player != null) {
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("ERROR: Remote Sign must be a part of a piloted craft!")));
            }
            return;
        }

        if (!foundCraft.getType().allowRemoteSign()) {
            if (player != null) {
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("ERROR: Remote Signs not allowed on this craft!")));
            }
            return;
        }

        String targetText = sign.lines().get(1).toPlain();
        if (targetText.equalsIgnoreCase(HEADER)) {
            if (player != null) {
                player.sendMessage(Text.of("ERROR: Remote Sign can't remote another Remote Sign!"));
            }
            return;
        }
        LinkedList<MovecraftLocation> foundLocations = new LinkedList<MovecraftLocation>();
        for (MovecraftLocation tloc : foundCraft.getHitBox()) {
            BlockSnapshot tb = blockWorld.getBlock(tloc.getX(), tloc.getY(), tloc.getZ());
            if (!tb.getState().getType().equals(BlockTypes.STANDING_SIGN) && !tb.getState().getType().equals(BlockTypes.WALL_SIGN)) {
                continue;
            }
            Sign ts = (Sign) tb.getState();

            if (ts.lines().get(0).toPlain().equalsIgnoreCase(HEADER)) {
                continue;
            }
            if (ts.lines().get(0).toPlain().equalsIgnoreCase(targetText)) {
                foundLocations.add(tloc);
                continue;
            }
            if (ts.lines().get(1).toPlain().equalsIgnoreCase(targetText)) {
                foundLocations.add(tloc);
                continue;
            }
            if (ts.lines().get(2).toPlain().equalsIgnoreCase(targetText)) {
                foundLocations.add(tloc);
                continue;
            }
            if (ts.lines().get(3).toPlain().equalsIgnoreCase(targetText)) {
                foundLocations.add(tloc);
            }
        }
        if (foundLocations.isEmpty()) {
            if (player != null) {
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("ERROR: Could not find target sign!")));
            }
            return;
        }

        for (MovecraftLocation foundLoc : foundLocations) {
            BlockSnapshot newBlock = blockWorld.getBlock(foundLoc.getX(), foundLoc.getY(), foundLoc.getZ());

            InteractBlockEvent interact = null;

            if(event instanceof InteractBlockEvent.Primary) {
                interact = SpongeEventFactory.createInteractBlockEventPrimaryMainHand(event.getCause(), HandTypes.MAIN_HAND, newBlock.getLocation(), newBlock, event.getTargetSide());
            }

            if(event instanceof InteractBlockEvent.Secondary) {
                interact = SpongeEventFactory.createInteractBlockEventSecondaryMainHand(event.getCause(), Tristate.FALSE, Tristate.FALSE, Tristate.FALSE, Tristate.FALSE, HandTypes.MAIN_HAND, newBlock.getLocation(), newBlock, event.getTargetSide());
            }

            Sponge.getEventManager().post(interact);
        }

        event.setCancelled(true);
    }
}