package cn.net.rms.syncmatica_r.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigRegistry {
    private final Map<String, Map<String, ConfigOption<?>>> options = new TreeMap<>();

    public void add(final ConfigOption<?> option) {
        final Map<String, ConfigOption<?>> section = options.computeIfAbsent(
                option.getSection(), ignored -> new TreeMap<>());
        if (section.putIfAbsent(option.getKey(), option) != null) {
            throw new IllegalArgumentException(
                    "Duplicate configuration option " + option.getSection() + "." + option.getKey());
        }
    }

    public ConfigOption<?> find(final String section, final String key) {
        final Map<String, ConfigOption<?>> sectionOptions = options.get(section);
        return sectionOptions == null ? null : sectionOptions.get(key);
    }

    public List<String> sections() {
        return Collections.unmodifiableList(new ArrayList<>(options.keySet()));
    }

    public List<String> keys(final String section) {
        final Map<String, ConfigOption<?>> sectionOptions = options.get(section);
        if (sectionOptions == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(sectionOptions.keySet()));
    }

    public void saveDefaults(final String section, final IServiceConfiguration configuration) {
        for (final String key : keys(section)) {
            final Object value = find(section, key).getDefaultValue();
            if (value instanceof Boolean) {
                configuration.saveBoolean(key, (Boolean) value);
            } else {
                configuration.saveInteger(key, (Integer) value);
            }
        }
    }
}
