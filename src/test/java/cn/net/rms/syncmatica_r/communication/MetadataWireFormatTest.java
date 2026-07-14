package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.schematic.SchematicPeek;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MetadataWireFormatTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripCarriesDisplayNameAndVersionForSupportingPeers() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newServerContext(manager);
        try {
            final ServerPlacement placement = newPlacement("file_name");
            placement.setDisplayName("Display Name");
            placement.setVersion(6, 3465);

            // Feature set of a legacy syncmatica peer (sakura fork): no
            // material/timestamp extensions, but display name and version.
            final ExchangeTarget peer = new TestTarget();
            peer.setFeatureSet(FeatureSet.fromString("CORE\nCORE_EX\nVERSION\nDISPLAY_NAME"));

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            manager.putMetaData(placement, buf, peer);
            final ServerPlacement received = manager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertEquals("Display Name", received.getName());
            assertEquals("file_name", received.getFileName());
            assertEquals(6, received.getLitematicVersion());
            assertEquals(3465, received.getDataVersion());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void peersWithoutNewFeaturesKeepLegacyWireFormat() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newServerContext(manager);
        try {
            final ServerPlacement placement = newPlacement("legacy_peer");
            placement.setDisplayName("Ignored For Legacy");
            placement.setVersion(6, 3465);

            final ExchangeTarget peer = new TestTarget();
            peer.setFeatureSet(FeatureSet.fromString("CORE\nCORE_EX"));

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            manager.putMetaData(placement, buf, peer);
            final ServerPlacement received = manager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertEquals("legacy_peer", received.getName());
            assertEquals(SchematicPeek.UNKNOWN_VERSION, received.getLitematicVersion());
            assertEquals(SchematicPeek.UNKNOWN_VERSION, received.getDataVersion());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void fullLocalFeatureSetRoundTripsAllMetadata() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newServerContext(manager);
        try {
            final ServerPlacement placement = newPlacement("local_peer");
            placement.setDisplayName("Local Peer Build");
            placement.setVersion(7, 4189);
            placement.setCreatedAtMillis(1234L);
            placement.setLastModifiedAtMillis(5678L);

            final ExchangeTarget peer = new TestTarget();
            peer.setFeatureSet(context.getFeatureSet());

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            manager.putMetaData(placement, buf, peer);
            final ServerPlacement received = manager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            assertEquals("Local Peer Build", received.getName());
            assertEquals(7, received.getLitematicVersion());
            assertEquals(4189, received.getDataVersion());
            assertEquals(1234L, received.getCreatedAtMillis());
            assertEquals(5678L, received.getLastModifiedAtMillis());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void sanitizeDisplayNameStripsControlCharactersAndClampsLength() {
        assertEquals("clean name", CommunicationManager.sanitizeDisplayName("clean name\n"));
        assertEquals("", CommunicationManager.sanitizeDisplayName(null));

        final StringBuilder longName = new StringBuilder();
        for (int i = 0; i < ProtocolLimits.MAX_DISPLAY_NAME_LENGTH * 2; i++) {
            longName.append('x');
        }
        final String clamped = CommunicationManager.sanitizeDisplayName(longName.toString());
        assertTrue(clamped.length() <= ProtocolLimits.MAX_DISPLAY_NAME_LENGTH);
        assertEquals(ProtocolLimits.MAX_DISPLAY_NAME_LENGTH, clamped.length());
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
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
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
