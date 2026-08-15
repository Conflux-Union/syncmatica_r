package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BuildRegionTest {

    @Test
    void anUnscannedRegionReportsNoProgressRatherThanZero() {
        final BuildRegion region = new BuildRegion("roof", 100L);

        // Zero percent and "not measured yet" mean different things to whoever
        // signed up for the region, so they must not render the same.
        assertFalse(region.isScanned());
        assertFalse(region.isComplete());
        assertEquals(-1, region.getCompletionPercent());
        assertEquals(0L, region.getPlacedBlocks());
    }

    @Test
    void progressIsReportedOnceAScanHasRun() {
        final BuildRegion region = new BuildRegion("roof", 200L);
        region.recordScan(50L, 1_000L);

        assertTrue(region.isScanned());
        assertFalse(region.isComplete());
        assertEquals(25, region.getCompletionPercent());
        assertEquals(1_000L, region.getLastScanMillis());
    }

    @Test
    void aScanCannotReportMoreProgressThanThereIsWork() {
        final BuildRegion region = new BuildRegion("roof", 10L);
        region.recordScan(999L, 1_000L);

        assertEquals(10L, region.getPlacedBlocks());
        assertEquals(100, region.getCompletionPercent());
        assertTrue(region.isComplete());

        region.recordScan(-5L, 2_000L);
        assertEquals(0L, region.getPlacedBlocks());
    }

    @Test
    void aRegionThatNeedsNothingIsCompleteOnceScanned() {
        final BuildRegion region = new BuildRegion("air_pocket", 0L);
        assertEquals(-1, region.getCompletionPercent());

        region.recordScan(0L, 1_000L);
        assertEquals(100, region.getCompletionPercent());
        assertTrue(region.isComplete());
    }

    @Test
    void aNegativeRequirementIsClampedRatherThanTrusted() {
        assertEquals(0L, new BuildRegion("roof", -5L).getRequiredBlocks());
    }
}
