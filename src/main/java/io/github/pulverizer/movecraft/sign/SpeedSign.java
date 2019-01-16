package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.events.SignTranslateEvent;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;

public final class SpeedSign {
    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){
                Sign sign = (Sign) block.getState();
                if (sign.lines().get(0).toPlain().equalsIgnoreCase("Speed:")) {
                    sign.offer(
                            sign.lines()
                                    .set(1, Text.of("0 m/s"))
                                    .set(2, Text.of("0ms"))
                    );
                }
            }
        }
    }

    @Listener
    public final void onSignTranslate(SignTranslateEvent event) {
        Craft craft = event.getCraft();
        if (event.lines().get(0).toPlain.equalsIgnoreCase("Speed:")) {
            return;
        }
        event.offer(
                event.lines()
                        .set(1, Text.of(String.format("%.2f",craft.getSpeed()) + "m/s"))
                        .set(2, Text.of(String.format("%.2f",craft.getMeanMoveTime()) + "ms"))
        );
    }
}