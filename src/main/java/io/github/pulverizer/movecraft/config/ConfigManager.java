package io.github.pulverizer.movecraft.config;

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
        List<Integer> dataBlockList = new ArrayList<>();
        dataBlockList.add(23);
        dataBlockList.add(25);
        dataBlockList.add(33);
        dataBlockList.add(44);
        dataBlockList.add(50);
        dataBlockList.add(53);
        dataBlockList.add(54);
        dataBlockList.add(55);
        dataBlockList.add(61);
        dataBlockList.add(62);
        dataBlockList.add(63);
        dataBlockList.add(64);
        dataBlockList.add(65);
        configFile.addDefault("dataBlocks", dataBlockList);
        configFile.options().copyDefaults(true);
        //Movecraft.getInstance().saveConfig();
    }
}