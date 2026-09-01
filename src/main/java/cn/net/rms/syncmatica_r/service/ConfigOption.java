package cn.net.rms.syncmatica_r.service;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

public final class ConfigOption<T> {
    private final String section;
    private final String key;
    private final T defaultValue;
    private final Supplier<T> currentValue;
    private final Consumer<T> apply;
    private final Parser<T> parser;

    private ConfigOption(
            final String section,
            final String key,
            final T defaultValue,
            final Supplier<T> currentValue,
            final Consumer<T> apply,
            final Parser<T> parser
    ) {
        this.section = requireName(section, "section");
        this.key = requireName(key, "key");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.currentValue = Objects.requireNonNull(currentValue, "currentValue");
        this.apply = Objects.requireNonNull(apply, "apply");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public static ConfigOption<Boolean> bool(
            final String section,
            final String key,
            final boolean defaultValue,
            final Supplier<Boolean> currentValue,
            final Consumer<Boolean> apply
    ) {
        return new ConfigOption<>(
                section, key, defaultValue, currentValue, apply, value -> {
                    if ("true".equalsIgnoreCase(value)) {
                        return true;
                    }
                    if ("false".equalsIgnoreCase(value)) {
                        return false;
                    }
                    throw new IllegalArgumentException("Expected true or false");
                });
    }

    public static ConfigOption<Integer> integer(
            final String section,
            final String key,
            final int defaultValue,
            final int minimum,
            final int maximum,
            final Supplier<Integer> currentValue,
            final Consumer<Integer> apply
    ) {
        return integer(
                section,
                key,
                defaultValue,
                value -> value >= minimum && value <= maximum,
                "Expected an integer from " + minimum + " through " + maximum,
                currentValue,
                apply
        );
    }

    public static ConfigOption<Integer> integer(
            final String section,
            final String key,
            final int defaultValue,
            final IntPredicate validator,
            final String validationMessage,
            final Supplier<Integer> currentValue,
            final Consumer<Integer> apply
    ) {
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(validationMessage, "validationMessage");
        if (!validator.test(defaultValue)) {
            throw new IllegalArgumentException("Default value is outside the allowed range");
        }
        return new ConfigOption<>(
                section, key, defaultValue, currentValue, apply, value -> {
                    final int parsed;
                    try {
                        parsed = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        throw new IllegalArgumentException("Expected an integer", exception);
                    }
                    if (!validator.test(parsed)) {
                        throw new IllegalArgumentException(validationMessage);
                    }
                    return parsed;
                });
    }

    public String getSection() {
        return section;
    }

    public String getKey() {
        return key;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T getCurrentValue() {
        return currentValue.get();
    }

    public T parse(final String value) {
        return parser.parse(Objects.requireNonNull(value, "value"));
    }

    void apply(final T value) {
        apply.accept(value);
    }

    private static String requireName(final String name, final String label) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return name;
    }

    private interface Parser<T> {
        T parse(String value);
    }
}
