package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.text.Text;

public final class ReleaseSign {
    private static final String HEADER = "Release";

    @Listener
    public final void onSignClick(InteractBlockEvent.Secondary.MainHand event, @Root Player player) {

        BlockSnapshot block = event.getTargetBlock();
        if (block.getState().getType() != BlockTypes.STANDING_SIGN && block.getState().getType() != BlockTypes.WALL_SIGN) {
            return;
        }

        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();

        if (!lines.get(0).toPlain().equalsIgnoreCase(HEADER)) {
            return;
        }
        Craft craft = CraftManager.getInstance().getCraftByPlayer(player.getUniqueId());
        if (craft == null) {
            return;
        }
        CraftManager.getInstance().removeCraft(craft);
    }
}