package io.github.pulverizer.movecraft.listener;

import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.sign.CommanderSign;
import io.github.pulverizer.movecraft.sign.CrewSign;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.property.block.MatterProperty;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.text.Text;

import static org.spongepowered.api.event.Order.FIRST;
import static org.spongepowered.api.event.Order.LAST;

public class BlockListener {

    private long lastDamagesUpdate = 0;

    @Listener(order = LAST)
    public void onBlockBreak(ChangeBlockEvent.Break event) {

        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            BlockSnapshot blockSnapshot = transaction.getOriginal();

            if (Settings.ProtectPilotedCrafts && event.getCause().root() instanceof Player) {
                for (Craft craft : CraftManager.getInstance().getCraftsInWorld(blockSnapshot.getLocation().get().getExtent())) {

                    if (craft == null || craft.isSinking()) {
                        continue;
                    }

                    if (craft.getHitBox().contains(blockSnapshot.getLocation().get().getBlockPosition())) {

                        transaction.setValid(false);
                        ((Player) event.getCause().root()).sendMessage(Text.of("BLOCK IS PART OF A PILOTED CRAFT"));
                        break;
                    }
                }
            }

            if (transaction.isValid()) {
                CommanderSign.onSignBreak(event, transaction);
                CrewSign.onSignBreak(event, transaction);
            }
        }
    }

    // prevent water and lava from spreading on moving crafts
    //TODO: This doesn't actually seem to work.
    @Listener(order = FIRST)
    public void onBlockFromTo(ChangeBlockEvent.Modify event) {

        if (!event.getContext().containsKey(EventContextKeys.LIQUID_FLOW))
            return;

        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {

            if (!transaction.getOriginal().getLocation().isPresent() || transaction.getOriginal().getProperty(MatterProperty.class).get().getValue() != MatterProperty.Matter.LIQUID)
                continue;

            for (Craft craft : CraftManager.getInstance().getCraftsInWorld(transaction.getOriginal().getLocation().get().getExtent())) {
                if (!craft.isNotProcessing() && craft.getHitBox().contains(transaction.getOriginal().getLocation().get().getBlockPosition())) {
                    transaction.setValid(false);
                    return;
                }
            }
        }
    }

    //TODO: Test this
/*
    // prevent pistons from moving on processing crafts
    // else if - piston extends - add locations to hitbox
    // else if - piston retracts - remove locations from hitbox
    @Listener(order = FIRST)
    public void onPistonEvent(ChangeBlockEvent.Post event) {
        BlockSnapshot block = event.getContext().get(EventContextKeys.PISTON_EXTEND);

        if (block.getState().getType() != BlockTypes.PISTON && block.getState().getType() != BlockTypes.STICKY_PISTON)
        CraftManager.getInstance().getCraftsInWorld(block.getLocation().get().getExtent());
        for (Craft craft : CraftManager.getInstance().getCraftsInWorld(block.getLocation().get().getExtent())) {
            Vector3i loc = block.getLocation().get().getBlockPosition();
            if (craft.getHitBox().contains(loc) && !craft.isNotProcessing()) {
                event.setCancelled(true);
                return;
            }
        }
    }
*/
    //TODO: Reimplement these listeners

    // Should not need this due to blocks still ticking?

    /*@Listener(order = LAST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // replace blocks with fire occasionally, to prevent fast crafts from simply ignoring fire
        if (!Settings.FireballPenetration || event.isCancelled() || event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) {
            return;
        }
        BlockSnapshot testBlock = event.getBlock().getRelative(-1, 0, 0);
        if (!testBlock.getType().isBurnable())
            testBlock = event.getBlock().getRelative(1, 0, 0);

        if (!testBlock.getType().isBurnable())
            testBlock = event.getBlock().getRelative(0, 0, -1);

        if (!testBlock.getType().isBurnable())
            testBlock = event.getBlock().getRelative(0, 0, 1);

        if (!testBlock.getType().isBurnable()) {
            return;
        }

        testBlock.setType(BlockTypes.AIR);
    }*/

}