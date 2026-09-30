package cn.net.rms.syncmatica_r.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class StockingAreaRegistryTest {
    private static final long LIMIT = 1_000_000L;

    private static StockingAreaDefinition area(final int maxX) {
        return new StockingAreaDefinition("minecraft:overworld",
                new BlockPos(0, 64, 0), new BlockPos(maxX, 64, 0));
    }

    @Test
    void createRejectsInvalidDuplicateAndReservedNames() {
        final StockingAreaRegistry registry = new StockingAreaRegistry();
        assertEquals(StockingAreaRegistry.CreateOutcome.CREATED,
                registry.create("warehouse", area(9), UUID.randomUUID(), LIMIT));
        assertEquals(StockingAreaRegistry.CreateOutcome.DUPLICATE_NAME,
                registry.create("warehouse", area(9), UUID.randomUUID(), LIMIT));
        assertEquals(StockingAreaRegistry.CreateOutcome.RESERVED_NAME,
                registry.create("default", area(9), UUID.randomUUID(), LIMIT));
        assertEquals(StockingAreaRegistry.CreateOutcome.INVALID_NAME,
                registry.create("has space", area(9), UUID.randomUUID(), LIMIT));
        assertEquals(StockingAreaRegistry.CreateOutcome.INVALID_NAME,
                registry.create("", area(9), UUID.randomUUID(), LIMIT));
        assertEquals(StockingAreaRegistry.CreateOutcome.INVALID_NAME,
                registry.create("a".repeat(33), area(9), UUID.randomUUID(), LIMIT));
        assertEquals(StockingAreaRegistry.CreateOutcome.TOO_LARGE,
                registry.create("big", area(Integer.MAX_VALUE), UUID.randomUUID(), LIMIT));
    }

    @Test
    void updateValidatesSizeAndUnknownId() {
        final StockingAreaRegistry registry = new StockingAreaRegistry();
        registry.create("warehouse", area(9), UUID.randomUUID(), LIMIT);
        final UUID id = registry.getByName("warehouse").getId();
        assertEquals(StockingAreaRegistry.UpdateOutcome.UPDATED, registry.update(id, area(19), LIMIT));
        assertEquals(19, registry.getById(id).getDefinition().getMax().getX());
        assertEquals(StockingAreaRegistry.UpdateOutcome.TOO_LARGE,
                registry.update(id, area(Integer.MAX_VALUE), LIMIT));
        assertEquals(StockingAreaRegistry.UpdateOutcome.NOT_FOUND,
                registry.update(UUID.randomUUID(), area(9), LIMIT));
    }

    @Test
    void deleteRefusesReservedDefaultName() {
        final StockingAreaRegistry registry = StockingAreaRegistry.fromJson(
                metaWithDefault(), LIMIT);
        final UUID defaultId = registry.getDefaultArea().getId();
        assertEquals(StockingAreaRegistry.DeleteOutcome.RESERVED_NAME, registry.delete(defaultId));
        assertEquals(StockingAreaRegistry.DeleteOutcome.NOT_FOUND, registry.delete(UUID.randomUUID()));
        registry.create("warehouse", area(9), UUID.randomUUID(), LIMIT);
        assertEquals(StockingAreaRegistry.DeleteOutcome.DELETED,
                registry.delete(registry.getByName("warehouse").getId()));
        assertNull(registry.getByName("warehouse"));
    }

    @Test
    void findUniqueIdForAppendsNumericSuffixes() {
        final StockingAreaRegistry registry = new StockingAreaRegistry();
        assertNull(registry.findUniqueIdFor("castle"));
        registry.create("castle", area(9), null, LIMIT);
        assertEquals("castle-2", registry.findUniqueIdFor("castle"));
        registry.create("castle-2", area(9), null, LIMIT);
        assertEquals("castle-3", registry.findUniqueIdFor("castle"));
    }

    @Test
    void jsonRoundTripKeepsOrderOwnerAndDefaultLookup() {
        final StockingAreaRegistry registry = new StockingAreaRegistry();
        final UUID owner = UUID.randomUUID();
        registry.create(StockingAreaRegistry.RESERVED_DEFAULT_NAME, area(4), null, LIMIT);
        registry.create("warehouse", area(9), owner, LIMIT);
        final JsonObject json = registry.toJson();
        final StockingAreaRegistry loaded = StockingAreaRegistry.fromJson(json, LIMIT);
        assertNotNull(loaded.getDefaultArea());
        assertEquals(owner, loaded.getByName("warehouse").getOwnerPlayerId());
        assertNull(loaded.getByName(StockingAreaRegistry.RESERVED_DEFAULT_NAME).getOwnerPlayerId());
        assertEquals(2, loaded.getAll().size());
    }

    @Test
    void fromJsonSkipsDuplicateNames() {
        final UUID first = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        final UUID second = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        final String duplicated = "{\"areas\":["
                + areaJson(first.toString(), "warehouse") + "," + areaJson(second.toString(), "warehouse") + "]}";
        final StockingAreaRegistry registry = StockingAreaRegistry.fromJson(
                new JsonParser().parse(duplicated).getAsJsonObject(), LIMIT);
        assertEquals(1, registry.getAll().size());
        assertEquals(first, registry.getByName("warehouse").getId());
    }

    private static String areaJson(final String id, final String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\","
                + "\"dimension\":\"minecraft:overworld\","
                + "\"minX\":0,\"minY\":64,\"minZ\":0,\"maxX\":9,\"maxY\":64,\"maxZ\":9}";
    }

    private static JsonObject metaWithDefault() {
        return new JsonParser().parse("{\"areas\":["
                + areaJson("cccccccc-cccc-cccc-cccc-cccccccccccc", "default") + "]}")
                .getAsJsonObject();
    }
}
