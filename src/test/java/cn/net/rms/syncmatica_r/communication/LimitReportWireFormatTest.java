package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialAvailability;
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

final class LimitReportWireFormatTest {

    private static final String LIMIT_AWARE_FEATURES =
            "CORE\nCORE_EX\nMESSAGE\nMATERIAL_PROGRESS\nMATERIAL_CLAIMS\nLIMIT_REPORT";
    private static final String LEGACY_FEATURES =
            "CORE\nCORE_EX\nMESSAGE\nMATERIAL_PROGRESS\nMATERIAL_CLAIMS";

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
    void materialAvailabilityReachesLimitAwareClients() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newContext(serverManager, true, "server");
        final Context clientContext = newContext(clientManager, false, "client");
        try {
            final ServerPlacement placement = newPlacement("too_big");
            placement.setMaterialAvailability(MaterialAvailability.TOO_MANY_BLOCKS);

            final ExchangeTarget peer = new TestTarget();
            peer.setFeatureSet(FeatureSet.fromString(LIMIT_AWARE_FEATURES));

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putMetaData(placement, buf, peer);
            final ServerPlacement received = clientManager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertEquals(MaterialAvailability.TOO_MANY_BLOCKS, received.getMaterialAvailability());
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void peersWithoutLimitReportKeepTheLegacyMaterialLayout() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context serverContext = newContext(serverManager, true, "server");
        final Context clientContext = newContext(clientManager, false, "client");
        try {
            final ServerPlacement placement = newPlacement("too_big_legacy");
            placement.setMaterialAvailability(MaterialAvailability.FILE_TOO_LARGE);

            final ExchangeTarget peer = new TestTarget();
            peer.setFeatureSet(FeatureSet.fromString(LEGACY_FEATURES));

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putMetaData(placement, buf, peer);
            final ServerPlacement received = clientManager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertEquals(MaterialAvailability.AVAILABLE, received.getMaterialAvailability());
        } finally {
            clientContext.shutdown();
            serverContext.shutdown();
        }
    }

    @Test
    void messageDetailIsOnlyAppendedForLimitAwareClients() {
        final PacketByteBuf limitAware = MessageCodec.encode(
                FeatureSet.fromString(LIMIT_AWARE_FEATURES),
                MessageType.ERROR,
                "syncmatica_r.error.material_file_too_large",
                "96.0 MB > 64.0 MB"
        );
        assertEquals(MessageType.ERROR, MessageCodec.readType(limitAware));
        assertEquals("syncmatica_r.error.material_file_too_large", MessageCodec.readIdentifier(limitAware));
        assertEquals("96.0 MB > 64.0 MB", MessageCodec.readDetail(limitAware));
        assertEquals(0, limitAware.readableBytes());

        final PacketByteBuf legacy = MessageCodec.encode(
                FeatureSet.fromString(LEGACY_FEATURES),
                MessageType.ERROR,
                "syncmatica_r.error.material_file_too_large",
                "96.0 MB > 64.0 MB"
        );
        assertEquals(MessageType.ERROR, MessageCodec.readType(legacy));
        assertEquals("syncmatica_r.error.material_file_too_large", MessageCodec.readIdentifier(legacy));
        assertEquals("", MessageCodec.readDetail(legacy), "legacy peers must not receive trailing detail bytes");
        assertEquals(0, legacy.readableBytes());
    }

    @Test
    void messagesWithoutDetailKeepTheTwoFieldLayout() {
        final PacketByteBuf buf = MessageCodec.encode(
                FeatureSet.fromString(LIMIT_AWARE_FEATURES),
                MessageType.ERROR,
                "syncmatica_r.error.permission_denied",
                ""
        );
        assertEquals(MessageType.ERROR, MessageCodec.readType(buf));
        assertEquals("syncmatica_r.error.permission_denied", MessageCodec.readIdentifier(buf));
        assertEquals("", MessageCodec.readDetail(buf));
        assertEquals(0, buf.readableBytes());
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

    private Context newContext(final CommunicationManager manager, final boolean server, final String folder) {
        return new Context(
                new FileStorage(),
                manager,
                new SyncmaticManager(),
                server,
                tempDir.resolve(folder).resolve("litematics").toFile(),
                true,
                tempDir.resolve(folder).toFile()
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
