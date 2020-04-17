package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;

/**
 * Add Permissions:
 * - Create Sign
 *
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.2 - 17 Apr 2020
 */
public final class RelativeMoveSign {
    private static final String HEADER = "RMove:";

    public static void onSignClick(InteractBlockEvent.Secondary.MainHand event, Player player, BlockSnapshot block) {

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

        if (!craft.getType().getCanStaticMove()) {
            return;
        }

        // Use permissions
        if (!player.hasPermission("movecraft." + craft.getType().getName() + ".movement.relativemove") && (craft.getType().requiresSpecificPerms() || !player.hasPermission("movecraft.movement.relativemove"))) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            return;
        }

        String[] numbers = lines.get(1).toPlain().split(",");
        int dLeftRight = Integer.parseInt(numbers[0]);
        // negative = left,
        // positive = right
        int dy = Integer.parseInt(numbers[1]);
        int dBackwardForward = Integer.parseInt(numbers[2]);
        // negative = backwards,
        // positive = forwards
        int maxMove = craft.getType().maxStaticMove();

        if (dLeftRight > maxMove)
            dLeftRight = maxMove;
        if (dLeftRight < -maxMove)
            dLeftRight = -maxMove;
        if (dy > maxMove)
            dy = maxMove;
        if (dy < -maxMove)
            dy = -maxMove;
        if (dBackwardForward > maxMove)
            dBackwardForward = maxMove;
        if (dBackwardForward < -maxMove)
            dBackwardForward = -maxMove;
        int dx = 0;
        int dz = 0;

        //get Orientation
        Direction orientation = block.get(Keys.DIRECTION).get().getOpposite();
        if (orientation != Direction.NORTH && orientation != Direction.WEST && orientation != Direction.SOUTH && orientation != Direction.EAST) {
            if (orientation == Direction.NORTH_NORTHEAST || orientation == Direction.NORTH_NORTHWEST) {
                orientation = Direction.NORTH;
            } else if (orientation == Direction.SOUTH_SOUTHEAST || orientation == Direction.SOUTH_SOUTHWEST) {
                orientation = Direction.SOUTH;
            } else if (orientation == Direction.WEST_NORTHWEST || orientation == Direction.WEST_SOUTHWEST) {
                orientation = Direction.WEST;
            } else if (orientation == Direction.EAST_NORTHEAST || orientation == Direction.EAST_SOUTHEAST) {
                orientation = Direction.EAST;
            } else {
                player.sendMessage(Text.of("Invalid Sign Orientation!"));
                return;
            }
        }


        switch (orientation) {
            case NORTH:
                // North
                dx = dLeftRight;
                dz = -dBackwardForward;
                break;
            case SOUTH:
                // South
                dx = -dLeftRight;
                dz = dBackwardForward;
                break;
            case EAST:
                // East
                dx = dBackwardForward;
                dz = dLeftRight;
                break;
            case WEST:
                // West
                dx = -dBackwardForward;
                dz = -dLeftRight;
                break;
        }

        CraftManager.getInstance().getCraftByPlayer(player.getUniqueId()).translate(new Vector3i(dx, dy, dz), false);
    }
}