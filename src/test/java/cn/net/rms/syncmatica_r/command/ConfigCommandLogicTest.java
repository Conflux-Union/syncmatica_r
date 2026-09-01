package cn.net.rms.syncmatica_r.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.rms.syncmatica_r.service.ConfigOption;
import cn.net.rms.syncmatica_r.service.ConfigRegistry;
import cn.net.rms.syncmatica_r.service.ConfigStore;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigCommandLogicTest {
    @TempDir
    Path tempDir;

    @Test
    void listsCurrentAndDefaultValuesInStableOrder() {
        final Fixture fixture = new Fixture(tempDir);

        assertEquals(
                Arrays.asList(
                        "build.enabled = true (default: true)",
                        "build.scan_interval = 1200 (default: 1200)",
                        "quota.limit = 40000000 (default: 40000000)"
                ),
                fixture.logic.list(null)
        );
        assertEquals(
                Arrays.asList(
                        "build.enabled = true (default: true)",
                        "build.scan_interval = 1200 (default: 1200)"
                ),
                fixture.logic.list("build")
        );
    }

    @Test
    void getsSetsAndResetsOneOption() throws Exception {
        final Fixture fixture = new Fixture(tempDir);

        assertEquals(
                "build.enabled = true (default: true)",
                fixture.logic.get("build", "enabled")
        );
        assertEquals(
                "build.enabled = false",
                fixture.logic.set("build", "enabled", "false")
        );
        assertEquals(false, fixture.enabled.get());
        assertEquals(
                "build.enabled = true (default)",
                fixture.logic.reset("build", "enabled")
        );
        assertEquals(true, fixture.enabled.get());
    }

    private static final class Fixture {
        private final AtomicBoolean enabled = new AtomicBoolean(true);
        private final AtomicInteger scanInterval = new AtomicInteger(1200);
        private final AtomicInteger quotaLimit = new AtomicInteger(40_000_000);
        private final ConfigCommandLogic logic;

        private Fixture(final Path tempDir) {
            final ConfigRegistry registry = new ConfigRegistry();
            registry.add(ConfigOption.bool(
                    "build", "enabled", true, enabled::get, enabled::set));
            registry.add(ConfigOption.integer(
                    "build", "scan_interval", 1200, 100, Integer.MAX_VALUE,
                    scanInterval::get, scanInterval::set));
            registry.add(ConfigOption.integer(
                    "quota", "limit", 40_000_000, 0, Integer.MAX_VALUE,
                    quotaLimit::get, quotaLimit::set));
            final ConfigStore store =
                    new ConfigStore(tempDir.resolve("config.json"), new JsonObject(), registry);
            logic = new ConfigCommandLogic(registry, store);
        }
    }
}
