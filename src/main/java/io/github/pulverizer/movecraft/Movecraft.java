package io.github.pulverizer.movecraft;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.config.ConfigDir;
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

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;

@Plugin(id = "movecraft", name = "Movecraft for Sponge", description = "Movecraft for Sponge", version = "0.0.0")
public class Movecraft {

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

    private static Movecraft instance;
    private boolean shuttingDown;
    private WorldHandler worldHandler;


    private AsyncManager asyncManager;

    public static synchronized Movecraft getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
    }

    @Override
    public void onEnable() {
        // Read in config
        this.saveDefaultConfig();

        Settings.LOCALE = getConfig().getString("Locale");
        Settings.Debug = getConfig().getBoolean("Debug", false);
        Settings.DisableSpillProtection = getConfig().getBoolean("DisableSpillProtection", false);
        // if the PilotTool is specified in the config.yml file, use it
        if (getConfig().getInt("PilotTool") != 0) {
            logger.info("Recognized PilotTool setting of: "
                    + getConfig().getInt("PilotTool"));
            Settings.PilotTool = getConfig().getInt("PilotTool");
        } else {
            logger.info("No PilotTool setting, using default of minecraft:stick");
        }

        //Switch to interfaces
        String packageName = this.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        try {
            final Class<?> clazz = Class.forName("net.countercraft.movecraft.compat." + version + ".IWorldHandler");
            // Check if we have a NMSHandler class at that location.
            if (WorldHandler.class.isAssignableFrom(clazz)) { // Make sure it actually implements NMS
                this.worldHandler = (WorldHandler) clazz.getConstructor().newInstance(); // Set our handler
            }
        } catch (final Exception e) {
            e.printStackTrace();
            this.getLogger().error("Could not find support for this version.");
            this.setEnabled(false);
            return;
        }
        this.getLogger().info("Loading support for " + version);


        Settings.SinkCheckTicks = getConfig().getDouble("SinkCheckTicks", 100.0);
        Settings.TracerRateTicks = getConfig().getDouble("TracerRateTicks", 5.0);
        Settings.ManOverBoardTimeout = getConfig().getInt("ManOverBoardTimeout", 30);
        Settings.SilhouetteViewDistance = getConfig().getInt("SilhouetteViewDistance", 200);
        Settings.SilhouetteBlockCount = getConfig().getInt("SilhouetteBlockCount", 20);
        Settings.FireballLifespan = getConfig().getInt("FireballLifespan", 6);
        Settings.FireballPenetration = getConfig().getBoolean("FireballPenetration", true);
        Settings.ProtectPilotedCrafts = getConfig().getBoolean("ProtectPilotedCrafts", false);
        Settings.AllowCrewSigns = getConfig().getBoolean("AllowCrewSigns", true);
        Settings.SetHomeToCrewSign = getConfig().getBoolean("SetHomeToCrewSign", true);
        Settings.RequireCreatePerm = getConfig().getBoolean("RequireCreatePerm", false);
        Settings.TNTContactExplosives = getConfig().getBoolean("TNTContactExplosives", true);
        Settings.FadeWrecksAfter = getConfig().getInt("FadeWrecksAfter", 0);
        if (getConfig().contains("DurabilityOverride")) {
            Map<String, Object> temp = getConfig().getConfigurationSection("DurabilityOverride").getValues(false);
            Settings.DurabilityOverride = new HashMap<>();
            for (String str : temp.keySet()) {
                Settings.DurabilityOverride.put(Integer.parseInt(str), (Integer) temp.get(str));
            }

        }
        Settings.DisableShadowBlocks = new HashSet<BlockType>(getConfig().getIntegerList("DisableShadowBlocks"));  //REMOVE FOR PUBLIC VERSION




        if (!Settings.CompatibilityMode) {
            for (BlockType typ : Settings.DisableShadowBlocks) {
                worldHandler.disableShadow(typ);
            }
        }

        String[] localisations = {"en", "cz", "nl"};
        for (String s : localisations) {
            if (!new File(getDataFolder()
                    + "/localisation/movecraftlang_" + s + ".properties").exists()) {
                this.saveResource("localisation/movecraftlang_" + s + ".properties", false);
            }
        }

        I18nSupport.init();
        if (shuttingDown && Settings.IGNORE_RESET) {
            logger.error(I18nSupport.getInternationalisedString("Startup - Error - Reload error"));
            logger.info(I18nSupport.getInternationalisedString("Startup - Error - Disable warning for reload"));
            getPluginLoader().disablePlugin(this);
        } else {

            // Startup procedure
            asyncManager = new AsyncManager();
            asyncManager.runTaskTimer(this, 0, 1);
            MapUpdateManager.getInstance().runTaskTimer(this, 0, 1);

            CraftManager.initialize();

            getServer().getPluginManager().registerEvents(new InteractListener(), this);
            }
            this.getCommand("movecraft").setExecutor(new MovecraftCommand());
            this.getCommand("release").setExecutor(new ReleaseCommand());
            this.getCommand("pilot").setExecutor(new PilotCommand());
            this.getCommand("rotate").setExecutor(new RotateCommand());
            this.getCommand("cruise").setExecutor(new CruiseCommand());
            this.getCommand("craftreport").setExecutor(new CraftReportCommand());
            this.getCommand("manoverboard").setExecutor(new ManOverboardCommand());
            this.getCommand("contacts").setExecutor(new ContactsCommand());
            this.getCommand("scuttle").setExecutor(new ScuttleCommand());

            getServer().getPluginManager().registerEvents(new BlockListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(), this);
            getServer().getPluginManager().registerEvents(new AntiAircraftDirectorSign(), this);
            getServer().getPluginManager().registerEvents(new AscendSign(), this);
            getServer().getPluginManager().registerEvents(new CannonDirectorSign(), this);
            getServer().getPluginManager().registerEvents(new ContactsSign(), this);
            getServer().getPluginManager().registerEvents(new CraftSign(), this);
            getServer().getPluginManager().registerEvents(new CrewSign(), this);
            getServer().getPluginManager().registerEvents(new CruiseSign(), this);
            getServer().getPluginManager().registerEvents(new DescendSign(), this);
            getServer().getPluginManager().registerEvents(new HelmSign(), this);
            getServer().getPluginManager().registerEvents(new MoveSign(), this);
            getServer().getPluginManager().registerEvents(new PilotSign(), this);
            getServer().getPluginManager().registerEvents(new RelativeMoveSign(), this);
            getServer().getPluginManager().registerEvents(new ReleaseSign(), this);
            getServer().getPluginManager().registerEvents(new RemoteSign(), this);
            //getServer().getPluginManager().registerEvents(new RepairSign(), this);
            getServer().getPluginManager().registerEvents(new SpeedSign(), this);
            getServer().getPluginManager().registerEvents(new StatusSign(), this);
            getServer().getPluginManager().registerEvents(new SubcraftRotateSign(), this);
            getServer().getPluginManager().registerEvents(new TeleportSign(), this);

            logger.info(String.format(I18nSupport.getInternationalisedString("Startup - Enabled message"), getDescription().getVersion()));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        instance = this;
        logger = getLogger();
    }

    public WorldHandler getWorldHandler(){
        return worldHandler;
    }

    public AsyncManager getAsyncManager(){return asyncManager;}

}
