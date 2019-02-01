package io.github.pulverizer.movecraft.sign;

import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;

public final class PilotSign {

    private static final String HEADER = "Pilot:";

    @Listener
    public final void onSignChange(ChangeSignEvent event, @Root Player player){

        ListValue<Text> lines = event.getText().lines();

        if (lines.get(0).toPlain().equalsIgnoreCase(HEADER)) {
            String pilotName = lines.get(1).toPlain();

            if (pilotName.isEmpty()) {
                lines.set(1, Text.of(player.getName()));
                event.getText().set(lines);
            }
        }
    }
}