package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

/**
 * No Permissions
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.0 - 12 Apr 2020
 */
public class ContactsSign {

    public static void onCraftDetect(CraftDetectEvent event, World world, HashHitBox hitBox){

        for(Vector3i location : hitBox) {

            if (world.getBlockType(location) != BlockTypes.WALL_SIGN && world.getBlockType(location) != BlockTypes.STANDING_SIGN || world.getTileEntity(location).isPresent())
                continue;

            Sign sign = (Sign) world.getTileEntity(location).get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Contacts:")) {
                lines.set(1, Text.of(""));
                lines.set(2, Text.of(""));
                lines.set(3, Text.of(""));
                sign.offer(lines);
            }
        }
    }

    public static void onSignTranslateEvent(Craft craft, Sign sign){

        ListValue<Text> lines = sign.lines();

        boolean foundContact = false;
        int signLine = 1;
        for(Craft tcraft : CraftManager.getInstance().getCraftsInWorld(craft.getWorld())) {
            long cposx=craft.getHitBox().getMaxX()+craft.getHitBox().getMinX();
            long cposy=craft.getHitBox().getMaxY()+craft.getHitBox().getMinY();
            long cposz=craft.getHitBox().getMaxZ()+craft.getHitBox().getMinZ();
            cposx=cposx>>1;
            cposy=cposy>>1;
            cposz=cposz>>1;
            long tposx=tcraft.getHitBox().getMaxX()+tcraft.getHitBox().getMinX();
            long tposy=tcraft.getHitBox().getMaxY()+tcraft.getHitBox().getMinY();
            long tposz=tcraft.getHitBox().getMaxZ()+tcraft.getHitBox().getMinZ();
            tposx=tposx>>1;
            tposy=tposy>>1;
            tposz=tposz>>1;
            long diffx=cposx-tposx;
            long diffy=cposy-tposy;
            long diffz=cposz-tposz;
            long distsquared= diffx * diffx;
            distsquared+= diffy * diffy;
            distsquared+= diffz * diffz;
            long detectionRange = 0;
            if(tposy>tcraft.getWorld().getSeaLevel()) {
                detectionRange=(long) (Math.sqrt(tcraft.getInitialSize())*tcraft.getType().getDetectionMultiplier());
            } else {
                detectionRange=(long) (Math.sqrt(tcraft.getInitialSize())*tcraft.getType().getUnderwaterDetectionMultiplier());
            }
            if(distsquared<detectionRange*detectionRange && tcraft.getPilot()!=craft.getPilot()) {
                // craft has been detected
                foundContact = true;
                String notification = TextColors.BLUE + tcraft.getType().getName();
                if(notification.length()>9) {
                    notification = notification.substring(0, 7);
                }
                notification += " " + (int)Math.sqrt(distsquared);
                if(Math.abs(diffx) > Math.abs(diffz)) {
                    if(diffx<0) {
                        notification+=" E";
                    } else {
                        notification+=" W";
                    }
                } else {
                    if(diffz<0) {
                        notification+=" S";
                    } else {
                        notification+=" N";
                    }
                }
                lines.set(signLine++, Text.of(notification));
                if (signLine >= 4) {
                    break;
                }
            }
        }
        if(signLine<4) {
            for(int i=signLine; i<4; i++) {
                lines.set(signLine, Text.of(""));
            }
        }

        sign.offer(lines);
    }


}