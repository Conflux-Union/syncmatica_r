package cn.net.rms.syncmatica_r.build_management;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * How many blocks of one sub-region are already built, counted per chunk column.
 *
 * <p>Resting on a single invariant: a block cannot change inside an unloaded
 * chunk. A column counted while it was loaded therefore stays exact for as long
 * as nobody loads it again, so a later pass can keep that number instead of
 * discarding the whole region because one corner happened to be out of view.
 * That distinction decides whether the feature works at all — the builds this
 * exists for have regions far larger than the area a player keeps loaded, and a
 * region that has to be loaded all at once never gets measured even once.
 *
 * <p>The counts describe one resolved box. Moving the placement or the
 * sub-region puts different world blocks under the schematic, so a cache whose
 * bounds no longer match is thrown away rather than migrated.
 */
public final class RegionScanCache {

    private final RegionBounds bounds;
    private final int minColumnX;
    private final int minColumnZ;
    private final int maxColumnX;
    private final int maxColumnZ;
    /** Insertion ordered so a stored file keeps a stable shape between saves. */
    private final Map<Long, Integer> counts = new LinkedHashMap<>();
    private long total;

    public RegionScanCache(final RegionBounds bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        minColumnX = bounds.getMin().getX() >> 4;
        minColumnZ = bounds.getMin().getZ() >> 4;
        maxColumnX = bounds.getMax().getX() >> 4;
        maxColumnZ = bounds.getMax().getZ() >> 4;
    }

    public RegionBounds getBounds() {
        return bounds;
    }

    /** @return true when these counts still describe the box the caller is scanning */
    public boolean matches(final RegionBounds candidate) {
        return bounds.equals(candidate);
    }

    /** @return every chunk column the region touches */
    public Iterator<Long> columns() {
        return new ColumnIterator();
    }

    public long getColumnCount() {
        return ((long) maxColumnX - minColumnX + 1L) * ((long) maxColumnZ - minColumnZ + 1L);
    }

    public int getCountedColumnCount() {
        return counts.size();
    }

    /** @return the counts so far, keyed by {@link #packColumn}, for storage */
    public Map<Long, Integer> getCounts() {
        return Collections.unmodifiableMap(counts);
    }

    public boolean isCounted(final int columnX, final int columnZ) {
        return counts.containsKey(packColumn(columnX, columnZ));
    }

    /**
     * Replaces what this column contributes. Columns outside the region are
     * rejected so a stored file cannot inflate the total with positions the
     * region never covered.
     */
    public void record(final int columnX, final int columnZ, final int matched) {
        if (!covers(columnX, columnZ)) {
            return;
        }
        final Integer previous = counts.put(packColumn(columnX, columnZ), Math.max(0, matched));
        total += Math.max(0, matched) - (previous == null ? 0 : previous);
    }

    /**
     * @return blocks placed across the whole region. Columns nobody has counted
     *         yet contribute nothing, which is the same answer as a column that
     *         was counted and found empty.
     */
    public long getTotal() {
        return total;
    }

    /**
     * @return the part of the region inside one chunk column, or null when the
     *         column lies outside the region
     */
    public RegionBounds columnBounds(final int columnX, final int columnZ) {
        if (!covers(columnX, columnZ)) {
            return null;
        }
        final BlockPos min = bounds.getMin();
        final BlockPos max = bounds.getMax();
        return new RegionBounds(
                new BlockPos(Math.max(min.getX(), columnX << 4), min.getY(), Math.max(min.getZ(), columnZ << 4)),
                new BlockPos(Math.min(max.getX(), (columnX << 4) + 15), max.getY(),
                        Math.min(max.getZ(), (columnZ << 4) + 15))
        );
    }

    private boolean covers(final int columnX, final int columnZ) {
        return columnX >= minColumnX && columnX <= maxColumnX
                && columnZ >= minColumnZ && columnZ <= maxColumnZ;
    }

    public static long packColumn(final int columnX, final int columnZ) {
        return ((long) columnX << 32) | (columnZ & 0xFFFFFFFFL);
    }

    public static int columnX(final long packed) {
        return (int) (packed >> 32);
    }

    public static int columnZ(final long packed) {
        return (int) packed;
    }

    private final class ColumnIterator implements Iterator<Long> {
        private int columnX = minColumnX;
        private int columnZ = minColumnZ;

        @Override
        public boolean hasNext() {
            return columnX <= maxColumnX;
        }

        @Override
        public Long next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final long packed = packColumn(columnX, columnZ);
            if (++columnZ > maxColumnZ) {
                columnZ = minColumnZ;
                columnX++;
            }
            return packed;
        }
    }
}
