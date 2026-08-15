package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RegionLayoutExtractorTest {

    private static final long ONE_MEGABYTE = 1024L * 1024L;

    @TempDir
    Path tempDir;

    @Test
    void everyRegionIsListedEvenWhenItNeedsNothing() throws IOException {
        final NbtCompound regions = new NbtCompound();
        regions.put("roof", sizedRegion(4, 3, 2));
        regions.put("walls", sizedRegion(2, 2, 2));

        final Map<String, Long> counts = RegionLayoutExtractor.extractLayout(writeRegions("build.litematic", regions), ONE_MEGABYTE).getBlockCounts();

        // A region with no palette asks for nothing, but it is still somebody's
        // job, so it has to stay claimable.
        assertEquals(2, counts.size());
        assertEquals(0L, counts.get("roof"));
        assertEquals(0L, counts.get("walls"));
    }

    @Test
    void palettesWithoutAMaterialCountAsNoWork() throws IOException {
        final NbtCompound region = sizedRegion(2, 1, 1);
        final NbtList palette = new NbtList();
        palette.add(paletteEntry("", null, null));
        palette.add(paletteEntry("minecraft:oak_door", "half", "upper"));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", new long[]{0b01_00L});
        final NbtCompound regions = new NbtCompound();
        regions.put("roof", region);

        final Map<String, Long> counts = RegionLayoutExtractor.extractLayout(writeRegions("skips.litematic", regions), ONE_MEGABYTE).getBlockCounts();

        // The upper half of a door is placed by its lower half, and a nameless
        // entry is not a block at all. Neither costs a material, so neither is
        // work the completion scan could ever tick off.
        assertEquals(0L, counts.get("roof"));
    }

    /**
     * The block registry is not available to a plain unit test, so the walk is
     * driven with the palette verdict handed in rather than looked up.
     */
    @Test
    void onlyThePositionsWhoseEntryNeedsAMaterialAreCounted() {
        final NbtCompound region = sizedRegion(4, 1, 1);
        // Two palette entries pack at two bits each. Read low bits first:
        // air, stone, stone, air.
        region.putLongArray("BlockStates", new long[]{0b00_01_01_00L});

        assertEquals(2L, RegionLayoutExtractor.countPositions(
                region, new int[]{4, 1, 1}, new boolean[]{false, true}));
    }

    @Test
    void aSingleEntryPaletteCoversTheWholeRegionWithoutAPackedArray() {
        final NbtCompound region = sizedRegion(4, 3, 2);

        assertEquals(24L, RegionLayoutExtractor.countPositions(
                region, new int[]{4, 3, 2}, new boolean[]{true}));
        assertEquals(0L, RegionLayoutExtractor.countPositions(
                region, new int[]{4, 3, 2}, new boolean[]{false}));
    }

    @Test
    void aVolumeThatOverflowsCountsAsNoWorkRatherThanThrowing() {
        final NbtCompound region = sizedRegion(1, 1, 1);

        assertEquals(0L, RegionLayoutExtractor.countPositions(
                region, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
                new boolean[]{true, true}));
    }

    @Test
    void regionsAreCappedAndNamesAreSanitised() throws IOException {
        final NbtCompound regions = new NbtCompound();
        for (int i = 0; i < ProtocolLimits.MAX_REGION_ENTRIES + 10; i++) {
            regions.put("region_" + i, sizedRegion(1, 1, 1));
        }
        regions.put(tooLongName(), sizedRegion(1, 1, 1));

        final Map<String, Long> counts = RegionLayoutExtractor.extractLayout(writeRegions("many.litematic", regions), ONE_MEGABYTE).getBlockCounts();

        assertTrue(counts.size() <= ProtocolLimits.MAX_REGION_ENTRIES);
        assertTrue(counts.keySet().stream()
                .allMatch(name -> !name.isEmpty() && name.length() <= ProtocolLimits.MAX_SUBREGION_NAME_LENGTH));
    }

    @Test
    void unreadableSchematicsYieldNoRegionsRatherThanThrowing() throws IOException {
        final File garbage = new File(tempDir.toFile(), "garbage.litematic");
        try (OutputStream output = new FileOutputStream(garbage)) {
            output.write(new byte[]{1, 2, 3, 4});
        }

        assertTrue(RegionLayoutExtractor.extractLayout(garbage, ONE_MEGABYTE).isEmpty());
        assertTrue(RegionLayoutExtractor
                .extractLayout(new File(tempDir.toFile(), "missing.litematic"), ONE_MEGABYTE).isEmpty());
        assertTrue(RegionLayoutExtractor.extractLayout(null, ONE_MEGABYTE).isEmpty());
        assertTrue(RegionLayoutExtractor
                .extractLayout(writeRegions("empty.litematic", null), ONE_MEGABYTE).isEmpty());
    }

    private static NbtCompound sizedRegion(final int x, final int y, final int z) {
        final NbtCompound region = new NbtCompound();
        region.putIntArray("Position", new int[]{0, 0, 0});
        region.putIntArray("Size", new int[]{x, y, z});
        return region;
    }

    private static NbtCompound paletteEntry(final String name, final String propertyKey, final String propertyValue) {
        final NbtCompound entry = new NbtCompound();
        entry.putString("Name", name);
        if (propertyKey != null) {
            final NbtCompound properties = new NbtCompound();
            properties.putString(propertyKey, propertyValue);
            entry.put("Properties", properties);
        }
        return entry;
    }

    private static String tooLongName() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= ProtocolLimits.MAX_SUBREGION_NAME_LENGTH; i++) {
            builder.append('x');
        }
        return builder.toString();
    }

    private File writeRegions(final String fileName, final NbtCompound regions) throws IOException {
        final NbtCompound root = new NbtCompound();
        if (regions != null) {
            root.put("Regions", regions);
        }
        final File file = new File(tempDir.toFile(), fileName);
        try (OutputStream output = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, output);
        }
        return file;
    }
}
