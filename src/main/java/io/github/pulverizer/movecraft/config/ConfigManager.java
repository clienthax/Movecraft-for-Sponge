package io.github.pulverizer.movecraft.config;

import io.github.pulverizer.movecraft.Movecraft;
import ninja.leaping.configurate.ConfigurationNode;

class ConfigManager {

    private final ConfigurationNode mainConfig = Movecraft.getInstance().getMainConfigNode();

    public void loadConfig() {

        Settings.THREAD_POOL_SIZE = mainConfig.getNode("ThreadPoolSize").getInt(5);
        //Movecraft.getInstance().saveConfig();
    }
}