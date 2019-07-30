package io.github.pulverizer.movecraft.config;

import com.google.common.reflect.TypeToken;
import io.github.pulverizer.movecraft.Movecraft;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;

import java.util.ArrayList;
import java.util.List;

class ConfigManager {

    private ConfigurationNode mainConfig = Movecraft.getInstance().getMainConfigNode();

    public void loadConfig() {

        Settings.THREAD_POOL_SIZE = mainConfig.getNode("ThreadPoolSize").getInt(5);
        //Movecraft.getInstance().saveConfig();
    }
}