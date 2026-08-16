package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The stocking area rides along at the very end of the placement payload, so a
 * peer that predates the feature must still read the stream to completion, and
 * the section must never let a client dictate server state.
 */
final class StockingAreaWireFormatTest {

    /** A client context stores its configuration next to the game directory. */
    private static final Path CLIENT_CONFIG_ROOT = Paths.get("config");

    @TempDir
    Path tempDir;

    private boolean clientConfigExisted;

    @BeforeEach
    void recordClientConfigState() {
        clientConfigExisted = Files.exists(CLIENT_CONFIG_ROOT);
    }

    @AfterEach
    void removeGeneratedClientConfig() throws IOException {
        if (clientConfigExisted || !Files.exists(CLIENT_CONFIG_ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(CLIENT_CONFIG_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void serverSendsStockingAreaToSupportingClient() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newServerContext(serverManager);
        final Context clientContext = newClientContext(clientManager);
        try {
            final ServerPlacement placement = newPlacement("with_area");
            placement.setStockingArea(new StockingAreaDefinition(
                    "minecraft:overworld", new BlockPos(10, 60, -5), new BlockPos(-3, 70, 12)));

            final FeatureSet shared = serverContext.getFeatureSet();
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putMetaData(placement, buf, peerWith(shared));
            final ServerPlacement received = clientManager.receiveMetaData(buf, peerWith(shared));

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            final StockingAreaDefinition area = received.getStockingArea();
            assertNotNull(area, "client must learn the stocking area");
            assertEquals("minecraft:overworld", area.getDimensionId());
            assertEquals(new BlockPos(-3, 60, -5), area.getMin());
            assertEquals(new BlockPos(10, 70, 12), area.getMax());
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void placementWithoutAreaClearsItOnTheClient() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newServerContext(serverManager);
        final Context clientContext = newClientContext(clientManager);
        try {
            final FeatureSet shared = serverContext.getFeatureSet();
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putMetaData(newPlacement("no_area"), buf, peerWith(shared));
            final ServerPlacement received = clientManager.receiveMetaData(buf, peerWith(shared));

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertNull(received.getStockingArea());
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void peerWithoutTheFeatureNeverSeesTheSection() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newServerContext(serverManager);
        final Context clientContext = newClientContext(clientManager);
        try {
            final ServerPlacement placement = newPlacement("legacy_peer");
            placement.setStockingArea(new StockingAreaDefinition(
                    "minecraft:overworld", BlockPos.ORIGIN, new BlockPos(4, 4, 4)));

            final FeatureSet legacy = FeatureSet.fromString("CORE\nCORE_EX\nMATERIAL_PROGRESS");
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putMetaData(placement, buf, peerWith(legacy));
            final ServerPlacement received = clientManager.receiveMetaData(buf, peerWith(legacy));

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertNull(received.getStockingArea(), "legacy peers must not gain a stocking area");
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void serverRefusesAStockingAreaAuthoredByAClient() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newServerContext(serverManager);
        final Context clientContext = newClientContext(clientManager);
        try {
            final ServerPlacement placement = newPlacement("client_authored");
            placement.setStockingArea(new StockingAreaDefinition(
                    "minecraft:the_end", BlockPos.ORIGIN, new BlockPos(64, 64, 64)));

            final FeatureSet shared = clientContext.getFeatureSet();
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            clientManager.putMetaData(placement, buf, peerWith(shared));
            final ServerPlacement received = serverManager.receiveMetaData(buf, peerWith(shared));

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertNull(received.getStockingArea(), "a client must not be able to set a stocking area");
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void modificationPayloadRoundTripsWithTheSection() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newServerContext(serverManager);
        final Context clientContext = newClientContext(clientManager);
        try {
            final ServerPlacement placement = newPlacement("modified");
            placement.setStockingArea(new StockingAreaDefinition(
                    "minecraft:overworld", new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)));

            final FeatureSet shared = serverContext.getFeatureSet();
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putPositionData(placement, buf, peerWith(shared));
            serverManager.putMaterialData(placement, buf, peerWith(shared));

            final ServerPlacement target = newPlacement("modified");
            clientManager.receiveModificationData(target, buf, peerWith(shared));

            assertEquals(0, buf.readableBytes(), "modification payload must be fully consumed");
            assertNotNull(target.getStockingArea());
            assertEquals(new BlockPos(1, 2, 3), target.getStockingArea().getMin());
            assertEquals(new BlockPos(4, 5, 6), target.getStockingArea().getMax());
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void serverFeatureSetAdvertisesStockingAreaSetup() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newServerContext(manager);
        try {
            assertEquals(true, context.getFeatureSet().hasFeature(Feature.STOCKING_AREA_SETUP));
        } finally {
            context.shutdown();
        }
    }

    private static ExchangeTarget peerWith(final FeatureSet featureSet) {
        final ExchangeTarget target = new TestTarget();
        target.setFeatureSet(featureSet);
        return target;
    }

    private ServerPlacement newPlacement(final String fileName) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(),
                fileName,
                UUID.randomUUID(),
                PlayerIdentifier.MISSING_PLAYER
        );
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        return placement;
    }

    private Context newServerContext(final CommunicationManager manager) {
        return new Context(
                new FileStorage(),
                manager,
                new SyncmaticManager(),
                true,
                tempDir.resolve("server/litematics").toFile(),
                true,
                tempDir.resolve("server").toFile()
        );
    }

    private Context newClientContext(final CommunicationManager manager) {
        return new Context(
                new FileStorage(),
                manager,
                new SyncmaticManager(),
                tempDir.resolve("client/litematics").toFile()
        );
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }

    private static final class TestTarget extends ExchangeTarget {
        private TestTarget() {
            super("test-peer");
        }

        @Override
        public void sendPacket(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
        }
    }
}
