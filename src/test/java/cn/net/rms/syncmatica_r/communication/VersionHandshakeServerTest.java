package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.exchange.VersionHandshakeServer;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VersionHandshakeServerTest {
    @TempDir
    Path tempDir;

    @Test
    void sendsPlacementsIndividuallyBeforeEmptyConfirmationMarker() {
        final StubCommunicationManager manager = new StubCommunicationManager();
        final Context context = new Context(
                new FileStorage(),
                manager,
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
        try {
            final ServerPlacement placement = new ServerPlacement(
                    UUID.randomUUID(),
                    "build",
                    UUID.randomUUID(),
                    PlayerIdentifier.MISSING_PLAYER
            );
            placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
            context.getSyncmaticManager().addPlacement(placement);

            final CapturingTarget target = new CapturingTarget();
            target.setFeatureSet(context.getFeatureSet());
            target.setProtocolFlavor(ProtocolFlavor.NEW);
            final TestHandshake handshake = new TestHandshake(target, context);
            final PacketByteBuf empty = new PacketByteBuf(Unpooled.buffer());

            assertFalse(handshake.checkPacket(PacketType.FEATURE.toIdentifier(ProtocolFlavor.NEW), empty));
            assertTrue(handshake.checkPacket(PacketType.FEATURE_REQUEST.toIdentifier(ProtocolFlavor.NEW), empty));

            handshake.sendInitialStateForTest();

            assertEquals(PacketType.REGISTER_METADATA.toIdentifier(ProtocolFlavor.NEW), target.ids.get(0));
            assertEquals(PacketType.CONFIRM_USER.toIdentifier(ProtocolFlavor.NEW), target.ids.get(1));
            final PacketByteBuf confirmation = new PacketByteBuf(Unpooled.wrappedBuffer(target.payloads.get(1)));
            assertEquals(0, confirmation.readInt());
        } finally {
            context.shutdown();
        }
    }

    private static final class TestHandshake extends VersionHandshakeServer {
        private TestHandshake(final ExchangeTarget partner, final Context context) {
            super(partner, context);
        }

        private void sendInitialStateForTest() {
            sendInitialState();
        }
    }

    @Test
    void warnsReforgedClientWithoutNamedStockingAreas() {
        final Context context = newServerContext();
        try {
            final CapturingTarget target = new CapturingTarget();
            target.setProtocolFlavor(ProtocolFlavor.NEW);
            final VersionHandshakeServer handshake = new VersionHandshakeServer(target, context);

            completeHandshake(handshake, "CORE\nMESSAGE\nSTOCKING_AREA_SETUP");

            final int messageIndex = target.ids.indexOf(PacketType.MESSAGE.toIdentifier(ProtocolFlavor.NEW));
            assertTrue(messageIndex >= 0);
            final PacketByteBuf payload =
                    new PacketByteBuf(Unpooled.wrappedBuffer(target.payloads.get(messageIndex)));
            assertEquals("WARNING", MessageCodec.readType(payload).toString());
            final String text = MessageCodec.readIdentifier(payload);
            assertTrue(text.contains("命名备货区"));
            assertTrue(text.contains("named stocking areas"));
            assertTrue(text.contains("0.4.2"));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void staysSilentForClientAdvertisingNamedStockingAreas() {
        final Context context = newServerContext();
        try {
            final CapturingTarget target = new CapturingTarget();
            target.setProtocolFlavor(ProtocolFlavor.NEW);
            final VersionHandshakeServer handshake = new VersionHandshakeServer(target, context);

            completeHandshake(handshake, "CORE\nMESSAGE\nSTOCKING_AREA_SETUP\nNAMED_STOCKING_AREAS");

            assertFalse(target.ids.contains(PacketType.MESSAGE.toIdentifier(ProtocolFlavor.NEW)));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void warnsLegacyClientBilingually() {
        final Context context = newServerContext();
        try {
            final CapturingTarget target = new CapturingTarget();
            target.setProtocolFlavor(ProtocolFlavor.LEGACY);
            final VersionHandshakeServer handshake = new VersionHandshakeServer(target, context);

            handshake.handle(
                    PacketType.REGISTER_VERSION.toIdentifier(ProtocolFlavor.LEGACY),
                    stringBuf("0.3.6", ProtocolLimits.MAX_VERSION_LENGTH)
            );
            handshake.handle(
                    PacketType.FEATURE.toIdentifier(ProtocolFlavor.LEGACY),
                    stringBuf("CORE\nFEATURE\nMODIFY\nMESSAGE", ProtocolLimits.MAX_FEATURE_STRING_LENGTH)
            );

            final int messageIndex = target.ids.indexOf(PacketType.MESSAGE.toIdentifier(ProtocolFlavor.LEGACY));
            assertTrue(messageIndex >= 0);
            final PacketByteBuf payload =
                    new PacketByteBuf(Unpooled.wrappedBuffer(target.payloads.get(messageIndex)));
            assertEquals("WARNING", MessageCodec.readType(payload).toString());
            final String text = MessageCodec.readIdentifier(payload);
            assertTrue(text.contains("原版 Syncmatica"));
            assertTrue(text.contains("Reforged"));
        } finally {
            context.shutdown();
        }
    }

    private Context newServerContext() {
        return new Context(
                new FileStorage(),
                new StubServerCommunicationManager(),
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
    }

    private static void completeHandshake(final VersionHandshakeServer handshake, final String clientFeatures) {
        handshake.handle(
                PacketType.REGISTER_VERSION.toIdentifier(ProtocolFlavor.NEW),
                stringBuf("0.4.2", ProtocolLimits.MAX_VERSION_LENGTH)
        );
        handshake.handle(
                PacketType.FEATURE.toIdentifier(ProtocolFlavor.NEW),
                stringBuf(clientFeatures, ProtocolLimits.MAX_FEATURE_STRING_LENGTH)
        );
    }

    private static PacketByteBuf stringBuf(final String value, final int maxLength) {
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(value, maxLength);
        return buf;
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final cn.net.rms.syncmatica_r.communication.exchange.Exchange exchange) {
        }
    }

    private static final class StubServerCommunicationManager extends ServerCommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final cn.net.rms.syncmatica_r.communication.exchange.Exchange exchange) {
        }
    }

    private static final class CapturingTarget extends ExchangeTarget {
        private final List<Identifier> ids = new ArrayList<>();
        private final List<byte[]> payloads = new ArrayList<>();

        private CapturingTarget() {
            super("player");
        }

        @Override
        public void sendPacket(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
            final byte[] payload = new byte[packetBuf.readableBytes()];
            packetBuf.getBytes(packetBuf.readerIndex(), payload);
            ids.add(id);
            payloads.add(payload);
        }
    }
}
