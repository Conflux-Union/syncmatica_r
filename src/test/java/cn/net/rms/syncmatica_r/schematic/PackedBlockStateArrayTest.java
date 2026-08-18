package cn.net.rms.syncmatica_r.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bit widths asserted here come from Litematica's own
 * {@code LitematicaBlockStateContainer.createFrom}, which packs entries at
 * {@code max(2, 32 - numberOfLeadingZeros(paletteSize - 1))} bits.
 */
final class PackedBlockStateArrayTest {

    @Test
    void bitWidthMatchesLitematica() {
        // The floor of two is the part that used to be missing: a palette of
        // air plus one block is packed at two bits, not one.
        assertEquals(2, PackedBlockStateArray.bitsForPalette(2));
        assertEquals(2, PackedBlockStateArray.bitsForPalette(3));
        assertEquals(2, PackedBlockStateArray.bitsForPalette(4));
        assertEquals(3, PackedBlockStateArray.bitsForPalette(5));
        assertEquals(3, PackedBlockStateArray.bitsForPalette(8));
        assertEquals(4, PackedBlockStateArray.bitsForPalette(9));
        assertEquals(8, PackedBlockStateArray.bitsForPalette(256));
        assertEquals(9, PackedBlockStateArray.bitsForPalette(257));

        // Degenerate palettes still use the minimum width rather than zero.
        assertEquals(2, PackedBlockStateArray.bitsForPalette(1));
        assertEquals(2, PackedBlockStateArray.bitsForPalette(0));
    }

    @Test
    void readsTwoBitEntriesBackInOrder() {
        final int[] values = {0, 1, 0, 1, 1, 0, 1, 1};
        final PackedBlockStateArray packed = new PackedBlockStateArray(pack(values, 2), 2);

        assertEquals(2, packed.getBits());
        for (int index = 0; index < values.length; index++) {
            assertEquals(values[index], packed.get(index), "entry " + index);
        }
    }

    @Test
    void readsEntriesThatStraddleALongBoundary() {
        // 3 bits per entry means entry 21 spans the gap between the two longs.
        final int[] values = new int[40];
        for (int index = 0; index < values.length; index++) {
            values[index] = index % 5;
        }
        final PackedBlockStateArray packed = new PackedBlockStateArray(pack(values, 3), 5);

        assertEquals(3, packed.getBits());
        for (int index = 0; index < values.length; index++) {
            assertEquals(values[index], packed.get(index), "entry " + index);
        }
    }

    @Test
    void readsWideEntries() {
        final int[] values = {0, 300, 511, 7, 256};
        final PackedBlockStateArray packed = new PackedBlockStateArray(pack(values, 9), 512);

        assertEquals(9, packed.getBits());
        for (int index = 0; index < values.length; index++) {
            assertEquals(values[index], packed.get(index), "entry " + index);
        }
    }

    @Test
    void readingPastTheEndReportsNothingRatherThanCrashing() {
        final PackedBlockStateArray packed = new PackedBlockStateArray(pack(new int[]{1, 0}, 2), 2);

        assertEquals(-1, packed.get(1_000_000L));
        assertTrue(new PackedBlockStateArray(new long[0], 2).isEmpty());
        assertEquals(-1, new PackedBlockStateArray(null, 2).get(0));
    }

    @Test
    void regionIndexIsYMajorThenZThenX() {
        assertEquals(0L, PackedBlockStateArray.indexOf(0, 0, 0, 4, 2));
        assertEquals(3L, PackedBlockStateArray.indexOf(3, 0, 0, 4, 2));
        assertEquals(4L, PackedBlockStateArray.indexOf(0, 0, 1, 4, 2));
        assertEquals(8L, PackedBlockStateArray.indexOf(0, 1, 0, 4, 2));
        assertEquals(15L, PackedBlockStateArray.indexOf(3, 1, 1, 4, 2));
    }

    /** Packs entries the way Litematica's bit array stores them. */
    private static long[] pack(final int[] values, final int bits) {
        final long totalBits = (long) values.length * bits;
        final long[] data = new long[(int) ((totalBits + 63) / 64)];
        for (int index = 0; index < values.length; index++) {
            final long bitIndex = (long) index * bits;
            final int arrayIndex = (int) (bitIndex >>> 6);
            final int bitOffset = (int) (bitIndex & 63L);
            final long value = values[index] & ((1L << bits) - 1L);
            data[arrayIndex] |= value << bitOffset;
            if (bitOffset + bits > 64 && arrayIndex + 1 < data.length) {
                data[arrayIndex + 1] |= value >>> (64 - bitOffset);
            }
        }
        return data;
    }
}
