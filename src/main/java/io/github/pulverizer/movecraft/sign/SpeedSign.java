package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;

/**
 * Add Permissions:
 * - Create Sign
 *
 * No Settings
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.0 - 12 Apr 2020
 */
public final class SpeedSign {

    public static void onCraftDetect(CraftDetectEvent event, World world, HashHitBox hitBox){

        for(Vector3i location: hitBox){

            if(world.getBlockType(location) != BlockTypes.WALL_SIGN && world.getBlockType(location) != BlockTypes.STANDING_SIGN || !world.getTileEntity(location).isPresent())
                continue;

            Sign sign = (Sign) world.getTileEntity(location).get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Speed:")) {
                lines.set(1, Text.of("0 m/s"));
                lines.set(2, Text.of(""));
                lines.set(3, Text.of("0ms"));
                sign.offer(lines);
            }
        }
    }

    public static void onSignTranslate(Craft craft, Sign sign) {

        ListValue<Text> lines = sign.lines();

        lines.set(1, Text.of(String.format("%.2f", craft.getSpeed()), " m/s"));
        lines.set(3, Text.of(String.format("%.2f", craft.getMeanMoveTime()), "ms"));
        sign.offer(lines);
    }
}