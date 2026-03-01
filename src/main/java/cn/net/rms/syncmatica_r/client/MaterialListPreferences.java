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

/**
 * Client-side preferences for material list display.
 * Persisted to config/syncmatica_r/material_list_settings.json
 */
public final class MaterialListPreferences {

    public enum SortMode {
        MISSING_DESC,  // Sort by missing count descending (default)
        NAME_ASC       // Sort by item name ascending
    }

    private static final String FIELD_SORT_MODE = "sort_mode";
    private static final String FIELD_HIDE_FINISHED = "hide_finished";
    private static final File CONFIG_FILE = resolveConfigFile();

    private static SortMode sortMode = SortMode.MISSING_DESC;
    private static boolean hideFinished = false;

    private MaterialListPreferences() {
    }

    public static void load() {
        sortMode = SortMode.MISSING_DESC;
        hideFinished = false;
        if (!CONFIG_FILE.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            final JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root != null) {
                if (root.has(FIELD_SORT_MODE)) {
                    try {
                        sortMode = SortMode.valueOf(root.get(FIELD_SORT_MODE).getAsString());
                    } catch (final IllegalArgumentException ignored) {
                        sortMode = SortMode.MISSING_DESC;
                    }
                }
                if (root.has(FIELD_HIDE_FINISHED)) {
                    hideFinished = root.get(FIELD_HIDE_FINISHED).getAsBoolean();
                }
            }
        } catch (final Exception ignored) {
            sortMode = SortMode.MISSING_DESC;
            hideFinished = false;
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
                root.addProperty(FIELD_SORT_MODE, sortMode.name());
                root.addProperty(FIELD_HIDE_FINISHED, hideFinished);
                new Gson().toJson(root, writer);
            }
        } catch (final IOException ignored) {
        }
    }

    public static SortMode getSortMode() {
        return sortMode;
    }

    public static void setSortMode(final SortMode mode) {
        if (mode != null && mode != sortMode) {
            sortMode = mode;
            save();
        }
    }

    public static void cycleSortMode() {
        final SortMode[] modes = SortMode.values();
        final int nextIndex = (sortMode.ordinal() + 1) % modes.length;
        sortMode = modes[nextIndex];
        save();
    }

    public static boolean isHideFinished() {
        return hideFinished;
    }

    public static void setHideFinished(final boolean hide) {
        if (hide != hideFinished) {
            hideFinished = hide;
            save();
        }
    }

    public static void toggleHideFinished() {
        hideFinished = !hideFinished;
        save();
    }

    private static File resolveConfigFile() {
        final File configRoot = new File("config");
        final File preferredFolder = new File(configRoot, Syncmatica.MOD_ID);
        return new File(preferredFolder, "material_list_settings.json");
    }
}
