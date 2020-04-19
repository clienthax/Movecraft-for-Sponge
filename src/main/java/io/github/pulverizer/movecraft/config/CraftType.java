package io.github.pulverizer.movecraft.config;

import com.google.common.reflect.TypeToken;
import io.github.pulverizer.movecraft.Movecraft;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.spongepowered.api.CatalogType;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

final public class CraftType {
    private final boolean requireWaterContact;
    private final boolean tryNudge;
    private final boolean canCruise;
    private final boolean canTeleport;
    private final boolean canStaticMove;
    private final boolean canHover;
    private final boolean canDirectControl;
    private final boolean useGravity;
    private final boolean canHoverOverWater;
    private final boolean moveEntities;
    private final boolean onlyMovePlayers;
    private final boolean allowHorizontalMovement;
    private final boolean allowVerticalMovement;
    private final boolean allowRemoteSign;
    private final boolean allowCannonDirectorSign;
    private final boolean allowAADirectorSign;
    private final boolean cruiseOnPilot;
    private final boolean allowVerticalTakeoffAndLanding;
    private final boolean rotateAtMidpoint;
    private final boolean halfSpeedUnderwater;
    private final boolean focusedExplosion;
    private final boolean mustBeSubcraft;
    private final boolean keepMovingOnSink;
    private final boolean requiresSpecificPerms;
    private final int maxSize;
    private final int minSize;
    private final int minHeightLimit;
    private final int maxHeightLimit;
    private final int maxHeightAboveGround;
    private final int cruiseOnPilotVertMove;
    private final int maxStaticMove;
    private final int cruiseSkipBlocks;
    private final int vertCruiseSkipBlocks;
    private final int cruiseTickCooldown;
    private final int staticWaterLevel;
    private final int sinkRateTicks;
    private final int smokeOnSink;
    private final int tickCooldown;
    private final int hoverLimit;
    private final BlockType dynamicFlyBlock;
    private final double fuelBurnRate;
    private final double sinkPercent;
    private final double overallSinkPercent;
    private final double detectionMultiplier;
    private final double underwaterDetectionMultiplier;
    private final double dynamicLagSpeedFactor;
    private final double dynamicFlyBlockSpeedFactor;
    private final double chestPenalty;
    private final float explodeOnCrash;
    private final float collisionExplosion;
    private final String name;
    private final Set<BlockType> allowedBlocks;
    private final Set<BlockType> forbiddenBlocks;
    private final Set<String> forbiddenSignStrings;
    private final Map<List<BlockType>, List<Double>> flyBlocks;
    private final Map<List<BlockType>, List<Double>> moveBlocks;
    private final List<BlockType> harvestBlocks;
    private final List<BlockType> harvesterBladeBlocks;
    private final Set<BlockType> passthroughBlocks;
    private final Set<BlockType> furnaceBlocks;
    private final Map<ItemType, Double> fuelItems;

    public CraftType(File file) throws NullPointerException, IOException, ObjectMappingException {

        ConfigurationLoader<ConfigurationNode> configLoader = ConfigManager.createConfigLoader(file.toPath());
        ConfigurationNode configNode = configLoader.load();

        // Load config file

        name = configNode.getNode("name").getString().toLowerCase();
        maxSize = configNode.getNode("maxSize").getInt();
        minSize = configNode.getNode("minSize").getInt();

        requiresSpecificPerms = configNode.getNode("requiresSpecificPerms").getBoolean(true);
        allowedBlocks = configNode.getNode("allowedBlocks").getValue(new TypeToken<Set<BlockType>>() {}, new HashSet<>());
        furnaceBlocks = configNode.getNode("furnaceBlocks").getValue(new TypeToken<Set<BlockType>>() {}, new HashSet<>());
        if (furnaceBlocks.isEmpty()) {
            furnaceBlocks.add(BlockTypes.FURNACE);
            furnaceBlocks.add(BlockTypes.LIT_FURNACE);
        }

        fuelItems = configNode.getNode("fuelItems").getValue(new TypeToken<Map<ItemType, Double>>() {}, new HashMap<>());
        if (fuelItems.isEmpty()) {
            fuelItems.put(ItemTypes.COAL, 8.0);
            fuelItems.put(ItemTypes.COAL_BLOCK, 72.0);
        }


        forbiddenBlocks = configNode.getNode("forbiddenBlocks").getValue(new TypeToken<Set<BlockType>>() {}, new HashSet<>());
        forbiddenSignStrings = configNode.getNode("forbiddenSignStrings").getValue(new TypeToken<Set<String>>() {}, new HashSet<>());

        requireWaterContact = configNode.getNode("requireWaterContact").getBoolean(false);
        tryNudge = configNode.getNode("tryNudge").getBoolean(false);
        tickCooldown = (int) Math.ceil(20 / configNode.getNode("speed").getDouble());
        cruiseTickCooldown = (int) Math.ceil(20 / configNode.getNode("cruiseSpeed").getDouble());

        flyBlocks = blockTypeMapListFromNode(configNode.getNode("flyblocks").getValue(new TypeToken<Map<List<BlockType>, List<String>>>() {}, new HashMap<>()));
        moveBlocks = blockTypeMapListFromNode(configNode.getNode("moveBlocks").getValue(new TypeToken<Map<List<BlockType>, List<String>>>() {}, new HashMap<>()));

        canCruise = configNode.getNode("canCruise").getBoolean(false);
        canTeleport = configNode.getNode("canTeleport").getBoolean(false);
        cruiseOnPilot = configNode.getNode("cruiseOnPilot").getBoolean(false);
        cruiseOnPilotVertMove = configNode.getNode("cruiseOnPilotVertMove").getInt(0);
        allowVerticalMovement = configNode.getNode("allowVerticalMovement").getBoolean(true);
        rotateAtMidpoint = configNode.getNode("rotateAtMidpoint").getBoolean(false);
        allowHorizontalMovement = configNode.getNode("allowHorizontalMovement").getBoolean(true);
        allowRemoteSign = configNode.getNode("allowRemoteSign").getBoolean(true);
        allowCannonDirectorSign = configNode.getNode("allowCannonDirectorSign").getBoolean(true);
        allowAADirectorSign = configNode.getNode("allowAADirectorSign").getBoolean(true);
        canStaticMove = configNode.getNode("canStaticMove").getBoolean(false);
        maxStaticMove = configNode.getNode("maxStaticMove").getInt(10000);
        cruiseSkipBlocks = configNode.getNode("cruiseSkipBlocks").getInt(0);
        vertCruiseSkipBlocks = configNode.getNode("vertCruiseSkipBlocks").getInt(cruiseSkipBlocks);
        halfSpeedUnderwater = configNode.getNode("halfSpeedUnderwater").getBoolean(false);
        focusedExplosion = configNode.getNode("focusedExplosion").getBoolean(false);
        mustBeSubcraft = configNode.getNode("mustBeSubcraft").getBoolean(false);
        staticWaterLevel = configNode.getNode("staticWaterLevel").getInt(0);
        fuelBurnRate = configNode.getNode("fuelBurnRate").getDouble(0);
        sinkPercent = configNode.getNode("sinkPercent").getDouble(0);
        overallSinkPercent = configNode.getNode("overallSinkPercent").getDouble(0);
        detectionMultiplier = configNode.getNode("detectionMultiplier").getDouble(0);
        underwaterDetectionMultiplier = configNode.getNode("underwaterDetectionMultiplier").getDouble(detectionMultiplier);
        //TODO - should the default be Settings.SinkRateTicks?
        sinkRateTicks = configNode.getNode("sinkTickRate").getInt(0);
        keepMovingOnSink = configNode.getNode("keepMovingOnSink").getBoolean(false);
        smokeOnSink = configNode.getNode("smokeOnSink").getInt(0);
        explodeOnCrash = configNode.getNode("explodeOnCrash").getFloat(0);
        collisionExplosion = configNode.getNode("collisionExplosion").getFloat(0);
        minHeightLimit = configNode.getNode("minHeightLimit").getInt(0);
        maxHeightLimit = configNode.getNode("minHeightLimit").getInt(255);
        maxHeightAboveGround = configNode.getNode("maxHeightAboveGround").getInt(-1);
        canDirectControl = configNode.getNode("canDirectControl").getBoolean(true);
        canHover = configNode.getNode("canHover").getBoolean(false);
        canHoverOverWater = configNode.getNode("canHoverOverWater").getBoolean(true);
        moveEntities = configNode.getNode("moveEntities").getBoolean(true);

        onlyMovePlayers = configNode.getNode("onlyMovePlayers").getBoolean(true);

        useGravity = configNode.getNode("useGravity").getBoolean(false);

        hoverLimit = configNode.getNode("hoverLimit").getInt(0);
        harvestBlocks = configNode.getNode("harvestBlocks").getList(TypeToken.of(BlockType.class), new ArrayList<>());
        harvesterBladeBlocks = configNode.getNode("harvesterBladeBlocks").getList(TypeToken.of(BlockType.class), new ArrayList<>());
        passthroughBlocks = configNode.getNode("passthroughBlocks").getValue(new TypeToken<Set<BlockType>>() {}, new HashSet<>());
        allowVerticalTakeoffAndLanding = configNode.getNode("allowVerticalTakeoffAndLanding").getBoolean(true);

        dynamicLagSpeedFactor = configNode.getNode("dynamicLagSpeedFactor").getDouble(0);
        dynamicFlyBlockSpeedFactor = configNode.getNode("dynamicFlyBlockSpeedFactor").getDouble(0);
        dynamicFlyBlock = configNode.getNode("dynamicFlyBlock").getValue(TypeToken.of(BlockType.class), BlockTypes.AIR);
        chestPenalty = configNode.getNode("chestPenalty").getDouble(0);

        // Save config file
        //TODO - When I've got it to stop destroying tidy configs
        //configLoader.save(configNode);
    }

    private Map<List<BlockType>, List<Double>> blockTypeMapListFromNode(Map<List<BlockType>, List<String>> configMap) {
        HashMap<List<BlockType>, List<Double>> returnMap = new HashMap<>();

        configMap.forEach((blockTypeList, minMaxValues) -> {
            // then read in the limitation values, low and high
            ArrayList<Double> limitList = new ArrayList<>();
            for (String str : minMaxValues) {
                if (str.contains("N")) { // a # indicates a specific quantity, IE: #2 for exactly 2 of the block
                    String[] parts = str.split("N");
                    Double val = Double.valueOf(parts[1]);
                    limitList.add(10000d + val);  // limit greater than 10000 indicates an specific quantity (not a ratio)
                } else {
                    Double val = Double.valueOf(str);
                    limitList.add(val);
                }
            }

            returnMap.put(blockTypeList, limitList);
        });

        return returnMap;
    }

    public String getName() {
        return name;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getMinSize() {
        return minSize;
    }

    public boolean requiresSpecificPerms() {
        return requiresSpecificPerms;
    }

    public Set<BlockType> getAllowedBlocks() {
        return allowedBlocks;
    }

    public Set<BlockType> getForbiddenBlocks() {
        return forbiddenBlocks;
    }

    public Set<String> getForbiddenSignStrings() {
        return forbiddenSignStrings;
    }

    //TODO - Remove this temp method
    @Deprecated
    public boolean blockedByWater() {
        return passthroughBlocks.contains(BlockTypes.WATER) && passthroughBlocks.contains(BlockTypes.FLOWING_WATER);
    }

    public boolean getRequireWaterContact() {
        return requireWaterContact;
    }

    public boolean getCanCruise() {
        return canCruise;
    }

    public int getCruiseSkipBlocks() {
        return cruiseSkipBlocks;
    }

    public int getVertCruiseSkipBlocks() {
        return vertCruiseSkipBlocks;
    }

    public int maxStaticMove() {
        return maxStaticMove;
    }

    public int getStaticWaterLevel() {
        return staticWaterLevel;
    }

    public boolean getCanTeleport() {
        return canTeleport;
    }

    public boolean getCanStaticMove() {
        return canStaticMove;
    }

    public boolean getCruiseOnPilot() {
        return cruiseOnPilot;
    }

    public int getCruiseOnPilotVertMove() {
        return cruiseOnPilotVertMove;
    }

    public boolean allowVerticalMovement() {
        return allowVerticalMovement;
    }

    public boolean rotateAtMidpoint() {
        return rotateAtMidpoint;
    }

    public boolean allowHorizontalMovement() {
        return allowHorizontalMovement;
    }

    public boolean allowRemoteSign() {
        return allowRemoteSign;
    }

    public boolean allowCannonDirectorSign() {
        return allowCannonDirectorSign;
    }

    public boolean allowAADirectorSign() {
        return allowAADirectorSign;
    }

    public double getFuelBurnRate() {
        return fuelBurnRate;
    }

    public Map<ItemType, Double> getFuelItems() {
        return fuelItems;
    }

    public double getSinkPercent() {
        return sinkPercent;
    }

    public double getOverallSinkPercent() {
        return overallSinkPercent;
    }

    public double getDetectionMultiplier() {
        return detectionMultiplier;
    }

    public double getUnderwaterDetectionMultiplier() {
        return underwaterDetectionMultiplier;
    }

    public int getSinkRateTicks() {
        return sinkRateTicks;
    }

    public boolean getKeepMovingOnSink() {
        return keepMovingOnSink;
    }

    public float getExplodeOnCrash() {
        return explodeOnCrash;
    }

    public int getSmokeOnSink() {
        return smokeOnSink;
    }

    public float getCollisionExplosion() {
        return collisionExplosion;
    }

    public int getTickCooldown() {
        return tickCooldown;
    }

    public int getCruiseTickCooldown() {
        return cruiseTickCooldown == 0 ? tickCooldown : cruiseTickCooldown;
    }

    public boolean getHalfSpeedUnderwater() {
        return halfSpeedUnderwater;
    }

    public boolean getFocusedExplosion() {
        return focusedExplosion;
    }

    public boolean mustBeSubcraft() {
        return mustBeSubcraft;
    }

    public boolean isTryNudge() {
        return tryNudge;
    }

    public Map<List<BlockType>, List<Double>> getFlyBlocks() {
        return flyBlocks;
    }

    public Map<List<BlockType>, List<Double>> getMoveBlocks() {
        return moveBlocks;
    }

    public int getMaxHeightLimit() {
        return maxHeightLimit;
    }

    public int getMinHeightLimit() {
        return minHeightLimit;
    }

    public int getMaxHeightAboveGround() {
        return maxHeightAboveGround;
    }

    public boolean getCanHover() {
        return canHover;
    }

    public boolean getCanDirectControl() {
        return canDirectControl;
    }

    public int getHoverLimit() {
        return hoverLimit;
    }

    public List<BlockType> getHarvestBlocks() {
        return harvestBlocks;
    }

    public List<BlockType> getHarvesterBladeBlocks() {
        return harvesterBladeBlocks;
    }

    public Set<BlockType> getFurnaceBlocks() {
        return furnaceBlocks;
    }

    public boolean getCanHoverOverWater() {
        return canHoverOverWater;
    }

    public boolean getMoveEntities() {
        return moveEntities;
    }

    public boolean getUseGravity() {
        return useGravity;
    }

    public boolean allowVerticalTakeoffAndLanding() {
        return allowVerticalTakeoffAndLanding;
    }

    public double getDynamicLagSpeedFactor() {
        return dynamicLagSpeedFactor;
    }

    public double getDynamicFlyBlockSpeedFactor() {
        return dynamicFlyBlockSpeedFactor;
    }

    public BlockType getDynamicFlyBlock() {
        return dynamicFlyBlock;
    }

    public double getChestPenalty() {
        return chestPenalty;
    }

    public Set<BlockType> getPassthroughBlocks() {
        return passthroughBlocks;
    }

    public boolean onlyMovePlayers() {
        return onlyMovePlayers;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}