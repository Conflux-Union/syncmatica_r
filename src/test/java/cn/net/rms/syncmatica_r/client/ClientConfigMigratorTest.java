package cn.net.rms.syncmatica_r.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientConfigMigratorTest {

    @TempDir
    Path configDirectory;

    @Test
    void combinesLegacyClientSettingsIntoMalilibCategories() throws IOException {
        write("hud_settings.json", """
                {"hud_enabled":false,"hud_scale":1.25}
                """);
        write("build_visibility_settings.json", """
                {"follow_claims":true}
                """);
        write("build_warning_settings.json", """
                {"warn_on_foreign_placement":false}
                """);
        write("syncmatica_r_hotkeys.json", """
                {"Hotkeys":{"openMaterialCollections":"M,C","openBuildManagement":"B"}}
                """);

        final JsonObject migrated = ClientConfigMigrator.readLegacy(configDirectory);
        final JsonObject general = migrated.getAsJsonObject("General");
        final JsonObject hotkeys = migrated.getAsJsonObject("Hotkeys");

        assertFalse(general.get("hudEnabled").getAsBoolean());
        assertEquals(1.25d, general.get("hudScale").getAsDouble());
        assertTrue(general.get("followClaims").getAsBoolean());
        assertFalse(general.get("warnOnForeignPlacement").getAsBoolean());
        assertEquals("M,C", hotkeys.get("openMaterialCollections").getAsString());
        assertEquals("B", hotkeys.get("openBuildManagement").getAsString());
    }

    @Test
    void fallsBackToTheOldModIdForHudSettings() throws IOException {
        final Path legacyDirectory = Files.createDirectory(configDirectory.resolve("legacy"));
        Files.writeString(legacyDirectory.resolve("hud_settings.json"),
                "{\"hud_enabled\":false,\"hud_scale\":0.75}");

        final JsonObject general = ClientConfigMigrator
                .readLegacy(configDirectory, legacyDirectory)
                .getAsJsonObject("General");

        assertFalse(general.get("hudEnabled").getAsBoolean());
        assertEquals(0.75d, general.get("hudScale").getAsDouble());
    }

    private void write(final String fileName, final String json) throws IOException {
        Files.writeString(configDirectory.resolve(fileName), json);
    }
}
