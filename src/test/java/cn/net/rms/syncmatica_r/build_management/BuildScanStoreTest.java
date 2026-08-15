package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildScanStoreTest {

    @TempDir
    Path worldFolder;

    private static final RegionBounds BOUNDS =
            new RegionBounds(new BlockPos(0, 64, 0), new BlockPos(31, 70, 31));

    @Test
    void countsSurviveARoundTripThroughTheWorldFolder() {
        final UUID placementId = UUID.randomUUID();
        final BuildScanStore store = new BuildScanStore(worldFolder.toFile());

        final BuildRegionState saved = state("roof");
        final RegionScanCache cache = new RegionScanCache(BOUNDS);
        cache.record(0, 0, 40);
        cache.record(1, 1, 20);
        saved.get("roof").setScanCache(cache);
        saved.get("roof").recordScan(cache.getTotal(), 4_242L);
        store.save(placementId, saved);

        final BuildRegionState loaded = state("roof");
        store.load(placementId, loaded);

        final RegionScanCache restored = loaded.get("roof").getScanCache();
        assertNotNull(restored);
        assertEquals(60L, restored.getTotal());
        assertTrue(restored.isCounted(0, 0));
        assertTrue(restored.isCounted(1, 1));
        assertFalse(restored.isCounted(1, 0), "a column nobody reached stays unknown");
        assertTrue(restored.matches(BOUNDS));
    }

    @Test
    void theTotalIsRebuiltFromTheColumnsRatherThanTakenOnTrust() {
        final UUID placementId = UUID.randomUUID();
        final BuildScanStore store = new BuildScanStore(worldFolder.toFile());

        final BuildRegionState saved = state("roof");
        final RegionScanCache cache = new RegionScanCache(BOUNDS);
        cache.record(0, 0, 25);
        saved.get("roof").setScanCache(cache);
        saved.get("roof").recordScan(cache.getTotal(), 4_242L);
        store.save(placementId, saved);

        // The region the file is read onto claims more progress than the world
        // it was measured in ever had, the way a placement file outlives a
        // rolled-back world.
        final BuildRegionState loaded = state("roof");
        loaded.get("roof").recordScan(90L, 9_999L);
        store.load(placementId, loaded);

        assertEquals(25L, loaded.get("roof").getPlacedBlocks());
        assertEquals(4_242L, loaded.get("roof").getLastScanMillis());
    }

    @Test
    void countsForARegionTheSchematicNoLongerHasAreDropped() {
        final UUID placementId = UUID.randomUUID();
        final BuildScanStore store = new BuildScanStore(worldFolder.toFile());

        final BuildRegionState saved = state("roof", "walls");
        for (final BuildRegion region : saved.getRegions()) {
            final RegionScanCache cache = new RegionScanCache(BOUNDS);
            cache.record(0, 0, 10);
            region.setScanCache(cache);
            region.recordScan(10L, 4_242L);
        }
        store.save(placementId, saved);

        final BuildRegionState loaded = state("roof");
        store.load(placementId, loaded);

        assertNotNull(loaded.get("roof").getScanCache());
        assertNull(loaded.get("walls"), "the region went away and its counts went with it");
    }

    @Test
    void aWorldWithNothingStoredLeavesTheRegionsAlone() {
        final BuildRegionState loaded = state("roof");
        loaded.get("roof").recordScan(30L, 4_242L);

        new BuildScanStore(worldFolder.toFile()).load(UUID.randomUUID(), loaded);

        assertNull(loaded.get("roof").getScanCache());
        assertEquals(30L, loaded.get("roof").getPlacedBlocks(), "nothing to say means nothing to change");
    }

    @Test
    void deletingLeavesNothingToComeBack() {
        final UUID placementId = UUID.randomUUID();
        final BuildScanStore store = new BuildScanStore(worldFolder.toFile());

        final BuildRegionState saved = state("roof");
        final RegionScanCache cache = new RegionScanCache(BOUNDS);
        cache.record(0, 0, 40);
        saved.get("roof").setScanCache(cache);
        store.save(placementId, saved);
        store.delete(placementId);

        final BuildRegionState loaded = state("roof");
        store.load(placementId, loaded);
        assertNull(loaded.get("roof").getScanCache());
    }

    @Test
    void aRegionThatWasNeverCountedIsNotWorthAFile() {
        final UUID placementId = UUID.randomUUID();
        final BuildScanStore store = new BuildScanStore(worldFolder.toFile());

        store.save(placementId, state("roof"));

        final BuildRegionState loaded = state("roof");
        store.load(placementId, loaded);
        assertNull(loaded.get("roof").getScanCache());
    }

    private static BuildRegionState state(final String... regionNames) {
        final BuildRegionState state = new BuildRegionState();
        for (final String regionName : regionNames) {
            state.getOrCreate(regionName, 100L);
        }
        return state;
    }
}
