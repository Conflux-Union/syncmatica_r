package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LiveFeatureReloadTest {
    @TempDir
    Path tempDir;

    @Test
    void changingFeatureSwitchInvalidatesCacheAndStartsFreshHandshake() throws Exception {
        final TestServerCommunicationManager manager = new TestServerCommunicationManager();
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
            context.startup();
            final CapturingTarget client = new CapturingTarget();
            client.setFeatureSet(context.getFeatureSet());
            manager.addOnlineClient(client);
            final FeatureSet original = context.getFeatureSet();

            context.getConfigStore().set("materials", "enabled", "false");

            assertNotSame(original, context.getFeatureSet());
            assertFalse(context.getFeatureSet().hasFeature(Feature.MATERIAL_PROGRESS));
            assertTrue(client.ids.contains(
                    PacketType.REGISTER_VERSION.toIdentifier(ProtocolFlavor.LEGACY)));
        } finally {
            context.shutdown();
        }
    }

    private static final class TestServerCommunicationManager extends ServerCommunicationManager {
        private void addOnlineClient(final ExchangeTarget target) {
            broadcastTargets.add(target);
        }
    }

    private static final class CapturingTarget extends ExchangeTarget {
        private final List<Identifier> ids = new ArrayList<>();

        private CapturingTarget() {
            super("player");
        }

        @Override
        public void sendPacket(final Identifier id, final PacketByteBuf packetBuf, final Context context) {
            ids.add(id);
        }
    }
}
