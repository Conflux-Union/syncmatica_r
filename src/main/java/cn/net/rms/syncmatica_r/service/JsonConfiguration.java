package cn.net.rms.syncmatica_r.service;

import ch.endte.syncmatica.service.IServiceConfiguration;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class JsonConfiguration implements IServiceConfiguration {

    public final JsonObject configuration;
    private Boolean wasError;
    private boolean changed;

    public JsonConfiguration(final JsonObject configuration) {
        this.configuration = configuration;
        wasError = false;
        changed = false;
    }

    @Override
    public void loadBoolean(final String key, final Consumer<Boolean> loader) {
        try {
            final JsonElement elem = configuration.get(key);
            if (elem != null) {
                loader.accept(elem.getAsBoolean());
            }
        } catch (final Exception ignored) {
            wasError = true;
        }
    }

    @Override
    public void saveBoolean(final String key, final Boolean value) {
        if (!configuration.has(key)) {
            configuration.addProperty(key, value);
            changed = true;
        }
    }

    @Override
    public void loadInteger(final String key, final IntConsumer loader) {
        try {
            final JsonElement elem = configuration.get(key);
            if (elem != null) {
                loader.accept(elem.getAsInt());
            }
        } catch (final Exception ignored) {
            wasError = true;
        }
    }

    @Override
    public void saveInteger(final String key, final Integer value) {
        if (!configuration.has(key)) {
            configuration.addProperty(key, value);
            changed = true;
        }
    }

    public Boolean hadError() {
        return wasError;
    }

    public boolean didWriteDefaults() {
        return changed;
    }
}
