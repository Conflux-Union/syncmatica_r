package cn.net.rms.syncmatica_r.client.hotkey;

import cn.net.rms.syncmatica_r.Syncmatica;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles loading and saving hotkey configuration to JSON file.
 * Config file: config/syncmatica_r/syncmatica_r_hotkeys.json
 */
public final class SyncmaticaHotkeyConfig {

    private static final Logger LOGGER = LogManager.getLogger(SyncmaticaHotkeyConfig.class);
    private static final String CONFIG_DIR = "config";
    private static final String HOTKEYS_SECTION = "Hotkeys";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SyncmaticaHotkeyConfig() {
        // Utility class, no instantiation
    }

    /**
     * Returns the config folder for syncmatica_r.
     */
    private static File getConfigFolder() {
        return new File(CONFIG_DIR, Syncmatica.MOD_ID);
    }

    /**
     * Returns the hotkey config file path.
     */
    private static File getConfigFile() {
        return new File(getConfigFolder(), Syncmatica.MOD_ID + "_hotkeys.json");
    }

    /**
     * Loads hotkey configuration from the JSON file.
     * If the file doesn't exist or is invalid, hotkeys retain their default values.
     */
    public static void load() {
        final File configFile = getConfigFile();
        if (!configFile.exists()) {
            return;
        }

        JsonObject root = null;
        try (final Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        } catch (final Exception e) {
            LOGGER.warn("Failed to read hotkey config file: {}", e.getMessage());
        }

        if (root == null || !root.has(HOTKEYS_SECTION)) {
            return;
        }

        final JsonObject hotkeysSection;
        try {
            hotkeysSection = root.getAsJsonObject(HOTKEYS_SECTION);
        } catch (final Exception e) {
            LOGGER.warn("Invalid hotkeys section in config: {}", e.getMessage());
            return;
        }

        for (final ConfigHotkey hotkey : SyncmaticaHotkeys.getHotkeys()) {
            final String name = hotkey.getName();
            if (!hotkeysSection.has(name)) {
                continue;
            }
            try {
                final JsonElement element = hotkeysSection.get(name);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    continue;
                }
                // Do not clobber values already provided by malilib or user config
                if (!hotkey.getKeybind().getStringValue().isEmpty()) {
                    continue;
                }
                final String keyString = element.getAsString();
                hotkey.getKeybind().setValueFromString(keyString);
            } catch (final Exception e) {
                LOGGER.warn("Failed to load hotkey '{}': {}", name, e.getMessage());
            }
        }
    }

    /**
     * Saves the current hotkey configuration to the JSON file.
     */
    public static void save() {
        final File configFolder = getConfigFolder();
        if (!configFolder.exists() && !configFolder.mkdirs()) {
            LOGGER.warn("Failed to create config folder: {}", configFolder.getAbsolutePath());
            return;
        }

        final JsonObject root = new JsonObject();
        final JsonObject hotkeysSection = new JsonObject();

        for (final ConfigHotkey hotkey : SyncmaticaHotkeys.getHotkeys()) {
            hotkeysSection.addProperty(hotkey.getName(), hotkey.getKeybind().getStringValue());
        }

        root.add(HOTKEYS_SECTION, hotkeysSection);

        final File configFile = getConfigFile();
        try (final Writer writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(root));
        } catch (final IOException e) {
            LOGGER.warn("Failed to save hotkey config: {}", e.getMessage());
        }
    }
}
