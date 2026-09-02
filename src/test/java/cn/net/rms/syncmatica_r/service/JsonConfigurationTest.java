package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class JsonConfigurationTest {
    @Test
    void acceptsOnlyBooleanPrimitivesForBooleans() {
        final JsonObject valid = object("{\"value\":true}");
        final AtomicBoolean loaded = new AtomicBoolean();
        new JsonConfiguration(valid).loadBoolean("value", loaded::set);
        assertTrue(loaded.get());

        for (final String invalid : new String[] {"\"true\"", "1", "null", "[]", "{}"}) {
            final JsonObject json = object("{\"value\":" + invalid + "}");
            final JsonConfiguration configuration = new JsonConfiguration(json);
            configuration.loadBoolean("value", ignored -> { });
            configuration.saveBoolean("value", false);
            assertTrue(configuration.hadError(), invalid);
            assertFalse(json.get("value").getAsBoolean(), invalid);
        }
    }

    @Test
    void acceptsOnlyExactIntRangeNumberPrimitivesForIntegers() {
        final JsonObject valid = object("{\"value\":2147483647}");
        final AtomicInteger loaded = new AtomicInteger();
        new JsonConfiguration(valid).loadInteger("value", loaded::set);
        assertEquals(Integer.MAX_VALUE, loaded.get());

        for (final String invalid : new String[] {
                "\"1\"", "1.0", "1.5", "1e2", "-2E3", "2147483648", "-2147483649",
                "true", "null", "[]", "{}"
        }) {
            final JsonObject json = object("{\"value\":" + invalid + "}");
            final JsonConfiguration configuration = new JsonConfiguration(json);
            configuration.loadInteger("value", ignored -> { });
            configuration.saveInteger("value", 7);
            assertTrue(configuration.hadError(), invalid);
            assertEquals(7, json.get("value").getAsInt(), invalid);
        }
    }

    @Test
    void acceptsOnlyStringPrimitivesForStrings() {
        final JsonObject valid = object("{\"value\":\"text\"}");
        final AtomicReference<String> loaded = new AtomicReference<>();
        new JsonConfiguration(valid).loadString("value", loaded::set);
        assertEquals("text", loaded.get());

        for (final String invalid : new String[] {"1", "true", "null", "[]", "{}"}) {
            final JsonObject json = object("{\"value\":" + invalid + "}");
            final JsonConfiguration configuration = new JsonConfiguration(json);
            configuration.loadString("value", ignored -> { });
            configuration.saveString("value", "default");
            assertTrue(configuration.hadError(), invalid);
            assertEquals("default", json.get("value").getAsString(), invalid);
        }
    }

    private static JsonObject object(final String json) {
        return new Gson().fromJson(json, JsonObject.class);
    }
}
