package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftDetectEvent;
import io.github.pulverizer.movecraft.sign.CommanderSign;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import io.github.pulverizer.movecraft.utils.HashHitBox;
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
    private int commanderSignId;

    private boolean failed;
    private String failMessage;

    public DetectionTask(Craft craft, Location<World> startLocation) {
        super(craft, "Detection");
        this.startLocation = startLocation.getBlockPosition();
        world = startLocation.getExtent();
    }

    @Override
    public void execute() {

        // Get craft type flyblocks
        Map<List<BlockType>, List<Double>> flyBlocks = craft.getType().getFlyBlocks();
        dynamicFlyBlocks = flyBlocks;

        // Detect blocks
        blockStack.push(startLocation);
        do {
            detectSurrounding(blockStack.pop());
        } while (!blockStack.isEmpty());

        // Get the player
        Player player = Sponge.getServer().getPlayer(craft.commandeeredBy()).orElse(null);

        // Check commander sign
        if (foundCommanderSign) {
            HashMap<UUID, Boolean> commanderSignMemberMap = CommanderSign.getMembers(commanderSignUsername, commanderSignId);

            if (commanderSignMemberMap == null) {
                Movecraft.getInstance().getLogger().warn("Commander sign not registered in database");

            } else if (!commanderSignMemberMap.containsKey(craft.commandeeredBy())
                    && !Sponge.getServer().getPlayer(craft.commandeeredBy()).get().hasPermission("movecraft.bypasslock")) {

                fail("Not one of the registered commanders for this craft.");
            }
        }

        // Run final checks
        isWithinLimit(detectedHitBox.size(), craft.getType().getMinSize(), craft.getType().getMaxSize());
        confirmStructureRequirements(flyBlocks, blockTypeCount);

        // Check that the player is still online
        // If they are offline we cancel the Detection Task
        if (player == null) {
            fail("Player went offline.");
        }

        if (!failed()) {
            // Submit the hitbox
            hitBox = detectedHitBox;
        }
    }

    @Override
    public void postProcess() {

        Player player = Sponge.getServer().getPlayer(craft.commandeeredBy()).orElse(null);

        if (!failed()) {
            boolean failed = false;
            Craft parentCraft = null;

            for (Craft testCraft : CraftManager.getInstance().getCraftsInWorld(craft.getWorld())) {
                if (testCraft.getHitBox().intersects(getHitBox())) {

                    parentCraft = testCraft;
                    break;
                }
            }

            Movecraft.getInstance().getLogger().info("Subcraft: " + (parentCraft != null));
            Movecraft.getInstance().getLogger().info("Size: " + getHitBox().size());
            if (parentCraft != null && player != null) {
                Movecraft.getInstance().getLogger().info("Parent Craft: " + parentCraft.getHitBox().size() + "   Commander: " + player);

                if (!parentCraft.isCrewMember(player.getUniqueId())) {
                    // Player is already controlling a craft
                    fail("You are not in the crew of this craft.");

                } else if (parentCraft.getType() == craft.getType() || parentCraft.getHitBox().size() <= getHitBox().size()) {
                    fail("Craft is already being controlled by another player.");

                } else {
                    // if this is a different type than the overlapping craft, and is smaller, this must be a child craft, like a fighter on a carrier
                    if (!parentCraft.isNotProcessing()) {
                        fail("Parent Craft is busy.");
                    }

                    parentCraft.setHitBox(new HashHitBox(CollectionUtils.filter(parentCraft.getHitBox(), getHitBox())));
                    parentCraft.setInitialSize(parentCraft.getInitialSize() - getHitBox().size());
                }
            }

            if (craft.getType().getMustBeSubcraft() && parentCraft == null) {
                fail("Craft must be part of another craft!");
            }

            if (!failed()) {
                craft.setInitialSize(getHitBox().size());
                craft.setHitBox(getHitBox());

                final int waterLine = craft.getWaterLine();
                if (!craft.getType().blockedByWater() && craft.getHitBox().getMinY() <= waterLine) {
                    for (Vector3i location : craft.getHitBox().boundingHitBox()) {
                        if (location.getY() <= waterLine) {
                            craft.getPhasedBlocks().add(BlockTypes.WATER.getDefaultState().snapshotFor(new Location<>(craft.getWorld(), location)));
                        }
                    }
                }

                if (craft.getHitBox() != null) {

                    if (player != null) {
                        //TODO We need a better way of doing this
                        if (!craft.getType().getMustBeSubcraft() || !craft.getType().getCruiseOnPilot()) {
                            craft.setCommander(craft.commandeeredBy());
                        }

                        player.sendMessage(Text.of("Successfully commandeered " + craft.getType().getName() + " Size: " + craft.getHitBox().size()));
                        Movecraft.getInstance().getLogger().info("New Craft Detected! Commandeered By: " + player.getName() + " CraftType: " + craft.getType().getName() + " Size: " + craft.getHitBox().size() + " Location: " + craft.getHitBox().getMidPoint().toString());
                    } else {
                        Movecraft.getInstance().getLogger().info("New Craft Detected! Commandeered By: " + craft.commandeeredBy() + " CraftType: " + craft.getType().getName() + " Size: " + craft.getHitBox().size() + " Location: " + craft.getHitBox().getMidPoint().toString());
                    }

                    CraftManager.getInstance().addCraft(craft);
                } else {
                    fail("NULL Hitbox!");
                }
            }
        }

        // Check if the task failed
        if (failed()) {
            if (player != null) {
                player.sendMessage(Text.of(getFailMessage()));
            }

            Movecraft.getInstance().getLogger().info("Craft " + super.type + " Failed: " + getFailMessage());
        }

        if (craft.getHitBox() != null) {
            CraftDetectEvent event = new CraftDetectEvent(craft);
            Sponge.getEventManager().post(event);
        }

        craft.setProcessing(false);
    }

    private void detectBlock(Vector3i workingLocation) {

        if (!notVisited(workingLocation))
            return;

        BlockType testType = world.getBlockType(workingLocation);

        if (testType == BlockTypes.AIR)
            return;

        if (isForbiddenBlock(testType))
            fail("Detection Failed - Forbidden block found.");


        if (testType == BlockTypes.FLOWING_WATER || testType == BlockTypes.WATER) {
            waterContact = true;
        }

        if (testType == BlockTypes.STANDING_SIGN || testType == BlockTypes.WALL_SIGN && world.getTileEntity(workingLocation).isPresent()) {

            ListValue<Text> signText = ((Sign) world.getTileEntity(workingLocation).get()).lines();
            if (signText.get(0).toPlain().equalsIgnoreCase("Commander:")) {

                String testUsername = signText.get(1).toPlain();
                int testId = Integer.parseInt(signText.get(2).toPlain());

                Map.Entry<Date, Time> timestamp = CommanderSign.getCreationTimeStamp(testUsername, testId);

                if (timestamp != null) {

                    if (!foundCommanderSign) {
                        foundCommanderSign = true;
                        commanderSignDate = timestamp.getKey();
                        commanderSignTime = timestamp.getValue();
                        commanderSignUsername = testUsername;
                        commanderSignId = testId;

                    } else if (timestamp.getKey().before(commanderSignDate)) {
                        commanderSignDate = timestamp.getKey();
                        commanderSignTime = timestamp.getValue();
                        commanderSignUsername = testUsername;
                        commanderSignId = testId;

                    } else if (timestamp.getKey().equals(commanderSignDate) && timestamp.getValue().before(commanderSignTime)) {
                        commanderSignDate = timestamp.getKey();
                        commanderSignTime = timestamp.getValue();
                        commanderSignUsername = testUsername;
                        commanderSignId = testId;

                    }
                }
            }

            signText.forEach(line -> {
                if (isForbiddenSignString(line.toString())) {
                    fail("Detection Failed - Forbidden sign string found.");
                }
            });
        }

        if (!isAllowedBlock(testType))
            return;

        addToBlockList(workingLocation);
        for (List<BlockType> flyBlockDef : dynamicFlyBlocks.keySet()) {
            if (flyBlockDef.contains(testType)) {
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