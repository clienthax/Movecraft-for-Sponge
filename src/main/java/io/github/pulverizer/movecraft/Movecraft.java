package io.github.pulverizer.movecraft;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.*;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.plugin.Plugin;

import io.github.pulverizer.movecraft.async.AsyncManager;
//import io.github.pulverizer.movecraft.commands.*;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.listener.BlockListener;
import io.github.pulverizer.movecraft.listener.InteractListener;
import io.github.pulverizer.movecraft.listener.PlayerListener;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import io.github.pulverizer.movecraft.mapUpdater.MapUpdateManager;
import io.github.pulverizer.movecraft.sign.AntiAircraftDirectorSign;
import io.github.pulverizer.movecraft.sign.AscendSign;
import io.github.pulverizer.movecraft.sign.CannonDirectorSign;
import io.github.pulverizer.movecraft.sign.ContactsSign;
import io.github.pulverizer.movecraft.sign.CraftSign;
import io.github.pulverizer.movecraft.sign.CrewSign;
import io.github.pulverizer.movecraft.sign.CruiseSign;
import io.github.pulverizer.movecraft.sign.DescendSign;
import io.github.pulverizer.movecraft.sign.HelmSign;
import io.github.pulverizer.movecraft.sign.MoveSign;
import io.github.pulverizer.movecraft.sign.PilotSign;
import io.github.pulverizer.movecraft.sign.RelativeMoveSign;
import io.github.pulverizer.movecraft.sign.ReleaseSign;
import io.github.pulverizer.movecraft.sign.RemoteSign;
import io.github.pulverizer.movecraft.sign.SpeedSign;
import io.github.pulverizer.movecraft.sign.StatusSign;
import io.github.pulverizer.movecraft.sign.SubcraftRotateSign;
import io.github.pulverizer.movecraft.sign.TeleportSign;
import org.spongepowered.api.scheduler.Task;

import java.io.File;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Plugin(id = "movecraft", name = "Movecraft for Sponge", description = "Movecraft for Sponge", version = "0.0.0")
public class Movecraft {

    private static Movecraft instance;
    private boolean shuttingDown;
    private WorldHandler worldHandler;
    private AsyncManager asyncManager;

    private ConfigurationLoader<ConfigurationNode> mainConfigLoader;
    private ConfigurationNode mainConfigNode;

    @Inject
    private Logger logger;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    public Path getConfigDir() {
        return configDir;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public static synchronized Movecraft getInstance() {
        return instance;
    }

    public ConfigurationNode getMainConfigNode() {
        return mainConfigNode;
    }

    public ConfigurationLoader<ConfigurationNode> createConfigLoader(Path file) {
        ConfigurationLoader<ConfigurationNode> loader = YAMLConfigurationLoader.builder().setPath(file).build();
            return loader;
    }

    @Listener
    public void onDisable(GameStoppingEvent event) {
        shuttingDown = true;
    }

    @Listener
    public void onLoad(GamePreInitializationEvent event) {

        instance = this;
        logger = getLogger();

        Path mainConfigPath = getConfigDir().resolve("movecraft.cfg");
        mainConfigLoader = createConfigLoader(mainConfigPath);

        try {
            mainConfigNode = mainConfigLoader.load();
        } catch (IOException error) {
            error.printStackTrace();
        }

        // Read in config

        Settings.LOCALE = mainConfigNode.getNode("Locale").getString("en");
        Settings.Debug = mainConfigNode.getNode("Debug").getBoolean(false);
        Settings.DisableSpillProtection = mainConfigNode.getNode("DisableSpillProtection").getBoolean(false);

        ItemType pilotStick = ItemTypes.AIR;

        try {
            // if the PilotTool is specified in the config.yml file, use it
            if (mainConfigNode.getNode("PilotTool").getValue(TypeToken.of(ItemType.class)) != null) {
                logger.info("Recognized PilotTool setting of: " + mainConfigNode.getNode("PilotTool").getValue(TypeToken.of(ItemType.class)));
                pilotStick = mainConfigNode.getNode("PilotTool").getValue(TypeToken.of(ItemType.class));
            }
        } catch (ObjectMappingException e) {
            e.printStackTrace();
        }

        if (pilotStick == ItemTypes.AIR) {
            logger.info("No PilotTool setting, using default of minecraft:stick");
            Settings.PilotTool = ItemTypes.STICK;
        } else {
            Settings.PilotTool = pilotStick;
        }

        worldHandler = new WorldHandler();


        Settings.SinkCheckTicks = mainConfigNode.getNode("SinkCheckTicks").getDouble(100.0);
        Settings.TracerRateTicks = mainConfigNode.getNode("TracerRateTicks").getDouble(5.0);
        Settings.ManOverBoardTimeout = mainConfigNode.getNode("ManOverBoardTimeout").getInt(30);
        Settings.SilhouetteViewDistance = mainConfigNode.getNode("SilhouetteViewDistance").getInt(200);
        Settings.SilhouetteBlockCount = mainConfigNode.getNode("SilhouetteBlockCount").getInt(20);
        Settings.FireballLifespan = mainConfigNode.getNode("FireballLifespan").getInt(6);
        Settings.FireballPenetration = mainConfigNode.getNode("FireballPenetration").getBoolean(true);
        Settings.ProtectPilotedCrafts = mainConfigNode.getNode("ProtectPilotedCrafts").getBoolean(true);
        Settings.AllowCrewSigns = mainConfigNode.getNode("AllowCrewSigns").getBoolean(true);
        Settings.SetHomeToCrewSign = mainConfigNode.getNode("SetHomeToCrewSign").getBoolean(true);
        Settings.RequireCreatePerm = mainConfigNode.getNode("RequireCreatePerm").getBoolean(false);
        Settings.TNTContactExplosives = mainConfigNode.getNode("TNTContactExplosives").getBoolean(true);
        Settings.FadeWrecksAfter = mainConfigNode.getNode("FadeWrecksAfter").getInt(0);
        Settings.DurabilityOverride = new HashMap<>();

        try {
            Map<BlockType, Integer> tempMap = mainConfigNode.getNode("DurabilityOverride").getValue(new TypeToken<Map<BlockType, Integer>>() {});
            if (tempMap != null)
                for (Object blockType : tempMap.keySet().toArray())
                    Settings.DurabilityOverride.put((BlockType) blockType, tempMap.get(blockType));

        } catch (ObjectMappingException e) {
            e.printStackTrace();
        }

        try {
            Settings.DisableShadowBlocks = new HashSet<BlockType>(mainConfigNode.getNode("DisableShadowBlocks").getList(TypeToken.of(BlockType.class)));  //REMOVE FOR PUBLIC VERSION
        } catch (ObjectMappingException e) {
            e.printStackTrace();

            Settings.DisableShadowBlocks = new HashSet<>();
        }


        /* TODO: Re-enable this?
        if (!Settings.CompatibilityMode) {
            for (BlockType typ : Settings.DisableShadowBlocks) {
                worldHandler.disableShadow(typ);
            }
        }
        */

        try {
            mainConfigLoader.save(mainConfigNode);
        } catch (IOException error) {
            error.printStackTrace();
        }
/*
        String[] localisations = {"en", "cz", "nl"};
        for (String s : localisations) {
            if (!new File(getConfigDir()
                    + "/localisation/movecraftlang_" + s + ".properties").exists()) {
                this.saveResource("localisation/movecraftlang_" + s + ".properties", false);
            }
        }
*/
        I18nSupport.init();
        if (shuttingDown && Settings.IGNORE_RESET) {
            logger.error(I18nSupport.getInternationalisedString("Startup - Error - Reload error"));
            logger.info(I18nSupport.getInternationalisedString("Startup - Error - Disable warning for reload"));
        } else {

            //TODO: Re-add this good stuff!
            /*
            this.getCommand("movecraft").setExecutor(new MovecraftCommand());
            this.getCommand("release").setExecutor(new ReleaseCommand());
            this.getCommand("pilot").setExecutor(new PilotCommand());
            this.getCommand("rotate").setExecutor(new RotateCommand());
            this.getCommand("cruise").setExecutor(new CruiseCommand());
            this.getCommand("craftreport").setExecutor(new CraftReportCommand());
            this.getCommand("manoverboard").setExecutor(new ManOverboardCommand());
            this.getCommand("contacts").setExecutor(new ContactsCommand());
            this.getCommand("scuttle").setExecutor(new ScuttleCommand());
            */

            Sponge.getEventManager().registerListeners(this, new InteractListener());
            Sponge.getEventManager().registerListeners(this, new BlockListener());
            Sponge.getEventManager().registerListeners(this, new PlayerListener());
            Sponge.getEventManager().registerListeners(this, new AntiAircraftDirectorSign());
            Sponge.getEventManager().registerListeners(this, new AscendSign());
            Sponge.getEventManager().registerListeners(this, new CannonDirectorSign());
            Sponge.getEventManager().registerListeners(this, new ContactsSign());
            Sponge.getEventManager().registerListeners(this, new CraftSign());
            Sponge.getEventManager().registerListeners(this, new CrewSign());
            Sponge.getEventManager().registerListeners(this, new CruiseSign());
            Sponge.getEventManager().registerListeners(this, new DescendSign());
            Sponge.getEventManager().registerListeners(this, new HelmSign());
            Sponge.getEventManager().registerListeners(this, new MoveSign());
            Sponge.getEventManager().registerListeners(this, new PilotSign());
            Sponge.getEventManager().registerListeners(this, new RelativeMoveSign());
            Sponge.getEventManager().registerListeners(this, new ReleaseSign());
            Sponge.getEventManager().registerListeners(this, new RemoteSign());
            Sponge.getEventManager().registerListeners(this, new SpeedSign());
            Sponge.getEventManager().registerListeners(this, new StatusSign());
            Sponge.getEventManager().registerListeners(this, new SubcraftRotateSign());
            Sponge.getEventManager().registerListeners(this, new TeleportSign());

            logger.info(String.format(I18nSupport.getInternationalisedString("Startup - Enabled message"), "0.0.0"));
        }
    }

    @Listener
    public void initializeManagers(GameStartedServerEvent event) {
/*
        if (shuttingDown && Settings.IGNORE_RESET) {
            logger.error(I18nSupport.getInternationalisedString("Startup - Error - Reload error"));
            logger.info(I18nSupport.getInternationalisedString("Startup - Error - Disable warning for reload"));
        } else {*/

            // Startup procedure
            asyncManager = new AsyncManager();
            Task.builder()
                    .execute(asyncManager)
                    .intervalTicks(1)
                    .submit(this);
            //asyncManager.runTaskTimer(this, 0, 1);
            Task.builder()
                    .execute(MapUpdateManager::getInstance)
                    .intervalTicks(1)
                    .submit(this);
            //MapUpdateManager.getInstance().runTaskTimer(this, 0, 1);

            CraftManager.initialize();
            CraftManager.getInstance().initCraftTypes();
        //}
    }

    public WorldHandler getWorldHandler(){
        return worldHandler;
    }

    public AsyncManager getAsyncManager(){
        return asyncManager;
    }

}
