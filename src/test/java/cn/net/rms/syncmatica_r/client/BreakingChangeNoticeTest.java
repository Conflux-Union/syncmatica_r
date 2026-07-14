package cn.net.rms.syncmatica_r.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
