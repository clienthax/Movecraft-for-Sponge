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

        try {
            Settings.DATA_BLOCKS = mainConfig.getNode("dataBlocks").getList(TypeToken.of(BlockType.class), dataBlockList);
        } catch (ObjectMappingException e) {
            e.printStackTrace();
        }
        Settings.THREAD_POOL_SIZE = mainConfig.getNode("ThreadPoolSize").getInt(5);
        Settings.IGNORE_RESET = mainConfig.getNode("safeReload").getBoolean(false);
        //Movecraft.getInstance().saveConfig();
    }
}