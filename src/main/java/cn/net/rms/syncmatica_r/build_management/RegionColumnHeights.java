package cn.net.rms.syncmatica_r.build_management;

import net.minecraft.util.Identifier;

import java.util.Arrays;

/**
 * The lowest and highest layer that holds anything, per schematic column of one
 * sub-region.
 *
 * <p>A litematic region is a box, and most boxes are mostly air: the space above
 * a roof and below a foundation is stored, counted in the region's volume, and
 * then found empty by every scan that walks it. This says where the walking can
 * start and stop, one schematic column at a time, so those layers are never
 * visited at all.
 *
 * <p>Emptiness is decided from the palette identifier rather than the block
 * registry, because this is measured off the server thread alongside the decode.
 * That makes it slightly more generous than the rule the scan itself applies —
 * a block with no item form counts as present here — and generous is the safe
 * direction: the span may be wider than it needs to be, never narrower.
 */
public final class RegionColumnHeights {

    /**
     * Refuse to index regions wider than this in the horizontal plane. The
     * arrays cost eight bytes per column, and a region large enough to matter
     * here is one where the scan is dominated by loaded-chunk work anyway.
     */
    private static final int MAX_COLUMNS = 512 * 512;
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String AIR_PATH = "air";
    private static final String CAVE_AIR_PATH = "cave_air";
    private static final String VOID_AIR_PATH = "void_air";

    private final int sizeX;
    private final int sizeZ;
    /** Local Y of the lowest occupied layer, or -1 for a column of pure air. */
    private final int[] lowest;
    private final int[] highest;

    private RegionColumnHeights(final int sizeX, final int sizeZ, final int[] lowest, final int[] highest) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.lowest = lowest;
        this.highest = highest;
    }

    /**
     * Walks the region once, in the order it is stored, and records where each
     * column starts and ends.
     *
     * @return the index, or null when the region is too wide to index
     */
    public static RegionColumnHeights measure(final RegionBlocks blocks) {
        if (blocks == null) {
            return null;
        }
        final int sizeX = blocks.getSizeX();
        final int sizeY = blocks.getSizeY();
        final int sizeZ = blocks.getSizeZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || (long) sizeX * sizeZ > MAX_COLUMNS) {
            return null;
        }
        final boolean[] empty = emptyPaletteEntries(blocks.getPalette());
        final int columns = sizeX * sizeZ;
        final int[] lowest = new int[columns];
        final int[] highest = new int[columns];
        Arrays.fill(lowest, -1);
        Arrays.fill(highest, -1);

        // Ascending Y, so the first hit in a column is its floor and the last
        // one to overwrite is its ceiling.
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    final int paletteIndex = blocks.paletteIndexAt(x, y, z);
                    if (paletteIndex < 0 || empty[paletteIndex]) {
                        continue;
                    }
                    final int column = x * sizeZ + z;
                    if (lowest[column] < 0) {
                        lowest[column] = y;
                    }
                    highest[column] = y;
                }
            }
        }
        return new RegionColumnHeights(sizeX, sizeZ, lowest, highest);
    }

    private static boolean[] emptyPaletteEntries(final Identifier[] palette) {
        final boolean[] empty = new boolean[palette.length];
        for (int index = 0; index < palette.length; index++) {
            empty[index] = isAir(palette[index]);
        }
        return empty;
    }

    private static boolean isAir(final Identifier id) {
        if (id == null) {
            return true;
        }
        if (!MINECRAFT_NAMESPACE.equals(id.getNamespace())) {
            return false;
        }
        final String path = id.getPath();
        return AIR_PATH.equals(path) || CAVE_AIR_PATH.equals(path) || VOID_AIR_PATH.equals(path);
    }

    /** @return roughly what this index costs to keep, for cache budgeting */
    public long getStoredBytes() {
        return ((long) lowest.length + highest.length) * Integer.BYTES;
    }

    /**
     * Reduces a rectangle of schematic columns to the layers worth visiting.
     *
     * @param span filled with the lowest and highest occupied local Y when this
     *             returns true, and left alone otherwise
     * @return false when every column in the rectangle is pure air, or the
     *         rectangle lies outside the region entirely
     */
    public boolean occupiedSpan(final int fromX, final int fromZ, final int toX, final int toZ, final int[] span) {
        final int startX = Math.max(0, Math.min(fromX, toX));
        final int endX = Math.min(sizeX - 1, Math.max(fromX, toX));
        final int startZ = Math.max(0, Math.min(fromZ, toZ));
        final int endZ = Math.min(sizeZ - 1, Math.max(fromZ, toZ));
        int low = Integer.MAX_VALUE;
        int high = -1;
        for (int x = startX; x <= endX; x++) {
            final int row = x * sizeZ;
            for (int z = startZ; z <= endZ; z++) {
                final int columnLow = lowest[row + z];
                if (columnLow < 0) {
                    continue;
                }
                if (columnLow < low) {
                    low = columnLow;
                }
                final int columnHigh = highest[row + z];
                if (columnHigh > high) {
                    high = columnHigh;
                }
            }
        }
        if (high < 0) {
            return false;
        }
        span[0] = low;
        span[1] = high;
        return true;
    }
}
