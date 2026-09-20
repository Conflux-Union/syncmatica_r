package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.exchange.AbstractExchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A placement being modified keeps its position only on the modifying client
 * until MODIFY_FINISH carries it to the server. Until then the server still
 * holds the old origin, so anything it broadcasts about that placement has to
 * be kept away from that client or the placement snaps back mid-edit.
 */
final class ModifyBroadcastGuardTest {
    private static final BlockPos SERVER_ORIGIN = new BlockPos(10, 64, 10);
    private static final BlockPos CLIENT_MOVED = new BlockPos(200, 70, 200);

    @TempDir
    Path tempDir;

    @Test
    void clientDropsServerModifyBroadcastWhileAModificationSessionIsActive() {
        final ClientFixture fixture = new ClientFixture();
        try {
            // Encoded before the move, so the packet carries the server's view.
            final PacketByteBuf serverBroadcast = fixture.modifyPacket();
            fixture.client.setModifier(fixture.placement, new StubExchange(fixture.server, fixture.context));
            // What the modifying player did: moved the placement locally.
            fixture.placement.move("minecraft:overworld", CLIENT_MOVED, BlockRotation.NONE, BlockMirror.NONE);

            fixture.client.onPacket(fixture.server, PacketType.MODIFY.toIdentifier(ProtocolFlavor.NEW),
                    serverBroadcast);

            assertEquals(CLIENT_MOVED, fixture.placement.getPosition(),
                    "the server still holds the pre-modification origin; applying it mid-edit snaps the placement back");
        } finally {
            fixture.close();
        }
    }

    @Test
    void clientAppliesServerModifyBroadcastOnceTheSessionHasEnded() {
        final ClientFixture fixture = new ClientFixture();
        try {
            final PacketByteBuf serverBroadcast = fixture.modifyPacket();
            fixture.client.setModifier(fixture.placement, new StubExchange(fixture.server, fixture.context));
            fixture.placement.move("minecraft:overworld", CLIENT_MOVED, BlockRotation.NONE, BlockMirror.NONE);

            // The session ends without a modification: the exchange closes and
            // the server state is the truth again.
            fixture.client.setModifier(fixture.placement, null);

            fixture.client.onPacket(fixture.server, PacketType.MODIFY.toIdentifier(ProtocolFlavor.NEW),
                    serverBroadcast);

            assertEquals(SERVER_ORIGIN, fixture.placement.getPosition());
            assertEquals(fixture.alice, fixture.placement.getLastModifiedBy());
        } finally {
            fixture.close();
        }
    }

    @Test
    void serverBroadcastSkipsTheClientThatIsMidModification() {
        final ServerFixture fixture = new ServerFixture();
        try {
            fixture.manager.addTarget(fixture.modifierClient);
            fixture.manager.addTarget(fixture.bystanderClient);
            fixture.manager.setModifier(fixture.placement, new StubExchange(fixture.modifierClient, fixture.context));

            fixture.manager.broadcastPlacementUpdate(fixture.placement);

            assertTrue(fixture.modifierClient.ids.isEmpty(),
                    "a client mid-modification holds a newer pose than the server; the finish exchange will carry it");
            assertEquals(1, fixture.bystanderClient.ids.size());
            assertEquals(PacketType.MODIFY.toIdentifier(ProtocolFlavor.NEW), fixture.bystanderClient.ids.get(0));
        } finally {
            fixture.close();
        }
    }

    private final class ClientFixture {
        private final CapturingTarget server = new CapturingTarget("server");
        private final ClientCommunicationManager client = new ClientCommunicationManager(server);
        private final Context context = new Context(
                new FileStorage(),
                client,
                new SyncmaticManager(),
                false,
                tempDir.resolve("litematics").toFile(),
                false,
                null
        );
        private final PlayerIdentifier alice = player("Alice");
        private final ServerPlacement placement = placement(context, alice);

        private ClientFixture() {
            server.setFeatureSet(context.getFeatureSet());
            server.setProtocolFlavor(ProtocolFlavor.NEW);
        }

        /** Encodes what the server sends: the placement as the server knows it. */
        private PacketByteBuf modifyPacket() {
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            client.putModificationData(placement, buf, server);
            final FeatureSet features = server.getFeatureSet();
            if (features != null && features.hasFeature(Feature.CORE_EX)) {
                buf.writeUuid(placement.getLastModifiedBy().uuid);
                buf.writeString(placement.getLastModifiedBy().getName(), ProtocolLimits.MAX_PLAYER_NAME_LENGTH);
            }
            return buf;
        }

        private PlayerIdentifier player(final String name) {
            return context.getPlayerIdentifierProvider().createOrGet(
                    UUID.nameUUIDFromBytes(name.getBytes()), name);
        }

        private void close() {
            context.shutdown();
        }
    }

    private final class ServerFixture {
        private final TestServerManager manager = new TestServerManager();
        private final Context context = new Context(
                new FileStorage(),
                manager,
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
        private final CapturingTarget modifierClient = new CapturingTarget("modifier");
        private final CapturingTarget bystanderClient = new CapturingTarget("bystander");
        private final ServerPlacement placement =
                placement(context, context.getPlayerIdentifierProvider().createOrGet(
                        UUID.nameUUIDFromBytes("Bob".getBytes()), "Bob"));

        private ServerFixture() {
            modifierClient.setFeatureSet(context.getFeatureSet());
            modifierClient.setProtocolFlavor(ProtocolFlavor.NEW);
            bystanderClient.setFeatureSet(context.getFeatureSet());
            bystanderClient.setProtocolFlavor(ProtocolFlavor.NEW);
        }

        private void close() {
            context.shutdown();
        }
    }

    private static ServerPlacement placement(final Context context, final PlayerIdentifier lastModifiedBy) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), "build", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        placement.move("minecraft:overworld", SERVER_ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        placement.setLastModifiedBy(lastModifiedBy);
        context.getSyncmaticManager().addPlacement(placement);
        return placement;
    }

    private static final class TestServerManager extends ServerCommunicationManager {
        private void addTarget(final ExchangeTarget target) {
            broadcastTargets.add(target);
        }
    }

    private static final class StubExchange extends AbstractExchange {
        private StubExchange(final ExchangeTarget partner, final Context context) {
            super(partner, context);
        }

        @Override
        public boolean checkPacket(final net.minecraft.util.Identifier id, final PacketByteBuf packetBuf) {
            return false;
        }

        @Override
        public void handle(final net.minecraft.util.Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        public void init() {
        }
    }

    private static final class CapturingTarget extends ExchangeTarget {
        private final List<net.minecraft.util.Identifier> ids = new ArrayList<>();

        private CapturingTarget(final String name) {
            super(name);
        }

        @Override
        public void sendPacket(final net.minecraft.util.Identifier id, final PacketByteBuf packetBuf,
                               final Context context) {
            ids.add(id);
        }
    }
}
