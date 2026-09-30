package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import cn.net.rms.syncmatica_r.material.StockingAreaRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StockingAreaMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesEmbeddedAreasAndLegacyDefaultOnLoad() throws IOException {
        // Integrated server contexts keep the placement store under
        // <world>/syncmatica_r/placement_store; ServerPosition reads the
        // {"position":[...],"dimension":...} shape its toJson writes.
        final Path store = tempDir.resolve("syncmatica_r").resolve("placement_store");
        Files.createDirectories(store);
        final String placementId = "00000000-0000-0000-0000-000000000001";
        Files.writeString(store.resolve(placementId + ".placement.json"),
                "{\"id\":\"" + placementId + "\","
                        + "\"file_name\":\"castle\",\"display_name\":\"castle\","
                        + "\"hash\":\"00000000-0000-0000-0000-000000000002\","
                        + "\"origin\":{\"position\":[0,64,0],\"dimension\":\"minecraft:overworld\"},"
                        + "\"rotation\":\"NONE\",\"mirror\":\"NONE\","
                        + "\"stockingArea\":{\"dimension\":\"minecraft:overworld\","
                        + "\"minX\":0,\"minY\":64,\"minZ\":0,\"maxX\":9,\"maxY\":70,\"maxZ\":9}}",
                StandardCharsets.UTF_8);
        Files.writeString(store.resolve("meta.json"),
                "{\"defaultStockingArea\":{\"dimension\":\"minecraft:overworld\","
                        + "\"minX\":100,\"minY\":64,\"minZ\":100,\"maxX\":109,\"maxY\":70,\"maxZ\":109}}",
                StandardCharsets.UTF_8);

        final SyncmaticManager manager = new SyncmaticManager();
        final Context context = new Context(
                new FileStorage(), new StubCommunicationManager(), manager,
                true, tempDir.resolve("litematics").toFile(), true, tempDir.toFile());
        try {
            manager.startup();

            final StockingAreaRegistry registry = context.getMaterialService().getStockingAreaRegistry();
            assertNotNull(registry.getDefaultArea());
            assertEquals(100, registry.getDefaultArea().getDefinition().getMin().getX());
            final StockingAreaRegistry.Entry migrated = registry.getByName("castle");
            assertNotNull(migrated, "legacy embedded area must be registered under the placement name");
            final ServerPlacement placement = manager.getPlacement(UUID.fromString(placementId));
            assertEquals(migrated.getId(), placement.getStockingAreaRef());
            assertNull(placement.getLegacyStockingArea());
            assertTrue(context.getMaterialService().hasBoundStockingArea(placement));

            manager.saveServerState();
            final JsonObject meta = new JsonParser().parse(
                    Files.readString(store.resolve("meta.json"), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(meta.has(SyncmaticManager.STOCKING_AREAS_JSON_KEY));
            assertTrue(meta.getAsJsonObject(SyncmaticManager.STOCKING_AREAS_JSON_KEY).has("areas"));
            assertTrue(!meta.has("defaultStockingArea"));
            final JsonObject placementJson = new JsonParser().parse(
                    Files.readString(store.resolve(placementId + ".placement.json"), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(placementJson.has("stockingAreaRef"));
            assertTrue(!placementJson.has("stockingArea"));

            // Second load is a no-op migration: same registry size, ref still valid.
            final int sizeBefore = registry.getAll().size();
            manager.startup();
            assertEquals(sizeBefore, context.getMaterialService().getStockingAreaRegistry().getAll().size());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void resolveFallsBackToDefaultOnDanglingRef() {
        final SyncmaticManager manager = new SyncmaticManager();
        final Context context = new Context(
                new FileStorage(), new StubCommunicationManager(), manager,
                true, tempDir.resolve("litematics2").toFile(), true, tempDir.toFile());
        try {
            final StockingAreaDefinition fallback = new StockingAreaDefinition(
                    "minecraft:overworld", new BlockPos(0, 64, 0), new BlockPos(9, 64, 9));
            context.getMaterialService().createStockingArea(
                    StockingAreaRegistry.RESERVED_DEFAULT_NAME, fallback, null);
            final ServerPlacement placement = new ServerPlacement(
                    UUID.randomUUID(), "castle", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
            placement.setStockingAreaRef(UUID.randomUUID());
            assertEquals(fallback, context.getMaterialService().resolveStockingArea(placement));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void migratesAreasWhosePlacementNamesAreInvalidRegistryNames() throws IOException {
        final Path store = tempDir.resolve("syncmatica_r").resolve("placement_store");
        Files.createDirectories(store);
        final String chineseId = "11111111-1111-1111-1111-111111111111";
        final String defaultId = "22222222-2222-2222-2222-222222222222";
        Files.writeString(store.resolve(chineseId + ".placement.json"),
                legacyPlacementJson(chineseId, "chinese", "我的城堡"), StandardCharsets.UTF_8);
        Files.writeString(store.resolve(defaultId + ".placement.json"),
                legacyPlacementJson(defaultId, "default", null), StandardCharsets.UTF_8);

        final SyncmaticManager manager = new SyncmaticManager();
        final Context context = new Context(
                new FileStorage(), new StubCommunicationManager(), manager,
                true, tempDir.resolve("litematics3").toFile(), true, tempDir.toFile());
        try {
            manager.startup();
            final cn.net.rms.syncmatica_r.service.MaterialService service = context.getMaterialService();
            final StockingAreaRegistry registry = service.getStockingAreaRegistry();

            // Non-ASCII display name and the reserved name both fail the direct
            // create; the areas must survive under placement-id slugs instead.
            final StockingAreaRegistry.Entry chineseEntry = registry.getByName("area-11111111");
            assertNotNull(chineseEntry, "non-ASCII placement name must migrate under a slug");
            final StockingAreaRegistry.Entry defaultEntry = registry.getByName("area-22222222");
            assertNotNull(defaultEntry, "placement named 'default' must migrate under a slug");
            assertNull(registry.getByName("我的城堡"));
            assertNull(registry.getDefaultArea(), "owner-carrying placement must not become the default");

            final ServerPlacement chinese = manager.getPlacement(UUID.fromString(chineseId));
            assertEquals(chineseEntry.getId(), chinese.getStockingAreaRef());
            assertNull(chinese.getLegacyStockingArea());
            assertTrue(service.hasBoundStockingArea(chinese));
            assertEquals(chineseEntry.getDefinition(), service.resolveStockingArea(chinese));

            final ServerPlacement namedDefault = manager.getPlacement(UUID.fromString(defaultId));
            assertEquals(defaultEntry.getId(), namedDefault.getStockingAreaRef());
            assertNull(namedDefault.getLegacyStockingArea());
            assertEquals(defaultEntry.getDefinition(), service.resolveStockingArea(namedDefault));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void updateAndDeleteStockingAreasGuardReferencingPlacements() {
        final SyncmaticManager manager = new SyncmaticManager();
        final Context context = new Context(
                new FileStorage(), new StubCommunicationManager(), manager,
                true, tempDir.resolve("litematics4").toFile(), true, tempDir.toFile());
        try {
            final cn.net.rms.syncmatica_r.service.MaterialService service = context.getMaterialService();
            final ServerPlacement placement = new ServerPlacement(
                    UUID.randomUUID(), "castle", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
            placement.move("minecraft:overworld", BlockPos.ORIGIN,
                    net.minecraft.util.BlockRotation.NONE, net.minecraft.util.BlockMirror.NONE);
            manager.addPlacement(placement);

            final StockingAreaDefinition yard = new StockingAreaDefinition(
                    "minecraft:overworld", new BlockPos(0, 64, 0), new BlockPos(9, 64, 9));
            assertEquals(StockingAreaRegistry.CreateOutcome.CREATED,
                    service.createStockingArea("yard", yard, null));
            final UUID yardId = service.getStockingAreaRegistry().getByName("yard").getId();
            assertEquals(cn.net.rms.syncmatica_r.service.MaterialService.StockingAreaBindOutcome.BOUND,
                    service.bindStockingArea(placement, yardId));

            assertEquals(cn.net.rms.syncmatica_r.service.MaterialService.StockingAreaDeleteOutcome.IN_USE,
                    service.deleteStockingArea(yardId, false));
            assertNotNull(service.getStockingAreaRegistry().getById(yardId));

            final StockingAreaDefinition moved = new StockingAreaDefinition(
                    "minecraft:overworld", new BlockPos(0, 64, 0), new BlockPos(19, 64, 19));
            assertEquals(StockingAreaRegistry.UpdateOutcome.UPDATED,
                    service.updateStockingArea(yardId, moved));
            assertEquals(moved, service.resolveStockingArea(placement));
            assertEquals(StockingAreaRegistry.UpdateOutcome.NOT_FOUND,
                    service.updateStockingArea(UUID.randomUUID(), moved));
            assertEquals(StockingAreaRegistry.UpdateOutcome.TOO_LARGE,
                    service.updateStockingArea(yardId, new StockingAreaDefinition(
                            "minecraft:overworld",
                            new BlockPos(Integer.MIN_VALUE, 0, 0),
                            new BlockPos(Integer.MAX_VALUE, 0, 0))));

            assertEquals(cn.net.rms.syncmatica_r.service.MaterialService.StockingAreaDeleteOutcome.DELETED,
                    service.deleteStockingArea(yardId, true));
            assertNull(placement.getStockingAreaRef());
            assertNull(service.getStockingAreaRegistry().getById(yardId));
            assertFalse(service.hasBoundStockingArea(placement));
            assertNull(service.resolveStockingArea(placement));
        } finally {
            context.shutdown();
        }
    }

    private static String legacyPlacementJson(final String id, final String fileName, final String displayName) {
        final StringBuilder json = new StringBuilder();
        json.append("{\"id\":\"").append(id).append("\",")
                .append("\"file_name\":\"").append(fileName).append("\",");
        if (displayName != null) {
            json.append("\"display_name\":\"").append(displayName).append("\",");
        }
        json.append("\"hash\":\"00000000-0000-0000-0000-00000000000f\",")
                .append("\"origin\":{\"position\":[0,64,0],\"dimension\":\"minecraft:overworld\"},")
                .append("\"rotation\":\"NONE\",\"mirror\":\"NONE\",")
                .append("\"stockingArea\":{\"dimension\":\"minecraft:overworld\",")
                .append("\"minX\":0,\"minY\":64,\"minZ\":0,\"maxX\":9,\"maxY\":70,\"maxZ\":9}}");
        return json.toString();
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
