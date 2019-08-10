package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.sign.AntiAircraftDirectorSign;
import io.github.pulverizer.movecraft.sign.AscendSign;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;

public class SignListener {

    @Listener
    @Include({InteractBlockEvent.Primary.class, InteractBlockEvent.Secondary.MainHand.class})
    public final void onSignClick(InteractBlockEvent event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        AntiAircraftDirectorSign.onSignClick(event, player);
        if (event instanceof InteractBlockEvent.Secondary.MainHand)
            AscendSign.onSignClick((InteractBlockEvent.Secondary.MainHand) event, player);

    }
}