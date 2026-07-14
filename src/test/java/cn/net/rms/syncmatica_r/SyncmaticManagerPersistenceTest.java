package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SyncmaticManagerPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void failedPlacementWriteRemainsPendingForNextServerTick() throws IOException {
        final AtomicLong clock = new AtomicLong(1L);
        final SyncmaticManager manager = new SyncmaticManager(clock::get);
        final Context context = new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                manager,
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
        try {
            final Path store = context.getConfigFolder().toPath().resolve("placement_store");
            Files.writeString(store, "blocks directory creation", StandardCharsets.UTF_8);
            final ServerPlacement placement = new ServerPlacement(
                    UUID.randomUUID(),
                    "build",
                    UUID.randomUUID(),
                    PlayerIdentifier.MISSING_PLAYER
            );
            placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
            manager.addPlacement(placement);

            manager.saveServerState();

            Files.delete(store);
            Files.createDirectory(store);
            clock.set(2_000L);
            manager.tickServer();

            assertTrue(Files.isRegularFile(store.resolve(placement.getId() + ".placement.json")));
        } finally {
            context.shutdown();
        }
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
