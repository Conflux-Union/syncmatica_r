package cn.net.rms.syncmatica_r.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private final ConfigRegistry registry;
    private JsonObject configuration;

    public ConfigStore(
            final Path configFile,
            final JsonObject configuration,
            final ConfigRegistry registry
    ) {
        this.configFile = configFile.toAbsolutePath();
        this.configuration = copy(configuration);
        this.registry = registry;
    }

    public void set(final String section, final String key, final String value) throws IOException {
        set(requireOption(section, key), value);
    }

    public void reset(final String section, final String key) throws IOException {
        reset(requireOption(section, key));
    }

    public JsonObject snapshot() {
        return copy(configuration);
    }

    private <T> void set(final ConfigOption<T> option, final String value) throws IOException {
        update(option, option.parse(value));
    }

    private <T> void reset(final ConfigOption<T> option) throws IOException {
        update(option, option.getDefaultValue());
    }

    private <T> void update(final ConfigOption<T> option, final T value) throws IOException {
        final JsonObject updated = copy(configuration);
        JsonObject section = updated.getAsJsonObject(option.getSection());
        if (section == null) {
            section = new JsonObject();
            updated.add(option.getSection(), section);
        }
        if (value instanceof Boolean) {
            section.addProperty(option.getKey(), (Boolean) value);
        } else {
            section.addProperty(option.getKey(), (Integer) value);
        }

        persist(updated);
        option.apply(value);
        configuration = updated;
    }

    private ConfigOption<?> requireOption(final String section, final String key) {
        final ConfigOption<?> option = registry.find(section, key);
        if (option == null) {
            throw new IllegalArgumentException("Unknown configuration option " + section + "." + key);
        }
        return option;
    }

    private static JsonObject copy(final JsonObject source) {
        return GSON.fromJson(GSON.toJson(source), JsonObject.class);
    }

    private void persist(final JsonObject updated) throws IOException {
        final Path parent = configFile.getParent();
        Files.createDirectories(parent);
        final Path temporary = Files.createTempFile(
                parent, configFile.getFileName().toString() + ".", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(updated, writer);
            }
            try {
                Files.move(
                        temporary,
                        configFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
