package io.github.pulverizer.movecraft.sign;

import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;

public final class CommanderSign {

    private static final String HEADER = "Commander:";

    public static void onSignChange(ChangeSignEvent event, Player player) {

        ListValue<Text> lines = event.getText().lines();

        String pilotName = lines.get(1).toPlain();

        if (pilotName.isEmpty()) {
            lines.set(1, Text.of(player.getName()));
            event.getText().set(lines);
        }

    }
}