package io.github.pulverizer.movecraft;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import io.github.pulverizer.movecraft.listener.*;
import io.github.pulverizer.movecraft.sign.*;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
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
import io.github.pulverizer.movecraft.mapUpdater.MapUpdateManager;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.sql.SqlService;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Plugin(
        id = "movecraft",
        name = "Movecraft for Sponge",
        description = "Allows players to create moving things out of blocks. Airships, Turrets, Submarines, Etc.",
        version = "0.3.0",
        url = "https://github.com/Pulverizer/Movecraft-for-Sponge",
        authors = {"BernardisGood", "https://github.com/Pulverizer/Movecraft-for-Sponge/graphs/contributors"})

public class Movecraft {

    private static Movecraft instance;
    private WorldHandler worldHandler;

    private ConfigurationLoader<ConfigurationNode> mainConfigLoader;
    private ConfigurationNode mainConfigNode;

    private SqlService sql;
    private final String databaseSettings = "jdbc:h2:";
    private final String databaseName = "/movecraft.db";

    @Inject
    private Logger logger;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    /**
     * Fetches the PATH of the config directory.
     * @return The PATH of the config directory.
     */
    public Path getConfigDir() {
        return configDir;
    }

    /**
     * Fetches the Logger for this Plugin.
     * @return This Plugin's logger.
     */
    public Logger getLogger() {
        return this.logger;
    }

    /**
     * Fetches this Plugin's instance.
     * @return The instance of this Plugin.
     */
    public static synchronized Movecraft getInstance() {
        return instance;
    }

    public ConfigurationNode getMainConfigNode() {
        return mainConfigNode;
    }

    public ConfigurationLoader<ConfigurationNode> createConfigLoader(Path file) {
        return YAMLConfigurationLoader.builder().setPath(file).setDefaultOptions(ConfigurationOptions.defaults().setShouldCopyDefaults(true)).build();
    }

    public Connection connectToSQL() {
        if (sql == null) {
            sql = Sponge.getServiceManager().provide(SqlService.class).get();
        }

        try {
            return sql.getDataSource(databaseSettings + configDir.toString() + databaseName).getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Listener for GamePreInitializationEvent. Loads the Plugin's settings.
     * @param event GamePreInitializationEvent from Listener.
     */
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
            // if the PilotTool is specified in the movecraft.cfg file, use it
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
        Settings.EnableCrewSigns = mainConfigNode.getNode("AllowCrewSigns").getBoolean(true);
        Settings.SetHomeToCrewSign = mainConfigNode.getNode("SetHomeToCrewSign").getBoolean(true);
        Settings.RequireCreateSignPerm = mainConfigNode.getNode("RequireCreatePerm").getBoolean(false);
        Settings.TNTContactExplosives = mainConfigNode.getNode("TNTContactExplosives").getBoolean(true);
        Settings.FadeWrecksAfter = mainConfigNode.getNode("FadeWrecksAfter").getInt(0);
        Settings.ReleaseOnCrewDeath = mainConfigNode.getNode("ReleaseOnCrewDeath").getBoolean(true);
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
            Settings.DisableShadowBlocks = new HashSet<>(mainConfigNode.getNode("DisableShadowBlocks").getList(TypeToken.of(BlockType.class)));  //REMOVE FOR PUBLIC VERSION
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

        //TODO: Re-add commands!

        /*this.getCommand("movecraft").setExecutor(new MovecraftCommand());
        this.getCommand("release").setExecutor(new ReleaseCommand());
        this.getCommand("pilot").setExecutor(new PilotCommand());
        this.getCommand("add").setExecutor(new RotateCommand());
        this.getCommand("cruise").setExecutor(new CruiseCommand());
        this.getCommand("craftreport").setExecutor(new CraftReportCommand());
        this.getCommand("manoverboard").setExecutor(new ManOverboardCommand());
        this.getCommand("contacts").setExecutor(new ContactsCommand());
        this.getCommand("scuttle").setExecutor(new ScuttleCommand());*/


        Sponge.getEventManager().registerListeners(this, new InteractListener());
        Sponge.getEventManager().registerListeners(this, new BlockListener());
        Sponge.getEventManager().registerListeners(this, new PlayerListener());
        Sponge.getEventManager().registerListeners(this, new SignListener());
        Sponge.getEventManager().registerListeners(this, new CrewSign());
        Sponge.getEventManager().registerListeners(this, new CommanderSign());
        Sponge.getEventManager().registerListeners(this, new TNTListener());
        Sponge.getEventManager().registerListeners(this, new FireballListener());

        logger.info("Movecraft Enabled.");
    }

    /**
     * <pre>
     * Listener for GameStartedServerEvent. Loads the Plugin's various content managers and databases.
     *
     * CraftManager
     * AsyncManager
     * MapUpdateManager
     * Commander Sign Database
     * Crew Sign Database
     * </pre>
     * @param event GameStartedServerEvent from Listener.
     */
    @Listener
    public void initializeManagers(GameStartedServerEvent event) {

        CraftManager.initialize();
        AsyncManager.initialize();
        MapUpdateManager.initialize();
        CommanderSign.initDatabase();
        if (Settings.EnableCrewSigns) {
            CrewSign.initDatabase();
        }

        // Startup procedure
        AsyncManager asyncManager = AsyncManager.getInstance();
        MapUpdateManager mapUpdateManager = MapUpdateManager.getInstance();
        Task.builder()
                .execute(asyncManager)
                .intervalTicks(1)
                .submit(this);
        Task.builder()
                .execute(mapUpdateManager)
                .intervalTicks(1)
                .submit(this);
    }

    /**
     * Fetches the WorldHandler instance that the Plugin is using.
     * @return WorldHandler instance.
     */
    public WorldHandler getWorldHandler(){
        return worldHandler;
    }

}
