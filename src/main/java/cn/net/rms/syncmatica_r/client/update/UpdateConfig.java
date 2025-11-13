package cn.net.rms.syncmatica_r.client.update;

import cn.net.rms.syncmatica_r.Syncmatica;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;

public final class UpdateConfig {

    private static final boolean CHECK_UPDATE_DEFAULT = true;
    private static final boolean CHECK_PRE_RELEASE_DEFAULT = false;

    private static boolean loaded = false;
    private static boolean checkUpdate = CHECK_UPDATE_DEFAULT;
    private static boolean checkPreRelease = CHECK_PRE_RELEASE_DEFAULT;

    private UpdateConfig() {
    }

    public static boolean isCheckUpdateEnabled() {
        ensureLoaded();
        return checkUpdate;
    }

    public static boolean isCheckPreReleaseEnabled() {
        ensureLoaded();
        return checkPreRelease;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static void load() {
        loaded = true;
        try {
            final File root = new File(".", "config");
            final File preferredFolder = new File(root, Syncmatica.MOD_ID);
            final File legacyFolder = new File(root, Syncmatica.LEGACY_MOD_ID);
            final File preferredConfig = new File(preferredFolder, "config.json");
            final File legacyConfig = new File(legacyFolder, "config.json");
            File configFile = preferredConfig;
            if (!preferredConfig.exists() && legacyConfig.exists()) {
                configFile = legacyConfig;
            }
            if (!configFile.exists()) {
                return;
            }
            final Gson gson = new Gson();
            try (Reader reader = new BufferedReader(new FileReader(configFile))) {
                final JsonObject rootJson = gson.fromJson(reader, JsonObject.class);
                if (rootJson == null) {
                    return;
                }
                if (rootJson.has("checkupdate")) {
                    checkUpdate = rootJson.get("checkupdate").getAsBoolean();
                }
                if (rootJson.has("check_pre_release")) {
                    checkPreRelease = rootJson.get("check_pre_release").getAsBoolean();
                }
            }
        } catch (final Exception ignored) {
        }
    }
}
