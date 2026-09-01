package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServiceConfigRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void serverRegistryContainsExactlyTheServiceOptionsAndDefaults() {
        final Context context = newContext(true, "server");
        try {
            final Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("quota.enabled", false);
            expected.put("quota.limit", 40_000_000);
            expected.put("materials.enabled", true);
            expected.put("materials.scan_interval", 200);
            expected.put("materials.include_container_contents", false);
            expected.put("materials.allow_owner_stocking_area_management", true);
            expected.put("materials.scan_blocks_per_tick", 2048);
            expected.put("materials.max_schematic_megabytes", 64);
            expected.put("materials.max_schematic_blocks", 8_000_000);
            expected.put("materials.max_stocking_area_blocks", 1_000_000);
            expected.put("build.enabled", true);
            expected.put("build.completion_enabled", true);
            expected.put("build.scan_blocks_per_tick", 4096);
            expected.put("build.scan_interval", 1200);
            expected.put("build.full_rescan_interval", 36_000);
            expected.put("debug.doPackageLogging", false);

            final ConfigRegistry registry = context.getConfigRegistry();
            final Map<String, Object> actual = new LinkedHashMap<>();
            for (final String section : registry.sections()) {
                for (final String key : registry.keys(section)) {
                    final ConfigOption<?> option = registry.find(section, key);
                    actual.put(section + "." + key, option.getDefaultValue());
                }
            }

            assertEquals(expected, actual);
            assertEquals(16, actual.size());
            assertFalse(actual.containsKey("checkupdate"));
            assertFalse(actual.containsKey("check_pre_release"));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void fullRescanIntervalAcceptsZeroOrAtLeastTwelveHundred() {
        final Context context = newContext(true, "server");
        try {
            final ConfigOption<?> option =
                    context.getConfigRegistry().find("build", "full_rescan_interval");

            assertEquals(0, option.parse("0"));
            assertEquals(1200, option.parse("1200"));
            assertEquals(Integer.MAX_VALUE, option.parse(Integer.toString(Integer.MAX_VALUE)));
            assertThrows(IllegalArgumentException.class, () -> option.parse("1"));
            assertThrows(IllegalArgumentException.class, () -> option.parse("1199"));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void clientContextDoesNotExposeServerConfiguration() {
        final Context context = newContext(false, "client");
        try {
            assertNull(context.getConfigRegistry());
            assertNull(context.getConfigStore());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void serverContextRetainsTheCompleteLoadedConfiguration() {
        final Context context = newContext(true, "server");
        try {
            final JsonObject loaded = context.getLoadedConfiguration();

            assertTrue(loaded.has("checkupdate"));
            assertTrue(loaded.has("check_pre_release"));
            assertTrue(loaded.has("quota"));
            assertTrue(loaded.has("materials"));
            assertTrue(loaded.has("build"));
            assertTrue(loaded.has("debug"));
            assertEquals(loaded, context.getConfigStore().snapshot());
        } finally {
            context.shutdown();
        }
    }

    private Context newContext(final boolean server, final String folder) {
        return new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                new SyncmaticManager(),
                server,
                tempDir.resolve(folder).resolve("litematics").toFile(),
                true,
                tempDir.resolve(folder).toFile()
        );
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
