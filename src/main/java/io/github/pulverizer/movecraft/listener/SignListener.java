package io.github.pulverizer.movecraft.listener;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.event.SignTranslateEvent;
import io.github.pulverizer.movecraft.sign.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Include;
import org.spongepowered.api.world.World;

public class SignListener {

    @Listener
    @Include({InteractBlockEvent.Primary.class, InteractBlockEvent.Secondary.MainHand.class})
    public final void onSignClick(InteractBlockEvent event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        AntiAircraftDirectorSign.onSignClick(event, player, block);
        CannonDirectorSign.onSignClick(event, player, block);
        HelmSign.onSignClick(event, player, block);
        PilotSign.onSignClick(event, player, block);
        RemoteSign.onSignClick(event, player, block);
        SubcraftRotateSign.onSignClick(event, player, block);

        if (event instanceof InteractBlockEvent.Secondary.MainHand) {

            InteractBlockEvent.Secondary.MainHand rightClickEvent = (InteractBlockEvent.Secondary.MainHand) event;

            AscendSign.onSignClick(rightClickEvent, player, block);
            CraftSign.onSignClick(rightClickEvent, player, block);
            CrewSign.onSignRightClick(rightClickEvent, player, block);
            CruiseSign.onSignClick(rightClickEvent, player, block);
            DescendSign.onSignClick(rightClickEvent, player, block);
            MoveSign.onSignClick(rightClickEvent, player, block);
            RelativeMoveSign.onSignClick(rightClickEvent, player, block);
            ReleaseSign.onSignClick(rightClickEvent, player, block);
            TeleportSign.onSignClick(rightClickEvent, player, block);

        }

    }

    @Listener
    public final void craftDetection(CraftDetectEvent event) {
        World world = event.getCraft().getWorld();
        HashHitBox hitBox = event.getCraft().getHitBox();

        StatusSign.onCraftDetect(event, world, hitBox);
        SpeedSign.onCraftDetect(event, world, hitBox);
        DescendSign.onCraftDetect(event, world, hitBox);
        AscendSign.onCraftDetect(event, world, hitBox);
        CruiseSign.onCraftDetect(event, world, hitBox);
        CrewSign.onCraftDetect(event, world, hitBox);
        ContactsSign.onCraftDetect(event, world, hitBox);

    }

    @Listener
    public final void onSignChange(ChangeSignEvent event, @Root Player player) {

        CruiseSign.onSignChange(event, player);
        CraftSign.onSignChange(event, player);
        HelmSign.onSignChange(event);
        CommanderSign.onSignChange(event, player);
        CrewSign.onSignChange(event, player);
    }

    @Listener
    public final void onSignTranslate(SignTranslateEvent event) {

        Craft craft = event.getCraft();
        World world = event.getWorld();
        Vector3i location = event.getBlockPosition();

        CrewSign.onSignTranslate(event, craft, world, location);
        StatusSign.onSignTranslate(event, craft, world, location);
        SpeedSign.onSignTranslate(event, craft, world, location);
        ContactsSign.onSignTranslateEvent(event, craft, world, location);
    }
}