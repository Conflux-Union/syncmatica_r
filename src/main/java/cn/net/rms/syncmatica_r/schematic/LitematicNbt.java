package cn.net.rms.syncmatica_r.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
//#if MC >= 12005
//$$ import net.minecraft.nbt.NbtSizeTracker;
//#else
import net.minecraft.nbt.NbtTagSizeTracker;
//#endif

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Reads the parts of a litematic file whose NBT shape changes between Minecraft
 * versions.
 *
 * <p>Everything here exists once so the preprocessor blocks do too. Callers that
 * decode a schematic — the material list and the build progress scan — differ in
 * what they do with a region, not in how they open one.
 */
public final class LitematicNbt {

    private LitematicNbt() {
    }

    /**
     * @param maxNbtBytes ceiling the decoder refuses to read past, so a crafted
     *                    file cannot exhaust the heap
     */
    public static NbtCompound readRoot(final File litematicFile, final long maxNbtBytes) throws IOException {
        try (InputStream input = new FileInputStream(litematicFile)) {
//#if MC >= 12005
//$$             return NbtIo.readCompressed(input, NbtSizeTracker.of(maxNbtBytes));
//#else
            try (DataInputStream dataInput = new DataInputStream(
                    new BufferedInputStream(new GZIPInputStream(input)))) {
                return NbtIo.read(dataInput, new NbtTagSizeTracker(maxNbtBytes));
            }
//#endif
        }
    }

    /**
     * @return {@code {sizeX, sizeY, sizeZ}} as positive extents, or null when the
     *         region declares no usable size. The sign is dropped because callers
     *         that only count blocks do not care which way a region grows.
     */
    public static int[] resolveSize(final NbtCompound region) {
        //#if MC >= 12106
        //$$ if (region.contains("Size")) {
        //$$     final Optional<int[]> intArrayOpt = region.getIntArray("Size");
        //$$     if (intArrayOpt.isPresent()) {
        //$$         final int[] raw = intArrayOpt.get();
        //$$         if (raw.length >= 3) {
        //$$             return normalizeDimensions(raw[0], raw[1], raw[2]);
        //$$         }
        //$$         return null;
        //$$     }
        //$$     final Optional<NbtCompound> compoundOpt = region.getCompound("Size");
        //$$     if (compoundOpt.isPresent()) {
        //$$         final NbtCompound compound = compoundOpt.get();
        //$$         if (compound.contains("x") && compound.contains("y") && compound.contains("z")) {
        //$$             return normalizeDimensions(
        //$$                     compound.getInt("x", 0),
        //$$                     compound.getInt("y", 0),
        //$$                     compound.getInt("z", 0)
        //$$             );
        //$$         }
        //$$     }
        //$$ }
        //$$ return null;
        //#else
        if (region.contains("Size", NbtElement.INT_ARRAY_TYPE)) {
            final int[] raw = region.getIntArray("Size");
            if (raw.length >= 3) {
                return normalizeDimensions(raw[0], raw[1], raw[2]);
            }
            return null;
        }
        if (region.contains("Size", NbtElement.COMPOUND_TYPE)) {
            final NbtCompound compound = region.getCompound("Size");
            if (compound.contains("x", NbtElement.INT_TYPE)
                    && compound.contains("y", NbtElement.INT_TYPE)
                    && compound.contains("z", NbtElement.INT_TYPE)) {
                return normalizeDimensions(
                        compound.getInt("x"),
                        compound.getInt("y"),
                        compound.getInt("z")
                );
            }
        }
        return null;
        //#endif
    }

    /**
     * @return the packed palette indices of a region, or an empty array when the
     *         region stores none
     */
    public static long[] resolveBlockStates(final NbtCompound region) {
        //#if MC >= 12106
        //$$ if (region.contains("BlockStates")) {
        //$$     final Optional<long[]> longArrayOpt = region.getLongArray("BlockStates");
        //$$     if (longArrayOpt.isPresent()) {
        //$$         return longArrayOpt.get();
        //$$     }
        //$$     final Optional<int[]> intArrayOpt = region.getIntArray("BlockStates");
        //$$     if (intArrayOpt.isPresent()) {
        //$$         final int[] ints = intArrayOpt.get();
        //$$         final long[] longs = new long[ints.length];
        //$$         for (int index = 0; index < ints.length; index++) {
        //$$             longs[index] = ints[index] & 0xFFFFFFFFL;
        //$$         }
        //$$         return longs;
        //$$     }
        //$$ }
        //$$ return new long[0];
        //#else
        if (region.contains("BlockStates", NbtElement.LONG_ARRAY_TYPE)) {
            return region.getLongArray("BlockStates");
        }
        if (region.contains("BlockStates", NbtElement.INT_ARRAY_TYPE)) {
            final int[] ints = region.getIntArray("BlockStates");
            final long[] longs = new long[ints.length];
            for (int index = 0; index < ints.length; index++) {
                longs[index] = ints[index] & 0xFFFFFFFFL;
            }
            return longs;
        }
        return new long[0];
        //#endif
    }

    private static int[] normalizeDimensions(final int x, final int y, final int z) {
        final int sizeX = safeAbs(x);
        final int sizeY = safeAbs(y);
        final int sizeZ = safeAbs(z);
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return null;
        }
        return new int[]{sizeX, sizeY, sizeZ};
    }

    private static int safeAbs(final int value) {
        return value == Integer.MIN_VALUE ? -1 : Math.abs(value);
    }
}
