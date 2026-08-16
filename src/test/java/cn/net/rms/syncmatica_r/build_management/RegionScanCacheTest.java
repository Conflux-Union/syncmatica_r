package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class RegionScanCacheTest {

    @Test
    void columnsCoverEveryChunkTheRegionTouches() {
        // Reaches into the chunks below zero on both axes and past the first one
        // above, so the grid is 3x3 rather than the 2x2 the extents suggest.
        final RegionScanCache cache = cache(new BlockPos(-3, 64, -3), new BlockPos(20, 70, 17));

        assertEquals(9L, cache.getColumnCount());
        final List<String> columns = new ArrayList<>();
        for (final Iterator<Long> iterator = cache.columns(); iterator.hasNext(); ) {
            final long packed = iterator.next();
            columns.add(RegionScanCache.columnX(packed) + "/" + RegionScanCache.columnZ(packed));
        }
        assertEquals(
                List.of("-1/-1", "-1/0", "-1/1", "0/-1", "0/0", "0/1", "1/-1", "1/0", "1/1"),
                columns);
    }

    @Test
    void aColumnIsClippedToTheRegionRatherThanTheChunk() {
        final RegionScanCache cache = cache(new BlockPos(4, 64, 4), new BlockPos(20, 70, 20));

        final RegionBounds inner = cache.columnBounds(0, 0);
        assertEquals(new BlockPos(4, 64, 4), inner.getMin(), "the region corner wins over the chunk corner");
        assertEquals(new BlockPos(15, 70, 15), inner.getMax(), "the chunk edge wins over the region edge");

        final RegionBounds outer = cache.columnBounds(1, 1);
        assertEquals(new BlockPos(16, 64, 16), outer.getMin());
        assertEquals(new BlockPos(20, 70, 20), outer.getMax(), "the region corner wins again");
    }

    @Test
    void theTotalIsTheSumOfWhatEachColumnContributed() {
        final RegionScanCache cache = cache(new BlockPos(0, 64, 0), new BlockPos(31, 70, 31));

        assertEquals(0L, cache.getTotal(), "nothing counted yet is nothing built");
        cache.record(0, 0, 12);
        cache.record(1, 0, 30);
        assertEquals(42L, cache.getTotal());
        assertEquals(2, cache.getCountedColumnCount());
        assertTrue(cache.isCounted(0, 0));
        assertFalse(cache.isCounted(1, 1), "a column nobody has counted stays unknown");
    }

    @Test
    void countingAColumnAgainReplacesItRatherThanAddingToIt() {
        final RegionScanCache cache = cache(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15));

        cache.record(0, 0, 40);
        cache.record(0, 0, 25);
        assertEquals(25L, cache.getTotal(), "blocks can be broken as well as placed");
        assertEquals(1, cache.getCountedColumnCount());
    }

    @Test
    void columnsOutsideTheRegionAreRefused() {
        final RegionScanCache cache = cache(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15));

        cache.record(5, 5, 1_000);
        assertEquals(0L, cache.getTotal(), "a stored file cannot inflate the total from outside the region");
        assertEquals(0, cache.getCountedColumnCount());
        assertNull(cache.columnBounds(5, 5));
    }

    @Test
    void aNegativeCountIsClampedRatherThanTrusted() {
        final RegionScanCache cache = cache(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15));

        cache.record(0, 0, -7);
        assertEquals(0L, cache.getTotal());
        assertTrue(cache.isCounted(0, 0), "it was still counted, just found empty");
    }

    @Test
    void countsBelongToTheBoxTheyWereTakenIn() {
        final RegionBounds bounds = new RegionBounds(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15));
        final RegionScanCache cache = new RegionScanCache(bounds);

        assertTrue(cache.matches(new RegionBounds(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15))));
        assertFalse(cache.matches(new RegionBounds(new BlockPos(1, 64, 0), new BlockPos(16, 70, 15))),
                "a moved placement puts different blocks under the schematic");
    }

    @Test
    void columnKeysSurviveNegativeCoordinates() {
        final long packed = RegionScanCache.packColumn(-2_000, 1_999);

        assertEquals(-2_000, RegionScanCache.columnX(packed));
        assertEquals(1_999, RegionScanCache.columnZ(packed));
    }

    private static RegionScanCache cache(final BlockPos min, final BlockPos max) {
        return new RegionScanCache(new RegionBounds(min, max));
    }
}
