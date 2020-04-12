package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.event.SignTranslateEvent;
import io.github.pulverizer.movecraft.sign.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
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
        ContactsSign.onCraftDetect(event, world, hitBox);

    }

    @Listener
    public final void onSignChange(ChangeSignEvent event, @Root Player player) {

        String header = event.getText().lines().get(0).toPlain().toLowerCase();

        switch (header) {

            case "[helm]":
                HelmSign.onSignChange(event);
                break;

            case "commander:":
                CommanderSign.onSignChange(event, player);
                break;

            case "crew:":

                if (Settings.EnableCrewSigns) {
                    CrewSign.onSignChange(event, player);
                }
                break;


            default:
                if (header.equals("cruise:") || header.equals("cruise: off") || header.equals("cruise: on")) {
                    CruiseSign.onSignChange(event, player);
                    return;
                }

                if (Settings.RequireCreateSignPerm && CraftManager.getInstance().getCraftTypeFromString(header) != null)
                    CraftSign.onSignChange(event, player, header);

                break;
        }
    }

    @Listener
    public final void onSignTranslate(SignTranslateEvent event) {

        Craft craft = event.getCraft();

        if (!event.getWorld().getTileEntity(event.getBlockPosition()).isPresent())
            return;

        Sign sign = (Sign) event.getWorld().getTileEntity(event.getBlockPosition()).get();

        String header = sign.lines().get(0).toPlain().toLowerCase();

        switch (header) {

            case "crew:":
                CrewSign.onSignTranslate(event.getWorld(), event.getBlockPosition(), sign);
                break;

            case "status:":
                StatusSign.onSignTranslate(craft, sign);
                break;

            case "speed:":
                SpeedSign.onSignTranslate(craft, sign);
                break;

            case "contacts:":
                ContactsSign.onSignTranslateEvent(craft, sign);
                break;
        }
    }
}