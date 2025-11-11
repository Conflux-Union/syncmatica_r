package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.Syncmatica;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public final class HudPreferences {

    private static final double MIN_SCALE = 0.6d;
    private static final double MAX_SCALE = 1.4d;
    private static final double DEFAULT_SCALE = 1.0d;
    private static final String FIELD_SCALE = "hud_scale";
    private static final File CONFIG_FILE = resolveConfigFile();

    private static double hudScale = DEFAULT_SCALE;

    private HudPreferences() {
    }

    public static void load() {
        hudScale = DEFAULT_SCALE;
        if (!CONFIG_FILE.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            final JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root != null && root.has(FIELD_SCALE)) {
                setHudScaleInternal(root.get(FIELD_SCALE).getAsDouble(), false);
            }
        } catch (final Exception ignored) {
            hudScale = DEFAULT_SCALE;
        }
    }

    public static void save() {
        try {
            final File folder = CONFIG_FILE.getParentFile();
            if (folder != null) {
                folder.mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
                final JsonObject root = new JsonObject();
                root.addProperty(FIELD_SCALE, hudScale);
                new Gson().toJson(root, writer);
            }
        } catch (final IOException ignored) {
        }
    }

    public static void setHudScale(final double scale) {
        setHudScaleInternal(scale, true);
    }

    private static void setHudScaleInternal(final double scale, final boolean persist) {
        hudScale = clampScale(scale);
        if (persist) {
            save();
        }
    }

    public static double clampScale(final double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static double getHudScale() {
        return hudScale;
    }

    public static double getMinScale() {
        return MIN_SCALE;
    }

    public static double getMaxScale() {
        return MAX_SCALE;
    }

    public static double getRelativeScale() {
        return (hudScale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
    }

    public static void setRelativeScale(final double relative) {
        final double clamped = Math.max(0d, Math.min(1d, relative));
        setHudScale(MIN_SCALE + clamped * (MAX_SCALE - MIN_SCALE));
    }

    private static File resolveConfigFile() {
        final File configRoot = new File("config");
        final File preferredFolder = new File(configRoot, Syncmatica.MOD_ID);
        final File legacyFolder = new File(configRoot, Syncmatica.LEGACY_MOD_ID);
        if (!preferredFolder.exists() && legacyFolder.exists()) {
            return new File(legacyFolder, "hud_settings.json");
        }
        return new File(preferredFolder, "hud_settings.json");
    }
}
