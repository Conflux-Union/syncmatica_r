package cn.net.rms.syncmatica_r.schematic;

/**
 * Reads the packed palette indices a litematic region stores in its
 * {@code BlockStates} long array.
 *
 * <p>The bit width has to match what Litematica wrote, which is
 * {@code max(2, ceil(log2(paletteSize)))} — see
 * {@code LitematicaBlockStateContainer.createFrom}. The floor of two matters:
 * a two-entry palette (air plus one block) is the most common shape there is,
 * and reading it one bit at a time yields garbage.
 */
public final class PackedBlockStateArray {

    private final long[] data;
    private final int bits;
    private final long mask;

    public PackedBlockStateArray(final long[] data, final int paletteSize) {
        this.data = data == null ? new long[0] : data;
        this.bits = bitsForPalette(paletteSize);
        this.mask = this.bits >= Long.SIZE ? -1L : (1L << this.bits) - 1L;
    }

    public static int bitsForPalette(final int paletteSize) {
        if (paletteSize <= 1) {
            return 2;
        }
        return Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    public int getBits() {
        return bits;
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    /**
     * @return the palette index stored at {@code index}, or -1 when the array is
     *         shorter than the region claims
     */
    public int get(final long index) {
        final long bitIndex = index * bits;
        final long arrayIndexLong = bitIndex >>> 6;
        if (arrayIndexLong > Integer.MAX_VALUE) {
            return -1;
        }
        final int arrayIndex = (int) arrayIndexLong;
        if (arrayIndex >= data.length) {
            return -1;
        }
        final int bitOffset = (int) (bitIndex & 63L);
        long value = data[arrayIndex] >>> bitOffset;
        if (bitOffset + bits > Long.SIZE && arrayIndex + 1 < data.length) {
            value |= data[arrayIndex + 1] << (Long.SIZE - bitOffset);
        }
        return (int) (value & mask);
    }

    /** Litematica lays regions out y-major, then z, then x. */
    public static long indexOf(final int x, final int y, final int z, final int sizeX, final int sizeZ) {
        return (long) y * sizeX * sizeZ + (long) z * sizeX + x;
    }
}
