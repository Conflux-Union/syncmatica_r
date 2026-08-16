package cn.net.rms.syncmatica_r.build_management;

import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which chunk columns of which placements have changed since they were last
 * counted.
 *
 * <p>Without this, a completion pass has no idea what moved and re-counts every
 * loaded column of every placement on a timer. That is the whole cost of the
 * feature on a large schematic, and almost all of it is spent confirming that
 * nothing changed. The server already knows every block that changes; it just
 * never told anyone. This is where it tells.
 *
 * <p>The hot path — one call per block change anywhere on the server, including
 * every redstone tick and every flowing water update — is a volatile read, a map
 * lookup and a box test. Placements are checked against a per-world bounding box
 * first, so a change far from any shared schematic costs one comparison and
 * stops.
 *
 * <p>Changes that reach the world without notifying it, such as a bulk editor
 * writing chunk sections directly, go behind this. That is what
 * {@link #requestFullPass()} and the operator's rescan command are for.
 */
public final class BuildScanTracker {

    private static volatile BuildScanTracker active;

    /** Immutable, replaced wholesale, so the hot path never sees a half-built map. */
    private volatile Map<Object, ColumnBox> worldBounds = Collections.emptyMap();
    private volatile List<RegionColumns> regions = Collections.emptyList();
    private final Map<UUID, Set<Long>> changedColumns = new ConcurrentHashMap<>();
    private final Set<UUID> fullPasses = ConcurrentHashMap.newKeySet();

    /**
     * Entry point for the block change mixin. Static because the alternative is
     * walking from the world to the mod context on every block change.
     */
    public static void onBlockChanged(final ServerWorld world, final int blockX, final int blockZ) {
        final BuildScanTracker tracker = active;
        if (tracker != null) {
            tracker.recordChange(world, blockX, blockZ);
        }
    }

    /** Starts feeding this tracker from the mixin, replacing any earlier one. */
    public void install() {
        active = this;
    }

    public void uninstall() {
        if (active == this) {
            active = null;
        }
        worldBounds = Collections.emptyMap();
        regions = Collections.emptyList();
        changedColumns.clear();
        fullPasses.clear();
    }

    /**
     * Notes a block change, if it landed inside a region anyone is tracking.
     *
     * @param world the world's identity, which is all this uses it for
     */
    public void recordChange(final Object world, final int blockX, final int blockZ) {
        final int columnX = blockX >> 4;
        final int columnZ = blockZ >> 4;
        final ColumnBox bounds = worldBounds.get(world);
        if (bounds == null || !bounds.covers(columnX, columnZ)) {
            return;
        }
        final long packed = RegionScanCache.packColumn(columnX, columnZ);
        for (final RegionColumns region : regions) {
            if (region.world == world && region.box.covers(columnX, columnZ)) {
                changedColumns
                        .computeIfAbsent(region.placementId, id -> ConcurrentHashMap.newKeySet())
                        .add(packed);
            }
        }
    }

    /**
     * Replaces what is being watched. Called on a timer rather than wired to
     * every event that can move a placement: rebuilding is cheap, and a coverage
     * map that silently stops matching the placements is not a failure worth
     * risking to save it.
     */
    public void replaceCoverage(final List<RegionColumns> coverage) {
        final List<RegionColumns> copy =
                coverage == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(coverage));
        final Map<Object, ColumnBox> bounds = new HashMap<>();
        for (final RegionColumns region : copy) {
            bounds.merge(region.world, region.box, ColumnBox::union);
        }
        regions = copy;
        worldBounds = Collections.unmodifiableMap(bounds);
    }

    /** @return true when this placement has something worth scanning */
    public boolean hasWork(final UUID placementId) {
        if (fullPasses.contains(placementId)) {
            return true;
        }
        final Set<Long> columns = changedColumns.get(placementId);
        return columns != null && !columns.isEmpty();
    }

    /**
     * Hands the outstanding work to a scan and clears it. Changes arriving while
     * that scan runs accumulate for the next one rather than being lost to it.
     */
    public ScanRequest take(final UUID placementId) {
        final boolean full = fullPasses.remove(placementId);
        final Set<Long> columns = changedColumns.remove(placementId);
        return new ScanRequest(full, columns == null ? Collections.emptySet() : columns);
    }

    /** Puts work back when the scan that took it was abandoned. */
    public void restore(final UUID placementId, final ScanRequest request) {
        if (request == null) {
            return;
        }
        if (request.fullPass) {
            fullPasses.add(placementId);
        }
        if (!request.columns.isEmpty()) {
            changedColumns
                    .computeIfAbsent(placementId, id -> ConcurrentHashMap.newKeySet())
                    .addAll(request.columns);
        }
    }

    /**
     * Queues a pass that re-counts everything, including columns nothing was
     * reported to have touched. The way back from an edit this never saw.
     */
    public void requestFullPass() {
        for (final RegionColumns region : regions) {
            fullPasses.add(region.placementId);
        }
    }

    public void requestFullPass(final UUID placementId) {
        fullPasses.add(placementId);
    }

    public void forget(final UUID placementId) {
        changedColumns.remove(placementId);
        fullPasses.remove(placementId);
    }

    /** One region's footprint, in chunk columns. */
    public static final class RegionColumns {
        private final UUID placementId;
        private final Object world;
        private final ColumnBox box;

        public RegionColumns(final UUID placementId, final Object world, final RegionBounds bounds) {
            this.placementId = placementId;
            this.world = world;
            box = new ColumnBox(
                    bounds.getMin().getX() >> 4, bounds.getMin().getZ() >> 4,
                    bounds.getMax().getX() >> 4, bounds.getMax().getZ() >> 4);
        }
    }

    /** What one scan was asked to look at. */
    public static final class ScanRequest {
        private final boolean fullPass;
        private final Set<Long> columns;

        private ScanRequest(final boolean fullPass, final Set<Long> columns) {
            this.fullPass = fullPass;
            this.columns = columns;
        }

        public boolean isFullPass() {
            return fullPass;
        }

        public boolean isEmpty() {
            return !fullPass && columns.isEmpty();
        }

        public boolean covers(final long packedColumn) {
            return fullPass || columns.contains(packedColumn);
        }
    }

    private static final class ColumnBox {
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;

        private ColumnBox(final int minX, final int minZ, final int maxX, final int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
        }

        private boolean covers(final int columnX, final int columnZ) {
            return columnX >= minX && columnX <= maxX && columnZ >= minZ && columnZ <= maxZ;
        }

        private static ColumnBox union(final ColumnBox left, final ColumnBox right) {
            return new ColumnBox(
                    Math.min(left.minX, right.minX), Math.min(left.minZ, right.minZ),
                    Math.max(left.maxX, right.maxX), Math.max(left.maxZ, right.maxZ));
        }
    }
}
