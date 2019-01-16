package io.github.pulverizer.movecraft.localisation;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class I18nSupport {
    private static Properties languageFile;

    public static void init() {
        languageFile = new Properties();

        File localisationDirectory = new File(Movecraft.getInstance().getDataFolder().getAbsolutePath() + "/localisation");

        if (!localisationDirectory.exists()) {
            localisationDirectory.mkdirs();
        }

        InputStream is = null;
        try {
            is = new FileInputStream(localisationDirectory.getAbsolutePath() + "/movecraftlang" + "_" + Settings.LOCALE + ".properties");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (is == null) {
            Movecraft.getInstance().getLogger().error("Critical Error in Localisation System");
            Sponge.getServer().shutdown(Text.of("Critical Error in Movecraft Localisation System"));
            return;
        }

        try {
            languageFile.load(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static String getInternationalisedString(String key) {
        String ret = languageFile.getProperty(key);
        if (ret != null) {
            return ret;
        } else {
            return key;
        }
    }

}