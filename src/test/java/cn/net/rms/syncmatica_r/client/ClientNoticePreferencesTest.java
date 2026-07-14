package cn.net.rms.syncmatica_r.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientNoticePreferencesTest {
    @TempDir
    Path tempDir;

    @Test
    void dismissedNoticesAccumulateWithoutHidingFutureNotices() throws IOException {
        final File configFile = tempDir.resolve("client_notices.json").toFile();
        final ClientNoticePreferences preferences = new ClientNoticePreferences(configFile);
        preferences.load();

        assertTrue(preferences.dismiss("syncmatica_r-0.4.0-breaking-changes"));
        assertTrue(preferences.dismiss("syncmatica_r-0.5.0-breaking-changes"));

        final ClientNoticePreferences reloaded = new ClientNoticePreferences(configFile);
        reloaded.load();

        assertTrue(reloaded.isDismissed("syncmatica_r-0.4.0-breaking-changes"));
        assertTrue(reloaded.isDismissed("syncmatica_r-0.5.0-breaking-changes"));
        assertFalse(reloaded.isDismissed("syncmatica_r-0.6.0-breaking-changes"));

        final JsonObject saved = new Gson().fromJson(Files.readString(configFile.toPath()), JsonObject.class);
        final JsonArray dismissed = saved.getAsJsonArray("dismissed_breaking_change_notices");
        assertEquals(2, dismissed.size());
        assertEquals("syncmatica_r-0.4.0-breaking-changes", dismissed.get(0).getAsString());
        assertEquals("syncmatica_r-0.5.0-breaking-changes", dismissed.get(1).getAsString());
    }

    @Test
    void failedWriteDoesNotDismissNoticeInMemory() throws IOException {
        final Path blockedFolder = tempDir.resolve("blocked");
        Files.writeString(blockedFolder, "not a directory", StandardCharsets.UTF_8);
        final ClientNoticePreferences preferences = new ClientNoticePreferences(
                blockedFolder.resolve("client_notices.json").toFile()
        );

        assertFalse(preferences.dismiss("syncmatica_r-0.4.0-breaking-changes"));
        assertFalse(preferences.isDismissed("syncmatica_r-0.4.0-breaking-changes"));
    }
}
