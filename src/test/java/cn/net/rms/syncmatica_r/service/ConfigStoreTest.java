package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesBooleanValuesStrictly() {
        final ConfigOption<Boolean> option = ConfigOption.bool(
                "build", "enabled", true, () -> true, value -> { });

        assertTrue(option.parse("true"));
        assertFalse(option.parse("FALSE"));
        assertThrows(IllegalArgumentException.class, () -> option.parse("yes"));
        assertThrows(IllegalArgumentException.class, () -> option.parse(" true "));
    }

    @Test
    void rejectsInvalidAndOutOfRangeIntegers() {
        final ConfigOption<Integer> option = ConfigOption.integer(
                "build", "limit", 5, 1, 10, () -> 5, value -> { });

        assertEquals(1, option.parse("1"));
        assertEquals(10, option.parse("10"));
        assertThrows(IllegalArgumentException.class, () -> option.parse("1.5"));
        assertThrows(IllegalArgumentException.class, () -> option.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> option.parse("11"));
    }

    @Test
    void exposesOptionMetadataAndCurrentValue() {
        final AtomicInteger current = new AtomicInteger(7);
        final ConfigOption<Integer> option = ConfigOption.integer(
                "build", "limit", 5, 1, 10, current::get, current::set);

        assertEquals("build", option.getSection());
        assertEquals("limit", option.getKey());
        assertEquals(5, option.getDefaultValue());
        assertEquals(7, option.getCurrentValue());
    }

    @Test
    void registryLooksUpOptionsInDeterministicOrder() {
        final ConfigOption<Boolean> zeta = booleanOption("zeta", "enabled");
        final ConfigOption<Boolean> beta = booleanOption("alpha", "beta");
        final ConfigOption<Boolean> alpha = booleanOption("alpha", "alpha");
        final ConfigRegistry registry = new ConfigRegistry();

        registry.add(zeta);
        registry.add(beta);
        registry.add(alpha);

        assertSame(alpha, registry.find("alpha", "alpha"));
        assertEquals(Arrays.asList("alpha", "zeta"), registry.sections());
        assertEquals(Arrays.asList("alpha", "beta"), registry.keys("alpha"));
    }

    @Test
    void registryRejectsDuplicateDefinitions() {
        final ConfigRegistry registry = new ConfigRegistry();
        registry.add(booleanOption("build", "enabled"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.add(booleanOption("build", "enabled")));
    }

    @Test
    void setPersistsFullJsonBeforeApplyingRuntimeValue() throws Exception {
        final Path configFile = temporaryDirectory.resolve("config.json");
        final JsonObject initial = new JsonObject();
        initial.addProperty("unknown_root", "preserved");
        final JsonObject build = new JsonObject();
        build.addProperty("limit", 3);
        build.addProperty("unknown_key", 42);
        initial.add("build", build);
        final AtomicInteger runtime = new AtomicInteger(3);
        final ConfigRegistry registry = new ConfigRegistry();
        registry.add(ConfigOption.integer(
                "build", "limit", 5, 1, 10, runtime::get, value -> {
                    assertTrue(Files.isRegularFile(configFile));
                    runtime.set(value);
                }));
        final ConfigStore store = new ConfigStore(configFile, initial, registry);

        store.set("build", "limit", "7");

        final JsonObject persisted = readJson(configFile);
        assertEquals("preserved", persisted.get("unknown_root").getAsString());
        assertEquals(42, persisted.getAsJsonObject("build").get("unknown_key").getAsInt());
        assertEquals(7, persisted.getAsJsonObject("build").get("limit").getAsInt());
        assertEquals(7, store.snapshot().getAsJsonObject("build").get("limit").getAsInt());
        assertEquals(7, runtime.get());
        assertEquals(3, initial.getAsJsonObject("build").get("limit").getAsInt());
    }

    @Test
    void resetChangesOnlyOneKeyToItsDefault() throws Exception {
        final Path configFile = temporaryDirectory.resolve("config.json");
        final JsonObject initial = new JsonObject();
        final JsonObject build = new JsonObject();
        build.addProperty("limit", 8);
        build.addProperty("enabled", false);
        initial.add("build", build);
        final AtomicInteger limit = new AtomicInteger(8);
        final AtomicBoolean enabled = new AtomicBoolean(false);
        final ConfigRegistry registry = new ConfigRegistry();
        registry.add(ConfigOption.integer(
                "build", "limit", 5, 1, 10, limit::get, limit::set));
        registry.add(ConfigOption.bool(
                "build", "enabled", true, enabled::get, enabled::set));
        final ConfigStore store = new ConfigStore(configFile, initial, registry);

        store.reset("build", "limit");

        final JsonObject persistedBuild = readJson(configFile).getAsJsonObject("build");
        assertEquals(5, persistedBuild.get("limit").getAsInt());
        assertFalse(persistedBuild.get("enabled").getAsBoolean());
        assertEquals(5, limit.get());
        assertFalse(enabled.get());
    }

    @Test
    void booleanSetAndResetArePersistedAndApplied() throws Exception {
        final Path configFile = temporaryDirectory.resolve("config.json");
        final JsonObject initial = new JsonObject();
        final JsonObject build = new JsonObject();
        build.addProperty("enabled", true);
        initial.add("build", build);
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final ConfigRegistry registry = new ConfigRegistry();
        registry.add(ConfigOption.bool(
                "build", "enabled", true, enabled::get, enabled::set));
        final ConfigStore store = new ConfigStore(configFile, initial, registry);

        store.set("build", "enabled", "false");

        assertFalse(readJson(configFile).getAsJsonObject("build").get("enabled").getAsBoolean());
        assertFalse(enabled.get());

        store.reset("build", "enabled");

        assertTrue(readJson(configFile).getAsJsonObject("build").get("enabled").getAsBoolean());
        assertTrue(enabled.get());
    }

    @Test
    void failedPersistenceLeavesRuntimeAndJsonUnchanged() throws Exception {
        final Path configFile = temporaryDirectory.resolve("config.json");
        Files.createDirectory(configFile);
        final JsonObject initial = new JsonObject();
        final JsonObject build = new JsonObject();
        build.addProperty("limit", 3);
        initial.add("build", build);
        final AtomicInteger runtime = new AtomicInteger(3);
        final ConfigRegistry registry = new ConfigRegistry();
        registry.add(ConfigOption.integer(
                "build", "limit", 5, 1, 10, runtime::get, runtime::set));
        final ConfigStore store = new ConfigStore(configFile, initial, registry);

        assertThrows(IOException.class, () -> store.set("build", "limit", "7"));

        assertEquals(3, runtime.get());
        assertEquals(3, store.snapshot().getAsJsonObject("build").get("limit").getAsInt());
        assertTrue(Files.isDirectory(configFile));
        try (java.util.stream.Stream<Path> children = Files.list(temporaryDirectory)) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void rejectsUnknownOptionsWithoutWriting() {
        final Path configFile = temporaryDirectory.resolve("config.json");
        final ConfigStore store = new ConfigStore(
                configFile, new JsonObject(), new ConfigRegistry());

        assertThrows(IllegalArgumentException.class,
                () -> store.set("missing", "key", "true"));
        assertFalse(Files.exists(configFile));
    }

    private static ConfigOption<Boolean> booleanOption(final String section, final String key) {
        return ConfigOption.bool(section, key, true, () -> true, value -> { });
    }

    private static JsonObject readJson(final Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final JsonObject result = new Gson().fromJson(reader, JsonObject.class);
            assertNotNull(result);
            return result;
        }
    }
}
