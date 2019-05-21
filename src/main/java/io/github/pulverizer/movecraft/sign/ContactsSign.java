package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.events.CraftDetectEvent;
import io.github.pulverizer.movecraft.events.SignTranslateEvent;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

public class ContactsSign {

    @Listener
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getWorld();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            BlockSnapshot block = location.toSponge(world).createSnapshot();
            if(block.getState().getType() == BlockTypes.WALL_SIGN || block.getState().getType() == BlockTypes.STANDING_SIGN){

                if (!location.toSponge(world).getTileEntity().isPresent())
                    return;

                Sign sign = (Sign) location.toSponge(world).getTileEntity().get();
                ListValue<Text> lines = sign.lines();
                if (lines.get(0).toPlain().equalsIgnoreCase("Contacts:")) {
                    lines.set(1, Text.of(""));
                    lines.set(2, Text.of(""));
                    lines.set(3, Text.of(""));
                    sign.offer(lines);
                }
            }
        }
    }

    @Listener
    public final void onSignTranslateEvent(SignTranslateEvent event){

        BlockSnapshot block = event.getBlock();
        if (!block.getLocation().isPresent() || !block.getLocation().get().getTileEntity().isPresent())
            return;

        Sign sign = (Sign) block.getLocation().get().getTileEntity().get();
        ListValue<Text> lines = sign.lines();
        Craft craft = event.getCraft();
        if (!lines.get(0).toPlain().equalsIgnoreCase("Contacts:")) {
            return;
        }

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