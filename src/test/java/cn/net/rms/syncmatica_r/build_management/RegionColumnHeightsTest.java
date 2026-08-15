package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.schematic.PackedBlockStateArray;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class RegionColumnHeightsTest {

    private static final int SIZE_X = 4;
    private static final int SIZE_Y = 8;
    private static final int SIZE_Z = 3;

    @Test
    void aColumnReportsTheLayersItActuallyFills() {
        final int[] indices = new int[SIZE_X * SIZE_Y * SIZE_Z];
        set(indices, 1, 2, 0, 1);
        set(indices, 1, 5, 0, 1);
        final RegionColumnHeights heights = measure(indices);
        assertNotNull(heights);

        final int[] span = new int[2];
        assertTrue(heights.occupiedSpan(1, 0, 1, 0, span));
        assertEquals(2, span[0]);
        assertEquals(5, span[1]);
    }

    @Test
    void aColumnOfPureAirReportsNothing() {
        final RegionColumnHeights heights = measure(new int[SIZE_X * SIZE_Y * SIZE_Z]);
        assertNotNull(heights);
        assertFalse(heights.occupiedSpan(0, 0, SIZE_X - 1, SIZE_Z - 1, new int[2]));
    }

    @Test
    void aRectangleTakesTheUnionOfItsColumns() {
        final int[] indices = new int[SIZE_X * SIZE_Y * SIZE_Z];
        set(indices, 0, 1, 0, 1);
        set(indices, 3, 6, 2, 1);
        final RegionColumnHeights heights = measure(indices);
        assertNotNull(heights);

        final int[] span = new int[2];
        assertTrue(heights.occupiedSpan(0, 0, SIZE_X - 1, SIZE_Z - 1, span));
        assertEquals(1, span[0]);
        assertEquals(6, span[1]);

        // The far column on its own must not inherit the near one's floor.
        assertTrue(heights.occupiedSpan(3, 2, 3, 2, span));
        assertEquals(6, span[0]);
        assertEquals(6, span[1]);
    }

    @Test
    void aRectangleReachingOutsideTheRegionIsClamped() {
        final int[] indices = new int[SIZE_X * SIZE_Y * SIZE_Z];
        set(indices, 0, 4, 0, 1);
        final RegionColumnHeights heights = measure(indices);
        assertNotNull(heights);

        final int[] span = new int[2];
        assertTrue(heights.occupiedSpan(-8, -8, SIZE_X + 8, SIZE_Z + 8, span));
        assertEquals(4, span[0]);
        assertEquals(4, span[1]);
        assertFalse(heights.occupiedSpan(SIZE_X + 1, SIZE_Z + 1, SIZE_X + 4, SIZE_Z + 4, new int[2]),
                "a rectangle entirely outside the region holds nothing");
    }

    /** Cave and void air are stored like blocks and are still nothing to build. */
    @Test
    void everyFormOfAirCountsAsEmpty() {
        final Identifier[] palette = {
                IdentifierUtil.require("minecraft:air"),
                IdentifierUtil.require("minecraft:cave_air"),
                IdentifierUtil.require("minecraft:void_air"),
                IdentifierUtil.require("minecraft:stone")};
        final int[] indices = new int[SIZE_X * SIZE_Y * SIZE_Z];
        set(indices, 0, 1, 0, 1);
        set(indices, 0, 2, 0, 2);
        set(indices, 0, 7, 0, 3);

        final RegionBlocks blocks = new RegionBlocks(
                new BlockPos(SIZE_X, SIZE_Y, SIZE_Z), palette, pack(indices, palette.length));
        blocks.measureColumnHeights();
        final RegionColumnHeights heights = blocks.getColumnHeights();
        assertNotNull(heights);

        final int[] span = new int[2];
        assertTrue(heights.occupiedSpan(0, 0, 0, 0, span));
        assertEquals(7, span[0], "only the stone layer is occupied");
        assertEquals(7, span[1]);
    }

    @Test
    void aRegionTooWideToIndexIsSkippedRatherThanAllocatedFor() {
        final Identifier[] palette = {
                IdentifierUtil.require("minecraft:air"), IdentifierUtil.require("minecraft:stone")};
        final RegionBlocks blocks = new RegionBlocks(
                new BlockPos(1024, 1, 1024), palette, new PackedBlockStateArray(new long[0], palette.length));
        blocks.measureColumnHeights();
        assertNull(blocks.getColumnHeights());
    }

    private static void set(final int[] indices, final int x, final int y, final int z, final int value) {
        indices[(int) PackedBlockStateArray.indexOf(x, y, z, SIZE_X, SIZE_Z)] = value;
    }

    private static RegionColumnHeights measure(final int[] indices) {
        final Identifier[] palette = {
                IdentifierUtil.require("minecraft:air"), IdentifierUtil.require("minecraft:stone")};
        final RegionBlocks blocks = new RegionBlocks(
                new BlockPos(SIZE_X, SIZE_Y, SIZE_Z), palette, pack(indices, palette.length));
        blocks.measureColumnHeights();
        return blocks.getColumnHeights();
    }

    /** Writes palette indices the way Litematica packs them, bit width and all. */
    private static PackedBlockStateArray pack(final int[] indices, final int paletteSize) {
        final int bits = PackedBlockStateArray.bitsForPalette(paletteSize);
        final long[] data = new long[(int) (((long) indices.length * bits + 63L) / 64L)];
        for (int index = 0; index < indices.length; index++) {
            final long bitIndex = (long) index * bits;
            final int arrayIndex = (int) (bitIndex >>> 6);
            final int offset = (int) (bitIndex & 63L);
            data[arrayIndex] |= (long) indices[index] << offset;
            if (offset + bits > Long.SIZE) {
                data[arrayIndex + 1] |= (long) indices[index] >>> (Long.SIZE - offset);
            }
        }
        return new PackedBlockStateArray(data, paletteSize);
    }
}
