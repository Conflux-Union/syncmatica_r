package cn.net.rms.syncmatica_r.command;

import cn.net.rms.syncmatica_r.service.ConfigOption;
import cn.net.rms.syncmatica_r.service.ConfigRegistry;
import cn.net.rms.syncmatica_r.service.ConfigStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ConfigCommandLogic {
    private final ConfigRegistry registry;
    private final ConfigStore store;

    ConfigCommandLogic(final ConfigRegistry registry, final ConfigStore store) {
        this.registry = registry;
        this.store = store;
    }

    List<String> list(final String section) {
        final List<String> result = new ArrayList<>();
        if (section == null) {
            for (final String candidate : registry.sections()) {
                appendSection(result, candidate);
            }
        } else {
            if (registry.keys(section).isEmpty()) {
                throw new IllegalArgumentException("Unknown configuration section " + section);
            }
            appendSection(result, section);
        }
        return result;
    }

    String get(final String section, final String key) {
        return describe(requireOption(section, key));
    }

    String set(final String section, final String key, final String value) throws IOException {
        store.set(section, key, value);
        return name(section, key) + " = " + requireOption(section, key).getCurrentValue();
    }

    String reset(final String section, final String key) throws IOException {
        store.reset(section, key);
        return name(section, key) + " = " + requireOption(section, key).getCurrentValue() + " (default)";
    }

    private void appendSection(final List<String> result, final String section) {
        for (final String key : registry.keys(section)) {
            result.add(describe(requireOption(section, key)));
        }
    }

    private ConfigOption<?> requireOption(final String section, final String key) {
        final ConfigOption<?> option = registry.find(section, key);
        if (option == null) {
            throw new IllegalArgumentException("Unknown configuration option " + name(section, key));
        }
        return option;
    }

    private static String describe(final ConfigOption<?> option) {
        return name(option.getSection(), option.getKey()) + " = " + option.getCurrentValue()
                + " (default: " + option.getDefaultValue() + ")";
    }

    private static String name(final String section, final String key) {
        return section + "." + key;
    }
}
