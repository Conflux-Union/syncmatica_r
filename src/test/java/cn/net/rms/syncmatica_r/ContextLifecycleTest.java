package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ContextLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void startsPlacementManagerBeforeWebAndRollsBackInReverseOnWebFailure() throws Exception {
        final Path configFolder = tempDir.resolve(Syncmatica.MOD_ID);
        Files.createDirectories(configFolder);
        Files.writeString(configFolder.resolve("config.json"),
                "{\"web\":{\"enabled\":true}}");
        final TrackingSyncmaticManager manager = new TrackingSyncmaticManager();
        final Context context = new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                manager,
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile());

        assertThrows(IllegalStateException.class, context::startup);

        assertEquals(1, manager.startups);
        assertEquals(1, manager.shutdowns);
        assertFalse(context.isStarted());
    }

    private static final class TrackingSyncmaticManager extends SyncmaticManager {
        private int startups;
        private int shutdowns;

        @Override
        public void startup() {
            startups++;
        }

        @Override
        public void shutdown() {
            shutdowns++;
        }
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
