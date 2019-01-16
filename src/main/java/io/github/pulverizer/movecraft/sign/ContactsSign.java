package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.events.SignTranslateEvent;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

public class ContactsSign implements Listener {

    @EventHandler
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getW();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            Block block = location.toSponge(world).getBlock();
            if(block.getType() == BlockTypes.WALL_SIGN || block.getType() == BlockTypes.STANDING_SIGN){
                Sign sign = (Sign) block.getState();
                if (ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Contacts:")) {
                    sign.setLine(1, "");
                    sign.setLine(2, "");
                    sign.setLine(3, "");
                    sign.update();
                }
            }
        }
    }

    @EventHandler
    public final void onSignTranslateEvent(SignTranslateEvent event){
        String[] lines = event.getLines();
        Craft craft = event.getCraft();
        if (!ChatColor.stripColor(lines[0]).equalsIgnoreCase("Contacts:")) {
            return;
        }
        boolean foundContact=false;
        int signLine=1;
        for(Craft tcraft : CraftManager.getInstance().getCraftsInWorld(craft.getW())) {
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
            long detectionRange=0;
            if(tposy>tcraft.getW().getSeaLevel()) {
                detectionRange=(long) (Math.sqrt(tcraft.getOrigBlockCount())*tcraft.getType().getDetectionMultiplier());
            } else {
                detectionRange=(long) (Math.sqrt(tcraft.getOrigBlockCount())*tcraft.getType().getUnderwaterDetectionMultiplier());
            }
            if(distsquared<detectionRange*detectionRange && tcraft.getNotificationPlayer()!=craft.getNotificationPlayer()) {
                // craft has been detected
                foundContact=true;
                String notification = TextColors.BLUE + tcraft.getType().getCraftName();
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
                lines[signLine++] = notification;
                if (signLine >= 4) {
                    break;
                }
            }
        }
        if(signLine<4) {
            for(int i=signLine; i<4; i++) {
                lines[signLine]="";
            }
        }
    }


}