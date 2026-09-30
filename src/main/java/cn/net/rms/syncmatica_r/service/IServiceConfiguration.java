package cn.net.rms.syncmatica_r.service;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface IServiceConfiguration {

    void loadBoolean(String key, Consumer<Boolean> loader);

    void saveBoolean(String key, Boolean value);

    void loadInteger(String key, IntConsumer loader);

    /**
     * Returns the integer stored under the key, or null when absent or
     * malformed. Reads only; never mutates the configuration.
     */
    default Integer readInteger(String key) {
        return null;
    }

    /** Removes the key from the configuration if present. */
    default void removeKey(String key) {
    }

    void saveInteger(String key, Integer value);

    default void loadString(final String key, final Consumer<String> loader) {
    }

    default void saveString(final String key, final String value) {
    }

    default void replaceInteger(final String key, final Integer value) {
        saveInteger(key, value);
    }

    default void replaceString(final String key, final String value) {
        saveString(key, value);
    }

    default void reportError() {
    }
}
