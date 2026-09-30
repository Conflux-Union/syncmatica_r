package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import cn.net.rms.syncmatica_r.material.StockingAreaRegistry;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.service.MaterialService;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The GUI "from selection" button names its area after the placement, and
 * display names the registry forbids (mainstream zh_cn names, spaces, the
 * reserved default name) must not dead-end that flow: the create retries
 * under a placement-id slug. The slug retry is a pure MaterialService
 * operation, so it is pinned here against a real headless registry, matching
 * the migration's fallback in {@code SyncmaticManager}.
 */
final class StockingAreaSlugFallbackTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path tempDir;

    private Context context;
    private MaterialService materialService;

    @BeforeEach
    void setUpService() {
        context = new Context(
                new FileStorage(),
                new StubManager(),
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.resolve("server").toFile()
        );
        materialService = context.getMaterialService();
    }

    @AfterEach
    void tearDownContext() {
        context.shutdown();
    }

    @Test
    void fallbackCreatesUnderThePlacementSlug() {
        final UUID placementId = UUID.fromString("1a2b3c4d-1111-2222-3333-444444444444");

        final String created = ServerCommunicationManager.createUnderPlacementSlug(
                materialService, smallArea(), OWNER, placementId);

        assertEquals("area-1a2b3c4d", created);
        final StockingAreaRegistry.Entry entry =
                materialService.getStockingAreaRegistry().getByName("area-1a2b3c4d");
        assertNotNull(entry, "the slug-named area must exist");
        assertEquals(OWNER, entry.getOwnerPlayerId());
    }

    @Test
    void takenSlugFallsThroughToASuffixedName() {
        final UUID placementId = UUID.fromString("1a2b3c4d-5555-6666-7777-888888888888");
        assertEquals(StockingAreaRegistry.CreateOutcome.CREATED,
                materialService.createStockingArea("area-1a2b3c4d", smallArea(), OWNER));

        final String created = ServerCommunicationManager.createUnderPlacementSlug(
                materialService, smallArea(), OWNER, placementId);

        assertEquals("area-1a2b3c4d-2", created);
        assertNotNull(materialService.getStockingAreaRegistry().getByName("area-1a2b3c4d-2"));
    }

    @Test
    void oversizedAreaFailsWithoutCreatingAPhantomEntry() {
        final UUID placementId = UUID.fromString("1a2b3c4d-9999-8888-7777-666666666666");
        // 101^3 exceeds the 1,000,000-block default limit, so the retry must
        // report failure instead of leaving a half-created entry behind.
        final StockingAreaDefinition oversized = new StockingAreaDefinition(
                "minecraft:overworld", BlockPos.ORIGIN, new BlockPos(100, 100, 100));

        assertNull(ServerCommunicationManager.createUnderPlacementSlug(
                materialService, oversized, OWNER, placementId));
        assertNull(materialService.getStockingAreaRegistry().getByName("area-1a2b3c4d"));
    }

    private static StockingAreaDefinition smallArea() {
        return new StockingAreaDefinition(
                "minecraft:overworld", BlockPos.ORIGIN, new BlockPos(3, 3, 3));
    }

    private static final class StubManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
