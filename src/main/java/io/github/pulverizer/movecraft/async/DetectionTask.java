package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.sign.CommanderSign;
import io.github.pulverizer.movecraft.utils.*;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.sql.Date;
import java.sql.Time;
import java.util.*;

public class DetectionTask extends AsyncTask {
    private final World world;
    private final Vector3i startLocation;
    private final Stack<Vector3i> blockStack = new Stack<>();
    private final HashHitBox detectedHitBox = new HashHitBox();
    private HashHitBox hitBox;
    private final HashSet<Vector3i> visited = new HashSet<>();
    private final HashMap<List<BlockType>, Integer> blockTypeCount = new HashMap<>();
    private Map<List<BlockType>, List<Double>> dynamicFlyBlocks;
    private boolean waterContact = false;

    private boolean foundCommanderSign = false;
    private Date commanderSignDate;
    private Time commanderSignTime;
    private String commanderSignUsername;
    private int commanderSignID;
    private boolean foundCommander = false;

    private boolean failed;
    private String failMessage;

    public DetectionTask(Craft craft, Location<World> startLocation) {
        super(craft, "Detection");
        this.startLocation = startLocation.getBlockPosition();
        world = startLocation.getExtent();
    }

    @Override
    public void execute() {
        long startTime = System.currentTimeMillis();

        Map<List<BlockType>, List<Double>> flyBlocks = craft.getType().getFlyBlocks();
        dynamicFlyBlocks = flyBlocks;

        blockStack.push(startLocation);
        do {
            detectSurrounding(blockStack.pop());
        } while (!blockStack.isEmpty());

        Player player = Sponge.getServer().getPlayer(craft.getCommander()).orElse(null);

        if (foundCommanderSign) {

            HashMap<UUID, Boolean> commanderSignMemberMap = CommanderSign.getMembers(commanderSignUsername, commanderSignID);

            if (commanderSignMemberMap.get(player.getUniqueId()) != null)
                foundCommander = true;

            commanderSignMemberMap.forEach((uuid, isOwner) -> Movecraft.getInstance().getLogger().info(Sponge.getServer().getPlayer(uuid).get().getName()));

            if (foundCommander && !Sponge.getServer().getPlayer(craft.getCommander()).get().hasPermission("movecraft.bypasslock"))
                fail("Not one of the registered commanders for this craft.");

        }

        if (failed()) {
            if (player != null)
                player.sendMessage(Text.of(getFailMessage()));
            else
                Movecraft.getInstance().getLogger().info("NULL Player Craft Detection failed:" + getFailMessage());

            return;
        }

        if (isWithinLimit(detectedHitBox.size(), craft.getType().getMinSize(), craft.getType().getMaxSize()) && confirmStructureRequirements(flyBlocks, blockTypeCount)) {
            hitBox = detectedHitBox;
        }

        long endTime = System.currentTimeMillis();

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Detection Task Took: " + (endTime - startTime) + "ms");

    }

    @Override
    public void postProcess() {

        Player player = Sponge.getServer().getPlayer(craft.getCommander()).orElse(null);

        if (!failed()) {
            Set<Craft> craftsInWorld = CraftManager.getInstance().getCraftsInWorld(craft.getWorld());
            boolean failed = false;
            boolean isSubcraft = false;
            Craft parentCraft = null;

            for (Craft testCraft : craftsInWorld) {
                if (testCraft.getHitBox().intersects(getHitBox())) {
                    Movecraft.getInstance().getLogger().info("Test Craft Size: " + testCraft.getHitBox().size());

                    isSubcraft = true;
                    parentCraft = testCraft;
                    break;
                }
            }

            Movecraft.getInstance().getLogger().info("Subcraft: " + isSubcraft);
            Movecraft.getInstance().getLogger().info("Hitbox: " + getHitBox().size());
            if (parentCraft != null && player != null)
                Movecraft.getInstance().getLogger().info("Parent Craft: " + parentCraft.getHitBox().size() + "   Pilot: " + Sponge.getServer().getPlayer(player.getUniqueId()));

            if (player != null && isSubcraft && !parentCraft.isCrewMember(player.getUniqueId())) {
                // Player is already controlling a craft
                player.sendMessage(Text.of("Detection Failed! You are not in the crew of this craft."));
            } else {

                if (isSubcraft) {

                    if (parentCraft.getType() == craft.getType() || parentCraft.getHitBox().size() <= getHitBox().size()) {
                        player.sendMessage(Text.of("Detection Failed. Craft is already being controlled by another player."));
                        failed = true;

                    } else {

                        // if this is a different type than the overlapping craft, and is smaller, this must be a child craft, like a fighter on a carrier
                        if (!parentCraft.isNotProcessing()) {
                            failed = true;
                            player.sendMessage(Text.of("Parent Craft is busy."));
                        }
                        parentCraft.setHitBox(new HashHitBox(CollectionUtils.filter(parentCraft.getHitBox(), getHitBox())));
                        parentCraft.setInitialSize(parentCraft.getInitialSize() - getHitBox().size());
                    }
                }
            }

            if (craft.getType().getMustBeSubcraft() && !isSubcraft) {
                failed = true;
                if (player != null)
                    player.sendMessage(Text.of("Craft must be part of another craft!"));
            }
            if (!failed) {
                craft.setInitialSize(getHitBox().size());
                craft.setHitBox(getHitBox());

                final int waterLine = craft.getWaterLine();
                if(!craft.getType().blockedByWater() && craft.getHitBox().getMinY() <= waterLine){
                    for(Vector3i location : craft.getHitBox().boundingHitBox()){
                        if(location.getY() <= waterLine){
                            craft.getPhasedBlocks().add(BlockTypes.WATER.getDefaultState().snapshotFor(new Location<World>(craft.getWorld(), location)));
                        }
                    }
                }

                if (craft.getHitBox() != null) {

                    if (player != null) {
                        player.sendMessage(Text.of("Successfully piloted " + craft.getType().getName() + " Size: " + craft.getHitBox().size()));
                        Movecraft.getInstance().getLogger().info("New Craft Detected! Pilot: " + player.getName() + " CraftType: " + craft.getType().getName() + " Size: " + craft.getHitBox().size() + " Location: " + craft.getHitBox().getMidPoint().toString());
                    } else {
                        Movecraft.getInstance().getLogger().info("New Craft Detected! Pilot: " + "NULL PLAYER" + " CraftType: " + craft.getType().getName() + " Size: " + craft.getHitBox().size() + " Location: " + craft.getHitBox().getMidPoint().toString());
                    }
                    CraftManager.getInstance().addCraft(craft);
                } else {
                    Movecraft.getInstance().getLogger().info("Detection Failed - NULL Hitbox!");
                }
            }
        } else {
            Movecraft.getInstance().getLogger().info("Craft Detection Failed. " + failMessage);
        }

        if(craft.getHitBox() != null){
            CraftDetectEvent event = new CraftDetectEvent(craft);
            Sponge.getEventManager().post(event);
        }

        craft.setProcessing(false);
    }

    private void detectBlock(Vector3i workingLocation) {

        if (!notVisited(workingLocation))
            return;

        BlockType testID = world.getBlockType(workingLocation);

        if (testID == BlockTypes.AIR)
            return;

        if (isForbiddenBlock(testID))
            fail("Detection Failed - Forbidden block found.");


        if (testID == BlockTypes.FLOWING_WATER || testID == BlockTypes.WATER) {
            waterContact = true;
        }

        if (testID == BlockTypes.STANDING_SIGN || testID == BlockTypes.WALL_SIGN && world.getTileEntity(workingLocation).isPresent()) {

            ListValue<Text> signText = ((Sign) world.getTileEntity(workingLocation).get()).lines();
            if (signText.get(0).toPlain().equalsIgnoreCase("Commander:") && craft.getCommander() != null) {

                Map.Entry<Date, Time> timestamp = CommanderSign.getCreationTimeStamp(signText.get(1).toPlain(), Integer.parseInt(signText.get(2).toPlain()));

                if (timestamp != null) {

                    if (!foundCommanderSign) {
                        foundCommanderSign = true;
                        commanderSignDate = timestamp.getKey();
                        commanderSignTime = timestamp.getValue();
                        commanderSignUsername = signText.get(1).toPlain();
                        commanderSignID = Integer.parseInt(signText.get(2).toPlain());

                    } else if (timestamp.getKey().before(commanderSignDate)) {
                        commanderSignDate = timestamp.getKey();
                        commanderSignTime = timestamp.getValue();
                        commanderSignUsername = signText.get(1).toPlain();
                        commanderSignID = Integer.parseInt(signText.get(2).toPlain());

                    } else if (timestamp.getKey().equals(commanderSignDate) && timestamp.getValue().before(commanderSignTime)) {
                        commanderSignDate = timestamp.getKey();
                        commanderSignTime = timestamp.getValue();
                        commanderSignUsername = signText.get(1).toPlain();
                        commanderSignID = Integer.parseInt(signText.get(2).toPlain());

                    }
                }
            }

            for (int i = 0; i < 4; i++) {
                if (isForbiddenSignString(signText.get(i).toString())) {
                    fail("Detection Failed - Forbidden sign string found.");
                }
            }
        }


        if (!isAllowedBlock(testID))
            return;

        addToBlockList(workingLocation);
        for (List<BlockType> flyBlockDef : dynamicFlyBlocks.keySet()) {
            if (flyBlockDef.contains(testID)) {
                addToBlockCount(flyBlockDef);
            } else {
                addToBlockCount(null);
            }
        }

        if (isWithinLimit(detectedHitBox.size(), 0, craft.getType().getMaxSize())) {

            addToDetectionStack(workingLocation);

        }
    }

    private void detectSurrounding(Vector3i location) {

        HashSet<Vector3i> surroundingLocations = new HashSet<>();

        //UP
        surroundingLocations.add(location.add(0, 1, 0));
        //UP - NORTH
        surroundingLocations.add(location.add(0, 1, -1));
        //UP - NORTHEAST
        surroundingLocations.add(location.add(1, 1, -1));
        //UP - EAST
        surroundingLocations.add(location.add(1, 1, 0));
        //UP - SOUTHEAST
        surroundingLocations.add(location.add(1, 1, 1));
        //UP - SOUTH
        surroundingLocations.add(location.add(0, 1, 1));
        //UP - SOUTHWEST
        surroundingLocations.add(location.add(-1, 1, 1));
        //UP - WEST
        surroundingLocations.add(location.add(-1, 1, 0));
        //UP - NORTHWEST
        surroundingLocations.add(location.add(-1, 1, -1));



        //NORTH
        surroundingLocations.add(location.add(0, 0, -1));
        //NORTHEAST
        surroundingLocations.add(location.add(1, 0, -1));
        //EAST
        surroundingLocations.add(location.add(1, 0, 0));
        //SOUTHEAST
        surroundingLocations.add(location.add(1, 0, 1));
        //SOUTH
        surroundingLocations.add(location.add(0, 0, 1));
        //SOUTHWEST
        surroundingLocations.add(location.add(-1, 0, 1));
        //WEST
        surroundingLocations.add(location.add(-1, 0, 0));
        //NORTHWEST
        surroundingLocations.add(location.add(-1, 0, -1));

        //DOWN
        surroundingLocations.add(location.add(0, -1, 0));
        //DOWN - NORTH
        surroundingLocations.add(location.add(0, -1, -1));
        //DOWN - NORTHEAST
        surroundingLocations.add(location.add(1, -1, -1));
        //DOWN - EAST
        surroundingLocations.add(location.add(1, -1, 0));
        //DOWN - SOUTHEAST
        surroundingLocations.add(location.add(1, -1, 1));
        //DOWN - SOUTH
        surroundingLocations.add(location.add(0, -1, 1));
        //DOWN - SOUTHWEST
        surroundingLocations.add(location.add(-1, -1, 1));
        //DOWN - WEST
        surroundingLocations.add(location.add(-1, -1, 0));
        //DOWN - NORTHWEST
        surroundingLocations.add(location.add(-1, -1, -1));

        //Detect blocks in surrounding locations.
        surroundingLocations.forEach(this::detectBlock);

    }

    private boolean confirmStructureRequirements(Map<List<BlockType>, List<Double>> flyBlocks, Map<List<BlockType>, Integer> countData) {
        if (craft.getType().getRequireWaterContact()) {
            if (!waterContact) {
                fail("Detection Failed - Water contact required but not found!");
                return false;
            }
        }
        for (List<BlockType> i : flyBlocks.keySet()) {
            Integer numberOfBlocks = countData.get(i);

            if (numberOfBlocks == null) {
                numberOfBlocks = 0;
            }

            float blockPercentage = (((float) numberOfBlocks / detectedHitBox.size()) * 100);
            Double minPercentage = flyBlocks.get(i).get(0);
            Double maxPercentage = flyBlocks.get(i).get(1);
            if (minPercentage < 10000.0) {
                if (blockPercentage < minPercentage) {
                    fail(String.format("Not enough flyblock" + ": %s %.2f%% < %.2f%%", i.get(0).getName(), blockPercentage, minPercentage));
                    return false;
                }
            } else {
                if (numberOfBlocks < flyBlocks.get(i).get(0) - 10000.0) {
                    fail(String.format("Not enough flyblock" + ": %s %d < %d", i.get(0).getName(), numberOfBlocks, flyBlocks.get(i).get(0).intValue() - 10000));
                    return false;
                }
            }
            if (maxPercentage < 10000.0) {
                if (blockPercentage > maxPercentage) {
                    fail(String.format("Too much flyblock" + ": %s %.2f%% > %.2f%%", i.get(0).getName(), blockPercentage, maxPercentage));
                    return false;
                }
            } else {
                if (numberOfBlocks > flyBlocks.get(i).get(1) - 10000.0) {
                    fail(String.format("Too much flyblock" + ": %s %d > %d", i.get(0).getName(), numberOfBlocks, flyBlocks.get(i).get(1).intValue() - 10000));
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isWithinLimit(int size, int min, int max) {
        if (size < min) {
            fail("Detection Failed - Craft too small! Min Size: " + min);
            return false;
        } else if (size > max) {
            fail("Detection Failed - Craft too large! Max Size: " + max);
            return false;
        } else {
            return true;
        }

    }

    private void fail(String message) {
        failed = true;
        failMessage = message;
    }

    private boolean notVisited(Vector3i location) {
        if (visited.contains(location)) {
            return false;
        } else {
            visited.add(location);
            return true;
        }
    }

    private void addToBlockList(Vector3i location) {
        detectedHitBox.add(location);
    }

    private void addToDetectionStack(Vector3i location) {
        blockStack.push(location);
    }

    private void addToBlockCount(List<BlockType> id) {
        Integer count = blockTypeCount.get(id);

        if (count == null) {
            count = 0;
        }

        blockTypeCount.put(id, count + 1);
    }

    private boolean isAllowedBlock(BlockType test) {

        return craft.getType().getAllowedBlocks().contains(test);
    }

    private boolean isForbiddenBlock(BlockType test) {

        return craft.getType().getForbiddenBlocks().contains(test);
    }

    private boolean isForbiddenSignString(String testString) {

        return craft.getType().getForbiddenSignStrings().contains(testString);
    }

    public boolean failed() {
        return failed;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public HashHitBox getHitBox() {
        return hitBox;
    }
}