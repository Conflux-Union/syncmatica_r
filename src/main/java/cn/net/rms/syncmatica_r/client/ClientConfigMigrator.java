package cn.net.rms.syncmatica_r.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ClientConfigMigrator {

    private ClientConfigMigrator() {
    }

    static JsonObject readLegacy(final Path configDirectory) {
        return readLegacy(configDirectory, configDirectory);
    }

    static JsonObject readLegacy(final Path configDirectory, final Path legacyConfigDirectory) {
        final JsonObject general = new JsonObject();
        final Path preferredHudPath = configDirectory.resolve("hud_settings.json");
        final JsonObject hud = read(Files.isRegularFile(preferredHudPath)
                ? preferredHudPath
                : legacyConfigDirectory.resolve("hud_settings.json"));
        copy(hud, "hud_enabled", general, "hudEnabled");
        copy(hud, "hud_scale", general, "hudScale");
        copy(read(configDirectory.resolve("build_visibility_settings.json")),
                "follow_claims", general, "followClaims");
        copy(read(configDirectory.resolve("build_warning_settings.json")),
                "warn_on_foreign_placement", general, "warnOnForeignPlacement");

        final JsonObject legacyHotkeys = read(configDirectory.resolve("syncmatica_r_hotkeys.json"));
        final JsonObject hotkeys = legacyHotkeys.has("Hotkeys")
                && legacyHotkeys.get("Hotkeys").isJsonObject()
                ? legacyHotkeys.getAsJsonObject("Hotkeys")
                : new JsonObject();

        final JsonObject migrated = new JsonObject();
        migrated.add("General", general);
        migrated.add("Hotkeys", hotkeys);
        return migrated;
    }

    private static JsonObject read(final Path path) {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final JsonObject object = new Gson().fromJson(reader, JsonObject.class);
            return object != null ? object : new JsonObject();
        } catch (final IOException | RuntimeException ignored) {
            return new JsonObject();
        }
    }

    private static void copy(final JsonObject source, final String sourceName,
                             final JsonObject target, final String targetName) {
        final JsonElement value = source.get(sourceName);
        if (value != null) {
            target.add(targetName, value);
        }
    }
}
