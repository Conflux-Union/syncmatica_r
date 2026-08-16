package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.Syncmatica;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Whether this player wants to be told about building inside a sub-region
 * somebody else claimed.
 *
 * <p>The check runs entirely on the client, so the choice belongs to the player
 * rather than to the server operator.
 */
public final class BuildWarningPreferences {

    private static final Logger LOGGER = LogManager.getLogger(BuildWarningPreferences.class);
    private static final String FIELD_ENABLED = "warn_on_foreign_placement";
    private static final boolean DEFAULT_ENABLED = true;
    private static final File CONFIG_FILE =
            new File(new File("config", Syncmatica.MOD_ID), "build_warning_settings.json");

    private static boolean enabled = DEFAULT_ENABLED;

    private BuildWarningPreferences() {
    }

    public static void load() {
        enabled = DEFAULT_ENABLED;
        if (!CONFIG_FILE.isFile()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            final JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root != null && root.has(FIELD_ENABLED)) {
                enabled = root.get(FIELD_ENABLED).getAsBoolean();
            }
        } catch (final IOException | RuntimeException exception) {
            LOGGER.warn("Failed to read {}", CONFIG_FILE, exception);
        }
    }

    public static void save() {
        final File folder = CONFIG_FILE.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
            LOGGER.warn("Failed to create {}", folder);
            return;
        }
        final JsonObject root = new JsonObject();
        root.addProperty(FIELD_ENABLED, enabled);
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (final IOException exception) {
            LOGGER.warn("Failed to write {}", CONFIG_FILE, exception);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(final boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        save();
    }
}
