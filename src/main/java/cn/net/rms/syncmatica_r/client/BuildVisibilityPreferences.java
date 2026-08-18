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
 * Whether this player wants Litematica's sub-region visibility to follow the
 * regions they claimed.
 *
 * <p>Off by default: turning it on lets the mod write to a Litematica setting
 * the player also edits by hand, and that is a trade only the player can decide
 * to make. Like the foreign-build warning, the choice is the player's rather
 * than the server operator's, so it lives on the client.
 */
public final class BuildVisibilityPreferences {

    private static final Logger LOGGER = LogManager.getLogger(BuildVisibilityPreferences.class);
    private static final String FIELD_FOLLOW_CLAIMS = "follow_claims";
    private static final boolean DEFAULT_FOLLOW_CLAIMS = false;
    private static final File CONFIG_FILE =
            new File(new File("config", Syncmatica.MOD_ID), "build_visibility_settings.json");

    private static boolean followClaims = DEFAULT_FOLLOW_CLAIMS;

    private BuildVisibilityPreferences() {
    }

    public static void load() {
        followClaims = DEFAULT_FOLLOW_CLAIMS;
        if (!CONFIG_FILE.isFile()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            final JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root != null && root.has(FIELD_FOLLOW_CLAIMS)) {
                followClaims = root.get(FIELD_FOLLOW_CLAIMS).getAsBoolean();
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
        root.addProperty(FIELD_FOLLOW_CLAIMS, followClaims);
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (final IOException exception) {
            LOGGER.warn("Failed to write {}", CONFIG_FILE, exception);
        }
    }

    public static boolean isFollowClaimsEnabled() {
        return followClaims;
    }

    public static void setFollowClaimsEnabled(final boolean value) {
        if (followClaims == value) {
            return;
        }
        followClaims = value;
        save();
    }
}
