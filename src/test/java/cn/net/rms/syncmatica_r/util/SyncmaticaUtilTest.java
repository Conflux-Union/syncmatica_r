package cn.net.rms.syncmatica_r.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SyncmaticaUtilTest {
    @TempDir
    Path tempDir;

    @Test
    void preservesSupplementaryUnicodeWithoutSplittingSurrogates() {
        assertEquals("build-😀-a", SyncmaticaUtil.sanitizeFileName("build-😀-a"));
    }

    @Test
    void returnsSafeBoundedPathComponent() {
        final String sanitized = SyncmaticaUtil.sanitizeFileName("../" + "界".repeat(200) + ". ");

        assertTrue(sanitized.getBytes(StandardCharsets.UTF_8).length <= 200);
        assertTrue(!sanitized.endsWith(".") && !sanitized.endsWith(" "));
    }

    @Test
    void backupAndReplaceReportsSuccessOnlyAfterMovingIncomingFile() throws IOException {
        final Path backup = tempDir.resolve("placement.bak");
        final Path current = tempDir.resolve("placement.json");
        final Path incoming = tempDir.resolve("placement.new");
        Files.writeString(current, "old", StandardCharsets.UTF_8);

        assertFalse(SyncmaticaUtil.backupAndReplace(backup, current, incoming));
        assertEquals("old", Files.readString(current, StandardCharsets.UTF_8));

        Files.writeString(incoming, "new", StandardCharsets.UTF_8);

        assertTrue(SyncmaticaUtil.backupAndReplace(backup, current, incoming));
        assertEquals("new", Files.readString(current, StandardCharsets.UTF_8));
        assertEquals("old", Files.readString(backup, StandardCharsets.UTF_8));
    }
}
