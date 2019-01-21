package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.events.SignTranslateEvent;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;

public final class SpeedSign {
    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if(block.getState().getType() != BlockTypes.WALL_SIGN && block.getState().getType() != BlockTypes.STANDING_SIGN) {
                return;
            }

            if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
                return;

            Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Speed:")) {
                lines.set(1, Text.of("0 m/s"));
                lines.set(2, Text.of("0ms"));
                sign.offer(lines);
            }
        }
    }

    @Listener
    public final void onSignTranslate(SignTranslateEvent event) {
        Craft craft = event.getCraft();

        BlockSnapshot block = event.getBlock();

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (!lines.get(0).toPlain().equalsIgnoreCase("Speed:")) {
            return;
        }

        lines.set(1, Text.of(String.format("%.2f",craft.getSpeed()) + "m/s"));
        lines.set(2, Text.of(String.format("%.2f",craft.getMeanMoveTime()) + "ms"));
        sign.offer(lines);
    }
}