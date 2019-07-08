package io.github.pulverizer.movecraft.async.detection;

import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.async.AsyncTask;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.*;

import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

public class DetectionTask extends AsyncTask {
    private final Location<World> startLocation;
    private final int minSize;
    private final int maxSize;
    private final Stack<Location<World>> blockStack = new Stack<>();
    private final HashHitBox blockList = new HashHitBox();
    private final HashSet<Location<World>> visited = new HashSet<>();
    private final HashMap<List<BlockType>, Integer> blockTypeCount = new HashMap<>();
    private final DetectionTaskData data;
    private final World world;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int minY;
    private Map<List<BlockType>, List<Double>> dFlyBlocks;
    private int foundDynamicFlyBlock = 0;

    public DetectionTask(Craft c, Location<World> startLocation, UUID player) {
        super(c);
        this.startLocation = startLocation;
        this.minSize = craft.getType().getMinSize();
        this.maxSize = craft.getType().getMaxSize();
        this.world = craft.getWorld();
        data = new DetectionTaskData(craft.getWorld(), player, craft.getPilot(), craft.getType().getAllowedBlocks(), craft.getType().getForbiddenBlocks(),
                craft.getType().getForbiddenSignStrings());
    }

    @Override
    public void execute() {
        Map<List<BlockType>, List<Double>> flyBlocks = getCraft().getType().getFlyBlocks();
        dFlyBlocks = flyBlocks;

        blockStack.push(startLocation);
        do {
            detectSurrounding(blockStack.pop());
        } while (!blockStack.isEmpty());
        if (data.failed()) {
            return;
        }
        if (getCraft().getType().getDynamicFlyBlockSpeedFactor() != 0.0) {
            int totalBlocks = blockList.size();
            double ratio = (double) foundDynamicFlyBlock / totalBlocks;
            double foundMinimum = 0.0;
            for (List<BlockType> i : flyBlocks.keySet()) {
                if (i.contains(getCraft().getType().getDynamicFlyBlock()))
                    foundMinimum = flyBlocks.get(i).get(0);
            }
            ratio = ratio - (foundMinimum / 100.0);
            ratio = ratio * getCraft().getType().getDynamicFlyBlockSpeedFactor();
            data.dynamicFlyBlockSpeedMultiplier = ratio;
        }
        if (isWithinLimit(blockList.size(), minSize, maxSize)) {
            data.setBlockList(blockList);
            if (confirmStructureRequirements(flyBlocks, blockTypeCount)) {
                data.setHitBox(blockList);

            }
        }
    }

    private void detectBlock(Location<World> workingLocation) {

        if (notVisited(workingLocation, visited)) {

            BlockType testID = BlockTypes.AIR;
            try {
                testID = workingLocation.getBlockType();
            } catch (Exception e) {
                fail("Detection Failed - Craft too large! Max Size: " + maxSize);
            }

            if ((testID == BlockTypes.FLOWING_WATER) || (testID == BlockTypes.WATER)) {
                data.setWaterContact(true);
            }
            if (testID == BlockTypes.STANDING_SIGN || testID == BlockTypes.WALL_SIGN) {
                BlockSnapshot snapshot = workingLocation.createSnapshot();

                if(snapshot.getLocation().isPresent() && snapshot.getLocation().get().getTileEntity().isPresent()) {

                    Sign s = (Sign) snapshot.getLocation().get().getTileEntity().get();
                    if (s.lines().get(0).toString().equalsIgnoreCase("Pilot:") && data.getPlayer() != null) {
                        String playerName = Sponge.getServer().getPlayer(data.getPlayer()).get().getName();
                        boolean foundPilot = false;
                        if (s.lines().get(1).toString().equalsIgnoreCase(playerName) || s.lines().get(2).toString().equalsIgnoreCase(playerName)
                                || s.lines().get(3).toString().equalsIgnoreCase(playerName)) {
                            foundPilot = true;
                        }
                        if (!foundPilot && (!Sponge.getServer().getPlayer(data.getPlayer()).get().hasPermission("movecraft.bypasslock"))) {
                            fail("Not one of the registered pilots on this craft.");
                        }
                    }
                    for (int i = 0; i < 4; i++) {
                        if (isForbiddenSignString(s.lines().get(i).toString())) {
                            fail("Detection Failed - Forbidden sign string found.");
                        }
                    }
                }
            }
            if (isForbiddenBlock(testID)) {
                fail("Detection Failed- Forbidden block found.");
            } else if (isAllowedBlock(testID)) {

                UUID player;
                if (data.getPlayer() == null) {
                    player = data.getNotificationPlayer();
                } else {
                    player = data.getPlayer();
                }
                if (player != null) {

                    addToBlockList(workingLocation);
                    BlockType blockID = testID;
                    for (List<BlockType> flyBlockDef : dFlyBlocks.keySet()) {
                        if (flyBlockDef.contains(blockID)) {
                            addToBlockCount(flyBlockDef);
                        } else {
                            addToBlockCount(null);
                        }
                    }
                    if (getCraft().getType().getDynamicFlyBlockSpeedFactor() != 0.0) {
                        if (blockID == getCraft().getType().getDynamicFlyBlock()) {
                            foundDynamicFlyBlock++;
                        }
                    }

                    if (isWithinLimit(blockList.size(), 0, maxSize)) {

                        addToDetectionStack(workingLocation);

                        calculateBounds(workingLocation);

                    }
                }
            }
        }
    }

    private boolean isAllowedBlock(BlockType test) {

        for (BlockType i : data.getAllowedBlocks()) {
            if (i == test) {
                return true;
            }
        }

        return false;
    }

    private boolean isForbiddenBlock(BlockType test) {

        for (BlockType i : data.getForbiddenBlocks()) {
            if (i == test) {
                return true;
            }
        }

        return false;
    }

    private boolean isForbiddenSignString(String testString) {

        for (String s : data.getForbiddenSignStrings()) {
            if (testString.equals(s)) {
                return true;
            }
        }

        return false;
    }

    public DetectionTaskData getData() {
        return data;
    }

    private boolean notVisited(Location<World> location, HashSet<Location<World>> locations) {
        if (locations.contains(location)) {
            return false;
        } else {
            locations.add(location);
            return true;
        }
    }

    private void addToBlockList(Location<World> location) {
        blockList.add(new MovecraftLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
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

    private void calculateBounds(Location<World> location) {
        if (location.getX() > maxX) {
            maxX = location.getBlockX();
        }
        if (location.getY() > maxY) {
            maxY = location.getBlockY();
        }
        if (location.getZ() > maxZ) {
            maxZ = location.getBlockZ();
        }
        if (data.getMinX() == null || location.getX() < data.getMinX()) {
            data.setMinX(location.getBlockX());
        }
        if (location.getY() < minY) {
            minY = location.getBlockY();
        }
        if (data.getMinZ() == null || location.getZ() < data.getMinZ()) {
            data.setMinZ(location.getBlockZ());
        }
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

    private MovecraftLocation[] finaliseBlockList(HashSet<MovecraftLocation> blockSet) {
        // MovecraftLocation[] finalList=blockSet.toArray( new
        // MovecraftLocation[1] );
        ArrayList<MovecraftLocation> finalList = new ArrayList<>();

        // Sort the blocks from the bottom up to minimize lower altitude block
        // updates
        for (int posx = data.getMinX(); posx <= this.maxX; posx++) {
            for (int posz = data.getMinZ(); posz <= this.maxZ; posz++) {
                for (int posy = this.minY; posy <= this.maxY; posy++) {
                    MovecraftLocation test = new MovecraftLocation(posx, posy, posz);
                    if (blockSet.contains(test))
                        finalList.add(test);
                }
            }
        }
        return finalList.toArray(new MovecraftLocation[1]);
    }

    private boolean confirmStructureRequirements(Map<List<BlockType>, List<Double>> flyBlocks,
                                                 Map<List<BlockType>, Integer> countData) {
        if (getCraft().getType().getRequireWaterContact()) {
            if (!data.getWaterContact()) {
                fail("Detection Failed - Water contact required but not found!");
                return false;
            }
        }
        for (List<BlockType> i : flyBlocks.keySet()) {
            Integer numberOfBlocks = countData.get(i);

            if (numberOfBlocks == null) {
                numberOfBlocks = 0;
            }

            float blockPercentage = (((float) numberOfBlocks / data.getBlockList().size()) * 100);
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

    private void fail(String message) {
        data.setFailed(true);
        data.setFailMessage(message);
    }
}