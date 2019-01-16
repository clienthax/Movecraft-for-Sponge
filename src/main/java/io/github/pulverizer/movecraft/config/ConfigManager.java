package io.github.pulverizer.movecraft.config;

import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.config.ConfigDir;

import java.util.ArrayList;
import java.util.List;

class ConfigManager {
    @ConfigDir(sharedRoot = false)
    private final ConfigDir configFile;

    public ConfigManager(ConfigDir configFile) {
        this.configFile = configFile;
    }

    public void loadConfig() {
        setupDefaults();
        Settings.DATA_BLOCKS = configFile.getIntegerList("dataBlocks");
        Settings.THREAD_POOL_SIZE = configFile.getInt("ThreadPoolSize");
        Settings.IGNORE_RESET = configFile.getBoolean("safeReload");

    }

    private void setupDefaults() {
        configFile.addDefault("ThreadPoolSize", 5);
        configFile.addDefault("safeReload", false);
        List<BlockType> dataBlockList = new ArrayList<>();
        dataBlockList.add(BlockTypes.DISPENSER);
        dataBlockList.add(BlockTypes.NOTEBLOCK);
        dataBlockList.add(BlockTypes.PISTON);
        dataBlockList.add(BlockTypes.STONE_SLAB);
        dataBlockList.add(BlockTypes.TORCH);
        dataBlockList.add(BlockTypes.OAK_STAIRS);
        dataBlockList.add(BlockTypes.CHEST);
        dataBlockList.add(BlockTypes.REDSTONE_WIRE);
        dataBlockList.add(BlockTypes.FURNACE);
        dataBlockList.add(BlockTypes.LIT_FURNACE);
        dataBlockList.add(BlockTypes.STANDING_SIGN);
        dataBlockList.add(BlockTypes.WOODEN_DOOR);
        dataBlockList.add(BlockTypes.LADDER);
        configFile.addDefault("dataBlocks", dataBlockList);
        configFile.options().copyDefaults(true);
        //Movecraft.getInstance().saveConfig();
    }
}