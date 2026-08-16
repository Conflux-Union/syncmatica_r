package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
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

final class BuildRegionWireFormatTest {

    @TempDir
    Path tempDir;

    @Test
    void regionsAndTheirClaimantsSurviveARoundTrip() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newServerContext(manager);
        try {
            final ServerPlacement placement = newPlacement();
            placement.getBuildRegions().getOrCreate("roof", 250L)
                    .addClaimer(player(context, "Alice"));
            placement.getBuildRegions().getOrCreate("walls", 40L);

            final ExchangeTarget peer = peerWith(context);
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            manager.putMetaData(placement, buf, peer);
            final ServerPlacement received = manager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            // The receiving side of this context is a server, which owns the state
            // and therefore discards the copy — but the bytes still have to line up.
            assertNotNull(received);
        } finally {
            context.shutdown();
        }
    }

    /**
     * The section is appended at the end of the metadata payload, so a peer that
     * predates build management stops reading before it and the stream still ends
     * where that peer expects.
     */
    @Test
    void peersWithoutBuildManagementNeverSeeTheSection() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newServerContext(manager);
        try {
            final ServerPlacement placement = newPlacement();
            placement.getBuildRegions().getOrCreate("roof", 250L)
                    .addClaimer(player(context, "Alice"));

            final ExchangeTarget peer = new TestTarget();
            peer.setFeatureSet(FeatureSet.fromString("CORE\nCORE_EX"));

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            manager.putMetaData(placement, buf, peer);
            final ServerPlacement received = manager.receiveMetaData(buf, peer);

            assertEquals(0, buf.readableBytes(), "an older peer must consume the whole payload");
            assertTrue(received.getBuildRegions().isEmpty());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void aClientAppliesWhatTheServerSent() {
        final StubCommunicationManager serverManager = new StubCommunicationManager();
        final Context server = newServerContext(serverManager);
        final StubCommunicationManager clientManager = new StubCommunicationManager();
        final Context client = newClientContext(clientManager);
        try {
            final ServerPlacement placement = newPlacement();
            placement.getBuildRegions().getOrCreate("roof", 250L)
                    .addClaimer(player(server, "Alice"));
            placement.getBuildRegions().getOrCreate("walls", 40L);

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            serverManager.putMetaData(placement, buf, peerWith(server));
            final ServerPlacement received = clientManager.receiveMetaData(buf, peerWith(server));

            assertEquals(0, buf.readableBytes(), "metadata payload must be fully consumed");
            final BuildRegion roof = received.getBuildRegions().get("roof");
            assertNotNull(roof);
            assertEquals(250L, roof.getRequiredBlocks());
            assertEquals("Alice", roof.getClaimants().iterator().next().getName());

            final BuildRegion walls = received.getBuildRegions().get("walls");
            assertNotNull(walls);
            assertTrue(walls.getClaimants().isEmpty());
            assertNull(received.getBuildRegions().get("basement"));
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }

    @Test
    void anImpossibleRegionCountIsRejectedRatherThanAllocated() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = newClientContext(manager);
        try {
            final ExchangeTarget peer = peerWith(context);
            final ServerPlacement placement = newPlacement();

            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            manager.putMetaData(placement, buf, peer);
            // Overwrite the region count that putMetaData wrote last.
            final PacketByteBuf hostile = new PacketByteBuf(Unpooled.buffer());
            hostile.writeBytes(buf, buf.readableBytes() - Integer.BYTES);
            hostile.writeInt(Integer.MAX_VALUE);

            assertThrows(IllegalArgumentException.class, () -> manager.receiveMetaData(hostile, peer));
        } finally {
            context.shutdown();
        }
    }

    private ExchangeTarget peerWith(final Context context) {
        final ExchangeTarget peer = new TestTarget();
        peer.setFeatureSet(context.getFeatureSet());
        return peer;
    }

    private PlayerIdentifier player(final Context context, final String name) {
        return context.getPlayerIdentifierProvider().createOrGet(UUID.nameUUIDFromBytes(name.getBytes()), name);
    }

    private ServerPlacement newPlacement() {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(),
                "build",
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
                tempDir.resolve("server").toFile(),
                true,
                tempDir.toFile()
        );
    }

    private Context newClientContext(final CommunicationManager manager) {
        return new Context(
                new FileStorage(),
                manager,
                new SyncmaticManager(),
                tempDir.resolve("client").toFile()
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
