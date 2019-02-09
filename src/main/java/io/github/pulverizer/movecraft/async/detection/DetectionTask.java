package io.github.pulverizer.movecraft.async.detection;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.async.AsyncTask;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.utils.*;

import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

public class DetectionTask extends AsyncTask {
    private final MovecraftLocation startLocation;
    private final int minSize;
    private final int maxSize;
    private final Stack<MovecraftLocation> blockStack = new Stack<>();
    private final HashHitBox blockList = new HashHitBox();
    private final HashSet<MovecraftLocation> visited = new HashSet<>();
    private final HashMap<List<BlockType>, Integer> blockTypeCount = new HashMap<>();
    private final DetectionTaskData data;
    private final World world;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int minY;
    private Map<List<BlockType>, List<Double>> dFlyBlocks;
    private int foundDynamicFlyBlock = 0;

    public DetectionTask(Craft c, MovecraftLocation startLocation, Player player) {
        super(c);
        this.startLocation = startLocation;
        this.minSize = craft.getType().getMinSize();
        this.maxSize = craft.getType().getMaxSize();
        this.world = craft.getW();
        data = new DetectionTaskData(craft.getW(), player, craft.getNotificationPlayer(), craft.getType().getAllowedBlocks(), craft.getType().getForbiddenBlocks(),
                craft.getType().getForbiddenSignStrings());
    }

    @Override
    public void excecute() {
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

    private void detectBlock(int x, int y, int z) {

        MovecraftLocation workingLocation = new MovecraftLocation(x, y, z);

        if (notVisited(workingLocation, visited)) {

            BlockType testID = BlockTypes.AIR;
            try {
                testID = data.getWorld().getBlockType(x, y, z);
            } catch (Exception e) {
                fail("Detection Failed - Craft too large! Max Size: " + maxSize);
            }

            if ((testID == BlockTypes.FLOWING_WATER) || (testID == BlockTypes.WATER)) {
                data.setWaterContact(true);
            }
            if (testID == BlockTypes.STANDING_SIGN || testID == BlockTypes.WALL_SIGN) {
                BlockSnapshot snapshot = data.getWorld().createSnapshot(x, y, z);

                if(snapshot.getLocation().isPresent() && snapshot.getLocation().get().getTileEntity().isPresent()) {

                    Sign s = (Sign) snapshot.getLocation().get().getTileEntity().get();
                    if (s.lines().get(0).toString().equalsIgnoreCase("Pilot:") && data.getPlayer() != null) {
                        String playerName = data.getPlayer().getName();
                        boolean foundPilot = false;
                        if (s.lines().get(1).toString().equalsIgnoreCase(playerName) || s.lines().get(2).toString().equalsIgnoreCase(playerName)
                                || s.lines().get(3).toString().equalsIgnoreCase(playerName)) {
                            foundPilot = true;
                        }
                        if (!foundPilot && (!data.getPlayer().hasPermission("movecraft.bypasslock"))) {
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
                // check for double chests
                if (testID == BlockTypes.CHEST) {
                    boolean foundDoubleChest = false;
                    if (data.getWorld().getBlockType(x - 1, y, z) == BlockTypes.CHEST) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockType(x + 1, y, z) == BlockTypes.CHEST) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockType(x, y, z - 1) == BlockTypes.CHEST) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockType(x, y, z + 1) == BlockTypes.CHEST) {
                        foundDoubleChest = true;
                    }
                    if (foundDoubleChest) {
                        fail("Detection Failed - Double chest found.");
                    }
                }
                // check for double trapped chests
                if (testID == BlockTypes.TRAPPED_CHEST) {
                    boolean foundDoubleChest = false;
                    if (data.getWorld().getBlockType(x - 1, y, z) == BlockTypes.TRAPPED_CHEST) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockType(x + 1, y, z) == BlockTypes.TRAPPED_CHEST) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockType(x, y, z - 1) == BlockTypes.TRAPPED_CHEST) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockType(x, y, z + 1) == BlockTypes.TRAPPED_CHEST) {
                        foundDoubleChest = true;
                    }
                    if (foundDoubleChest) {
                        fail("Detection Failed - Double chest found.");
                    }
                }

                Location loc = new Location<>(data.getWorld(), x, y, z);
                Player p;
                if (data.getPlayer() == null) {
                    p = data.getNotificationPlayer();
                } else {
                    p = data.getPlayer();
                }
                if (p != null) {

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

    private boolean notVisited(MovecraftLocation l, HashSet<MovecraftLocation> locations) {
        if (locations.contains(l)) {
            return false;
        } else {
            locations.add(l);
            return true;
        }
    }

    private void addToBlockList(MovecraftLocation l) {
        blockList.add(l);
    }

    private void addToDetectionStack(MovecraftLocation l) {
        blockStack.push(l);
    }

    private void addToBlockCount(List<BlockType> id) {
        Integer count = blockTypeCount.get(id);

        if (count == null) {
            count = 0;
        }

        blockTypeCount.put(id, count + 1);
    }

    private void detectSurrounding(MovecraftLocation l) {
        int x = l.getX();
        int y = l.getY();
        int z = l.getZ();

        for (int xMod = -1; xMod < 2; xMod += 2) {

            for (int yMod = -1; yMod < 2; yMod++) {

                detectBlock(x + xMod, y + yMod, z);

            }

        }

        for (int zMod = -1; zMod < 2; zMod += 2) {

            for (int yMod = -1; yMod < 2; yMod++) {

                detectBlock(x, y + yMod, z + zMod);

            }

        }

        for (int yMod = -1; yMod < 2; yMod += 2) {

            detectBlock(x, y + yMod, z);

        }

    }

    private void calculateBounds(MovecraftLocation l) {
        if (l.getX() > maxX) {
            maxX = l.getX();
        }
        if (l.getY() > maxY) {
            maxY = l.getY();
        }
        if (l.getZ() > maxZ) {
            maxZ = l.getZ();
        }
        if (data.getMinX() == null || l.getX() < data.getMinX()) {
            data.setMinX(l.getX());
        }
        if (l.getY() < minY) {
            minY = l.getY();
        }
        if (data.getMinZ() == null || l.getZ() < data.getMinZ()) {
            data.setMinZ(l.getZ());
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