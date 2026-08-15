package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class BuildScanTrackerTest {

    private static final Object OVERWORLD = new Object();
    private static final Object NETHER = new Object();

    @Test
    void aChangeInsideARegionMarksItsColumn() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        assertFalse(tracker.hasWork(placement), "nothing has happened yet");
        tracker.recordChange(OVERWORLD, 20, 35);
        assertTrue(tracker.hasWork(placement));

        final BuildScanTracker.ScanRequest request = tracker.take(placement);
        assertFalse(request.isFullPass());
        assertTrue(request.covers(RegionScanCache.packColumn(1, 2)), "the changed column");
        assertFalse(request.covers(RegionScanCache.packColumn(0, 0)), "a column nothing touched");
        assertFalse(tracker.hasWork(placement), "taking the work clears it");
    }

    @Test
    void aChangeOutsideEveryRegionIsIgnored() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        tracker.recordChange(OVERWORLD, 5000, 5000);
        tracker.recordChange(OVERWORLD, -1, -1);
        assertFalse(tracker.hasWork(placement));
    }

    @Test
    void theSameColumnInAnotherWorldIsAnotherColumn() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        tracker.recordChange(NETHER, 20, 35);
        assertFalse(tracker.hasWork(placement), "the nether copy of those coordinates is not this region");
    }

    /**
     * Two placements sharing ground is unusual but legal, and a change under both
     * belongs to both.
     */
    @Test
    void overlappingPlacementsBothHearAboutAChange() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        tracker.replaceCoverage(Arrays.asList(
                new BuildScanTracker.RegionColumns(first, OVERWORLD, box(0, 0, 47, 47)),
                new BuildScanTracker.RegionColumns(second, OVERWORLD, box(32, 32, 79, 79))));

        tracker.recordChange(OVERWORLD, 40, 40);
        assertTrue(tracker.hasWork(first));
        assertTrue(tracker.hasWork(second));

        tracker.recordChange(OVERWORLD, 70, 70);
        assertTrue(tracker.take(second).covers(RegionScanCache.packColumn(4, 4)));
        assertFalse(tracker.take(first).covers(RegionScanCache.packColumn(4, 4)),
                "the far column is outside the first placement");
    }

    @Test
    void aFullPassCoversEveryColumnWithoutListingThem() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        tracker.requestFullPass();
        assertTrue(tracker.hasWork(placement));

        final BuildScanTracker.ScanRequest request = tracker.take(placement);
        assertTrue(request.isFullPass());
        assertTrue(request.covers(RegionScanCache.packColumn(123, -456)), "a full pass covers anything it is asked");
        assertFalse(tracker.hasWork(placement));
    }

    /**
     * An abandoned scan must not take the report of what changed down with it, or
     * the region silently keeps a stale count until the next sweep.
     */
    @Test
    void workHandedToAScanThatDiedComesBack() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        tracker.recordChange(OVERWORLD, 20, 35);
        tracker.requestFullPass(placement);
        final BuildScanTracker.ScanRequest taken = tracker.take(placement);
        assertFalse(tracker.hasWork(placement));

        tracker.restore(placement, taken);
        assertTrue(tracker.hasWork(placement));
        final BuildScanTracker.ScanRequest again = tracker.take(placement);
        assertTrue(again.isFullPass());
        assertTrue(again.covers(RegionScanCache.packColumn(1, 2)));
    }

    @Test
    void changesArrivingDuringAScanSurviveIt() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        tracker.recordChange(OVERWORLD, 20, 35);
        tracker.take(placement);
        tracker.recordChange(OVERWORLD, 36, 36);

        assertTrue(tracker.hasWork(placement), "the change that arrived mid-scan is still outstanding");
        assertTrue(tracker.take(placement).covers(RegionScanCache.packColumn(2, 2)));
    }

    @Test
    void aForgottenPlacementLeavesNoWorkBehind() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        tracker.replaceCoverage(Collections.singletonList(
                new BuildScanTracker.RegionColumns(placement, OVERWORLD, box(0, 0, 47, 47))));

        tracker.recordChange(OVERWORLD, 20, 35);
        tracker.requestFullPass(placement);
        tracker.forget(placement);
        assertFalse(tracker.hasWork(placement));
    }

    @Test
    void anEmptyRequestAsksForNothing() {
        final BuildScanTracker tracker = new BuildScanTracker();
        final UUID placement = UUID.randomUUID();
        final BuildScanTracker.ScanRequest request = tracker.take(placement);
        assertTrue(request.isEmpty());
        assertFalse(request.covers(RegionScanCache.packColumn(0, 0)));
    }

    private static RegionBounds box(final int minX, final int minZ, final int maxX, final int maxZ) {
        return new RegionBounds(new BlockPos(minX, 0, minZ), new BlockPos(maxX, 64, maxZ));
    }
}
