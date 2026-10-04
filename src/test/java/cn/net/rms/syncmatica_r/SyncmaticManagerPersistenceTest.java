package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void importsPlacementsFromUpstreamWorldLayout() throws IOException {
        // Upstream syncmatica keeps server metadata inside the save. Pre-create
        // the fork's own folder so the config-folder migration does not copy
        // the whole legacy directory, isolating the world-layout probe.
        Files.createDirectories(tempDir.resolve("syncmatica_r"));
        final Path upstreamStore = tempDir.resolve("syncmatica").resolve("placements.json");
        Files.createDirectories(upstreamStore.getParent());
        final String placementId = "00000000-0000-0000-0000-0000000000aa";
        Files.writeString(upstreamStore,
                "{\"placements\":[{"
                        + "\"id\":\"" + placementId + "\","
                        + "\"file_name\":\"castle\",\"display_name\":\"Castle\","
                        + "\"hash\":\"00000000-0000-0000-0000-00000000000b\","
                        + "\"origin\":{\"position\":[12,64,-8],\"dimension\":\"minecraft:overworld\"},"
                        + "\"rotation\":\"CLOCKWISE_90\",\"mirror\":\"LEFT_RIGHT\","
                        + "\"owner\":{\"uuid\":\"00000000-0000-0000-0000-0000000000cc\",\"name\":\"alice\"}}]}",
                StandardCharsets.UTF_8);

        final SyncmaticManager manager = new SyncmaticManager();
        final Context context = new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                manager,
                true,
                tempDir.resolve("litematics-upstream").toFile(),
                true,
                tempDir.toFile()
        );
        try {
            manager.startup();

            final ServerPlacement placement = manager.getPlacement(UUID.fromString(placementId));
            assertTrue(placement != null, "upstream world-layout placements must be imported");
            assertEquals("Castle", placement.getName());
            assertEquals("minecraft:overworld", placement.getOrigin().getDimensionId());
            assertEquals(BlockRotation.CLOCKWISE_90, placement.getRotation());
            assertEquals(BlockMirror.LEFT_RIGHT, placement.getMirror());
            assertEquals("alice", placement.getOwner().getName());

            manager.saveServerState();
            final Path storeFile = context.getConfigFolder().toPath()
                    .resolve("placement_store")
                    .resolve(placementId + ".placement.json");
            assertTrue(Files.isRegularFile(storeFile), "imported placements must move into the placement store");

            manager.startup();
            assertEquals(1, manager.getAll().size(), "re-loading must not import the source again");
            assertTrue(Files.isRegularFile(upstreamStore), "the upstream source file stays untouched");
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
