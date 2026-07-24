package cn.net.rms.syncmatica_r.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class BreakingChangeNoticeTest {
    @Test
    void versionProducesReusableNoticeIdentity() {
        assertEquals(
                "syncmatica_r-0.4.0-breaking-changes",
                BreakingChangeNotice.noticeIdForVersion("0.4.0")
        );
        assertNotEquals(
                BreakingChangeNotice.noticeIdForVersion("0.4.0"),
                BreakingChangeNotice.noticeIdForVersion("0.5.0")
        );
    }

    @Test
    void versionProducesSeparateEnglishAndChineseDocumentationTargets() {
        final String englishUrl = BreakingChangeNotice.documentationUrlForVersion("0.5.0", false);
        final String chineseUrl = BreakingChangeNotice.documentationUrlForVersion("0.5.0", true);

        assertTrue(englishUrl.endsWith("BREAKING_CHANGES_0.5.0.md"));
        assertTrue(chineseUrl.endsWith("BREAKING_CHANGES_0.5.0_CN.md"));
        assertNotEquals(englishUrl, chineseUrl);
    }

    @Test
    void documentationTargetSanitizesPreReleaseVersionForFileName() {
        assertTrue(
                BreakingChangeNotice.documentationUrlForVersion("0.5.0-rc.1+build/7", false)
                        .endsWith("BREAKING_CHANGES_0.5.0-rc.1_build_7.md")
        );
    }

    @Test
    void pinnedBreakingChangeVersionHasCommittedDocumentation() {
        final Path repositoryRoot = findRepositoryRoot();
        final String version = BreakingChangeNotice.BREAKING_CHANGE_VERSION;
        final Path englishDocumentation =
                repositoryRoot.resolve("docs").resolve("BREAKING_CHANGES_" + version + ".md");
        final Path chineseDocumentation =
                repositoryRoot.resolve("docs").resolve("BREAKING_CHANGES_" + version + "_CN.md");

        assertTrue(
                Files.isRegularFile(englishDocumentation),
                "Missing " + englishDocumentation + "; the in-game notice would link to a 404"
        );
        assertTrue(
                Files.isRegularFile(chineseDocumentation),
                "Missing " + chineseDocumentation + "; the in-game notice would link to a 404"
        );
    }

    private static Path findRepositoryRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        assertTrue(current != null, "Could not locate repository root from " + Paths.get("").toAbsolutePath());
        return current;
    }
}
