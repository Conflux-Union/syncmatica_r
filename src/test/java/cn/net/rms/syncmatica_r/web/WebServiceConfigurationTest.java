package cn.net.rms.syncmatica_r.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.service.JsonConfiguration;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebServiceConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsAreSafeAndServerIsDisabled() {
        final JsonObject json = new JsonObject();
        final JsonConfiguration configuration = new JsonConfiguration(json);
        final WebService service = new WebService();

        service.getDefaultConfiguration(configuration);
        service.configure(configuration);

        assertFalse(service.isEnabled());
        assertEquals("127.0.0.1", service.getBindAddress());
        assertEquals(8080, service.getPort());
        assertEquals(24, service.getSessionHours());
        assertFalse(service.isSecureCookie());
        assertEquals(65_536, service.getMaxRequestBytes());
        assertEquals(7, json.size());
        assertTrue(configuration.didWriteDefaults());
    }

    @Test
    void loadsConfiguredValues() {
        final JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("bind_address", "0.0.0.0");
        json.addProperty("port", 9876);
        json.addProperty("session_hours", 12);
        json.addProperty("secure_cookie", true);
        json.addProperty("max_request_bytes", 8192);
        json.addProperty("request_timeout_seconds", 7);
        final WebService service = new WebService();

        service.configure(new JsonConfiguration(json));

        assertTrue(service.isEnabled());
        assertEquals("0.0.0.0", service.getBindAddress());
        assertEquals(9876, service.getPort());
        assertEquals(12, service.getSessionHours());
        assertTrue(service.isSecureCookie());
        assertEquals(8192, service.getMaxRequestBytes());
        assertEquals(7, service.getRequestTimeoutSeconds());
    }

    @Test
    void invalidValuesFallBackToDefaultsAndAreRewritten() {
        final JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("bind_address", "not an address");
        json.addProperty("port", 0);
        json.addProperty("session_hours", -1);
        json.addProperty("secure_cookie", false);
        json.addProperty("max_request_bytes", 0);
        json.addProperty("request_timeout_seconds", 0);
        final JsonConfiguration configuration = new JsonConfiguration(json);
        final WebService service = new WebService();

        service.configure(configuration);

        assertEquals("127.0.0.1", service.getBindAddress());
        assertEquals(8080, service.getPort());
        assertEquals(24, service.getSessionHours());
        assertEquals(65_536, service.getMaxRequestBytes());
        assertEquals(10, service.getRequestTimeoutSeconds());
        assertTrue(configuration.hadError());
        assertEquals("127.0.0.1", json.get("bind_address").getAsString());
        assertEquals(8080, json.get("port").getAsInt());
    }

    @Test
    void malformedValuesAreRemovedSoDefaultsCanRewriteThem() {
        final JsonObject json = new JsonObject();
        json.addProperty("port", "not-a-number");
        final JsonConfiguration configuration = new JsonConfiguration(json);
        final WebService service = new WebService();

        service.configure(configuration);
        service.getDefaultConfiguration(configuration);

        assertTrue(configuration.hadError());
        assertEquals(8080, json.get("port").getAsInt());
    }

    @Test
    void contextOwnsWebServiceOnlyOnTheServer() {
        final Context server = context(true, "server");
        final Context client = context(false, "client");
        try {
            assertFalse(server.getWebService().isEnabled());
            assertTrue(server.getLoadedConfiguration().has("web"));
            assertNull(client.getWebService());
        } finally {
            server.shutdown();
            client.shutdown();
        }
    }

    private Context context(final boolean server, final String name) {
        return new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                new SyncmaticManager(),
                server,
                tempDir.resolve(name).resolve("litematics").toFile(),
                true,
                tempDir.resolve(name).toFile());
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id,
                              final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
