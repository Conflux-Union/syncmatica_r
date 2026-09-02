package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
        final JsonElement elem = configuration.get(key);
        if (elem == null) {
            return;
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isBoolean()) {
            discardInvalid(key);
            return;
        }
        loader.accept(elem.getAsBoolean());
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
        final JsonElement elem = configuration.get(key);
        if (elem == null) {
            return;
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isNumber()) {
            discardInvalid(key);
            return;
        }
        final String literal = elem.getAsString();
        if (!literal.matches("-?(?:0|[1-9][0-9]*)")) {
            discardInvalid(key);
            return;
        }
        final int value;
        try {
            value = Integer.parseInt(literal);
        } catch (final NumberFormatException ignored) {
            discardInvalid(key);
            return;
        }
        loader.accept(value);
    }

    @Override
    public void saveInteger(final String key, final Integer value) {
        if (!configuration.has(key)) {
            configuration.addProperty(key, value);
            changed = true;
        }
    }

    @Override
    public void loadString(final String key, final Consumer<String> loader) {
        final JsonElement elem = configuration.get(key);
        if (elem == null) {
            return;
        }
        final JsonPrimitive primitive =
                elem.isJsonPrimitive() ? elem.getAsJsonPrimitive() : null;
        if (primitive == null || !primitive.isString()) {
            discardInvalid(key);
            return;
        }
        loader.accept(primitive.getAsString());
    }

    @Override
    public void saveString(final String key, final String value) {
        if (!configuration.has(key)) {
            configuration.addProperty(key, value);
            changed = true;
        }
    }

    @Override
    public void replaceInteger(final String key, final Integer value) {
        configuration.addProperty(key, value);
        changed = true;
    }

    @Override
    public void replaceString(final String key, final String value) {
        configuration.addProperty(key, value);
        changed = true;
    }

    @Override
    public void reportError() {
        wasError = true;
    }

    private void discardInvalid(final String key) {
        configuration.remove(key);
        wasError = true;
        changed = true;
    }

    public Boolean hadError() {
        return wasError;
    }

    public boolean didWriteDefaults() {
        return changed;
    }
}
