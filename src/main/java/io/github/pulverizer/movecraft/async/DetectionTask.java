package io.github.pulverizer.movecraft.async;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.*;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

public class DetectionTask extends AsyncTask {
    private final Location<World> startLocation;
    private final Stack<Location<World>> blockStack = new Stack<>();
    private final HashHitBox detectedHitBox = new HashHitBox();
    private HashHitBox hitBox;
    private final HashSet<Location<World>> visited = new HashSet<>();
    private final HashMap<List<BlockType>, Integer> blockTypeCount = new HashMap<>();
    private Map<List<BlockType>, List<Double>> dynamicFlyBlocks;
    private boolean waterContact = false;
    private boolean foundCommanderSign = false;
    private boolean foundCommander = false;
    private boolean failed;
    private String failMessage;

    public DetectionTask(Craft craft, Location<World> startLocation) {
        super(craft, "Detection");
        this.startLocation = startLocation;
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

        if (foundCommanderSign && !foundCommander && !Sponge.getServer().getPlayer(craft.getOriginalPilot()).get().hasPermission("movecraft.bypasslock")) {
            fail("Not one of the registered commanders for this craft.");
        }

        if (failed()) {
            return;
        }

        if (isWithinLimit(detectedHitBox.size(), craft.getType().getMinSize(), craft.getType().getMaxSize()) && confirmStructureRequirements(flyBlocks, blockTypeCount)) {
            hitBox = detectedHitBox;
        }

        long endTime = System.currentTimeMillis();

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Detection Task Took: " + (endTime - startTime) + "ms");
    }

    private void detectBlock(Location<World> workingLocation) {

        if (!notVisited(workingLocation))
            return;

        if (workingLocation.getBlockType() == BlockTypes.AIR)
            return;

        BlockType testID = workingLocation.getBlockType();

        if (isForbiddenBlock(testID))
            fail("Detection Failed - Forbidden block found.");


        if (testID == BlockTypes.FLOWING_WATER || testID == BlockTypes.WATER) {
            waterContact = true;
        }

        if ((testID == BlockTypes.STANDING_SIGN || testID == BlockTypes.WALL_SIGN) && workingLocation.getTileEntity().isPresent()) {

            Sign s = (Sign) workingLocation.getTileEntity().get();
            if (s.lines().get(0).toString().equalsIgnoreCase("Commander:") && craft.getOriginalPilot() != null) {
                String playerName = Sponge.getServer().getPlayer(craft.getOriginalPilot()).get().getName();
                foundCommanderSign = true;
                if (s.lines().get(1).toString().equalsIgnoreCase(playerName) || s.lines().get(2).toString().equalsIgnoreCase(playerName) || s.lines().get(3).toString().equalsIgnoreCase(playerName)) {
                    foundCommander = true;
                }
            }

            for (int i = 0; i < 4; i++) {
                if (isForbiddenSignString(s.lines().get(i).toString())) {
                    fail("Detection Failed - Forbidden sign string found.");
                }
            }
        }


        if (!isAllowedBlock(testID))
            return;

        UUID player = craft.getOriginalPilot();
        if (player != null) {

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
    }

    private void detectSurrounding(Location<World> location) {

        HashSet<Location<World>> surroundingLocations = new HashSet<>();

        //Above
        surroundingLocations.add(location.getBlockRelative(Direction.UP));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.NORTH));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.NORTHEAST));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.EAST));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.SOUTHEAST));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.SOUTH));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.SOUTHWEST));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.WEST));
        surroundingLocations.add(location.getBlockRelative(Direction.UP).getBlockRelative(Direction.NORTHWEST));

        //Vertical
        surroundingLocations.add(location.getBlockRelative(Direction.NORTH));
        surroundingLocations.add(location.getBlockRelative(Direction.NORTHEAST));
        surroundingLocations.add(location.getBlockRelative(Direction.EAST));
        surroundingLocations.add(location.getBlockRelative(Direction.SOUTHEAST));
        surroundingLocations.add(location.getBlockRelative(Direction.SOUTH));
        surroundingLocations.add(location.getBlockRelative(Direction.SOUTHWEST));
        surroundingLocations.add(location.getBlockRelative(Direction.WEST));
        surroundingLocations.add(location.getBlockRelative(Direction.NORTHWEST));

        //Below
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.NORTH));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.NORTHEAST));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.EAST));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.SOUTHEAST));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.SOUTH));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.SOUTHWEST));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.WEST));
        surroundingLocations.add(location.getBlockRelative(Direction.DOWN).getBlockRelative(Direction.NORTHWEST));

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

    private boolean notVisited(Location<World> location) {
        if (visited.contains(location)) {
            return false;
        } else {
            visited.add(location);
            return true;
        }
    }

    private void addToBlockList(Location<World> location) {
        detectedHitBox.add(location.getBlockPosition());
    }

    private void addToDetectionStack(Location<World> location) {
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