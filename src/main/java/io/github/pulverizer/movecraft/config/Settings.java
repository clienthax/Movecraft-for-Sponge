package io.github.pulverizer.movecraft.config;

import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class Settings {
    public static boolean IGNORE_RESET = false;
    public static boolean Debug = false;
    public static int THREAD_POOL_SIZE = 5;
    public static List<Integer> DATA_BLOCKS;
    public static String LOCALE;
    public static ItemType PilotTool = ItemTypes.STICK;
    public static int SilhouetteViewDistance = 200;
    public static int SilhouetteBlockCount = 20;
    public static boolean CompatibilityMode = false;
    public static boolean DelayColorChanges = false;
    public static double SinkRateTicks = 20.0;
    public static double SinkCheckTicks = 100.0;
    public static double TracerRateTicks = 5.0;
    public static boolean ProtectPilotedCrafts = false;
    public static boolean DisableSpillProtection = false;
    public static boolean RequireCreatePerm = false;
    public static boolean TNTContactExplosives = true;
    public static int FadeWrecksAfter = 0;
    public static int ManOverBoardTimeout = 60;
    public static int FireballLifespan = 6;
    public static int RepairTicksPerBlock = 0;
    public static int BlockQueueChunkSize = 1000;
    public static double RepairMoneyPerBlock = 0.0;
    public static boolean FireballPenetration = true;
    public static boolean AllowCrewSigns = true;
    public static boolean SetHomeToCrewSign = true;
    public static Map<BlockType, Integer> DurabilityOverride;
    public static boolean IsPaper = false;
    public static HashSet<BlockType> DisableShadowBlocks;
}