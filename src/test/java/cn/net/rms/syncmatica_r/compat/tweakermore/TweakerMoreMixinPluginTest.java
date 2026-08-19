package cn.net.rms.syncmatica_r.compat.tweakermore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TweakerMoreMixinPluginTest {
    @Test
    void acceptsOnlyTheVerifiedTweakerMoreReleaseLine() {
        assertTrue(TweakerMoreMixinPlugin.isSupportedVersion("3.33.0"));
        assertTrue(TweakerMoreMixinPlugin.isSupportedVersion("3.33.1+build.4"));
        assertFalse(TweakerMoreMixinPlugin.isSupportedVersion("3.32.1"));
        assertFalse(TweakerMoreMixinPlugin.isSupportedVersion("3.34.0"));
        assertFalse(TweakerMoreMixinPlugin.isSupportedVersion(null));
    }
}
