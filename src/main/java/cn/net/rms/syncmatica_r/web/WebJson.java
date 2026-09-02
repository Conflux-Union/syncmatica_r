package cn.net.rms.syncmatica_r.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public final class WebJson {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private WebJson() {
    }

    public static String toJson(final Object value) {
        return GSON.toJson(value);
    }

    public static JsonObject parseObject(final String json) {
        try {
            final JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null) {
                throw new IllegalArgumentException("JSON body must be an object");
            }
            return object;
        } catch (final JsonParseException | IllegalStateException failure) {
            throw new IllegalArgumentException("JSON body must be an object", failure);
        }
    }
}
