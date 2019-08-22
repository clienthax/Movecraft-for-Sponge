package io.github.pulverizer.movecraft.sign;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.event.SignTranslateEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StatusSign {

    public static void onCraftDetect(CraftDetectEvent event, World world, HashHitBox hitBox){

        for(Vector3i location: hitBox){

            if(world.getBlockType(location) != BlockTypes.WALL_SIGN && world.getBlockType(location) != BlockTypes.STANDING_SIGN || !world.getTileEntity(location).isPresent())
                continue;

            Sign sign = (Sign) world.getTileEntity(location).get();
            ListValue<Text> lines = sign.lines();

            if (lines.get(0).toPlain().equalsIgnoreCase("Status:")) {
                lines.set(1, Text.of(""));
                lines.set(2, Text.of(""));
                lines.set(3, Text.of(""));
                sign.offer(lines);
            }
        }
    }

    public static void onSignTranslate(Craft craft, Sign sign) {

        ListValue<Text> lines = sign.lines();

        double fuel = craft.checkFuelStored() + craft.getBurningFuel();
        int totalBlocks=0;
        Map<BlockType, Integer> foundBlocks = new HashMap<>();
        for (Vector3i loc : craft.getHitBox()) {
            BlockType blockType = craft.getWorld().getBlockType(loc);

            if (foundBlocks.containsKey(blockType)) {
                Integer count = foundBlocks.get(blockType);
                if (count == null) {
                    foundBlocks.put(blockType, 1);
                } else {
                    foundBlocks.put(blockType, count + 1);
                }
            } else {
                foundBlocks.put(blockType, 1);
            }

            if (blockType != BlockTypes.AIR) {
                totalBlocks++;
            }
        }
        int signLine=1;
        int signColumn=0;
        for(List<BlockType> flyBlockIDs : craft.getType().getFlyBlocks().keySet()) {
            BlockType flyBlockID = flyBlockIDs.get(0);
            Double minimum = craft.getType().getFlyBlocks().get(flyBlockIDs).get(0);
            if(foundBlocks.containsKey(flyBlockID) && minimum > 0) { // if it has a minimum, it should be considered for sinking consideration
                int amount = foundBlocks.get(flyBlockID);
                Double percentPresent = (double) (amount*100/totalBlocks);
                String signText = "";
                if (percentPresent > minimum * 1.04) {
                    signText+= TextColors.GREEN;
                } else if(percentPresent>minimum*1.02) {
                    signText+= TextColors.YELLOW;
                } else {
                    signText+= TextColors.RED;
                }
                //TODO: Change to Fly and Move Blocks
                if(flyBlockID == BlockTypes.REDSTONE_BLOCK) {
                    signText+="R";
                } else if(flyBlockID == BlockTypes.IRON_BLOCK) {
                    signText+="I";
                } else {
                    signText+= flyBlockID.getName().charAt(0);
                }

                signText+=" ";
                signText+=percentPresent.intValue();
                signText+="/";
                signText+=minimum.intValue();
                signText+="  ";
                if(signColumn == 0) {
                    lines.set(signLine,Text.of(signText));
                    sign.offer(lines);
                    signColumn++;
                } else if(signLine < 3) {
                    String existingLine = lines.get(signLine).toPlain();
                    existingLine+= signText;
                    lines.set(signLine, Text.of(existingLine));
                    sign.offer(lines);
                    signLine++;
                    signColumn=0;
                }
            }
        }
        String fuelText="";
        int fuelRange = (int) Math.floor((fuel*(1+craft.getType().getCruiseSkipBlocks()))/craft.getType().getFuelBurnRate());
        TextColor fuelColor;

        if(fuelRange>1000) {
            fuelColor = TextColors.GREEN;
        } else if(fuelRange>100) {
            fuelColor = TextColors.YELLOW;
        } else {
            fuelColor = TextColors.RED;
        }
        fuelText+="Fuel range:";
        fuelText+=fuelRange;
        lines.set(signLine, Text.of("Fuel range: " + fuelRange).toBuilder().color(fuelColor).build());
        sign.offer(lines);
    }
}