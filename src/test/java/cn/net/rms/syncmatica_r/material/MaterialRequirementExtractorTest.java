package cn.net.rms.syncmatica_r.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class MaterialRequirementExtractorTest {

    private static final long ONE_MEGABYTE = 1024L * 1024L;

    @TempDir
    Path tempDir;

    @Test
    void reportsBlockLimitWhenSchematicVolumeExceedsTheConfiguredMaximum() throws IOException {
        final File file = writeSingleRegion("huge.litematic", 100, 100, 100);

        final MaterialRequirementExtractor.ExtractionOutcome outcome =
                MaterialRequirementExtractor.extractDetailed(file, false, 10L, ONE_MEGABYTE);

        assertEquals(MaterialAvailability.TOO_MANY_BLOCKS, outcome.getAvailability());
        assertTrue(outcome.getRequirements().isEmpty());
    }

    @Test
    void reportsExtractionFailureForUnreadableSchematics() throws IOException {
        final File garbage = new File(tempDir.toFile(), "garbage.litematic");
        try (OutputStream output = new FileOutputStream(garbage)) {
            output.write(new byte[]{1, 2, 3, 4});
        }

        assertEquals(MaterialAvailability.EXTRACTION_FAILED,
                MaterialRequirementExtractor.extractDetailed(garbage, false, 1_000L, ONE_MEGABYTE)
                        .getAvailability());
        assertEquals(MaterialAvailability.EXTRACTION_FAILED,
                MaterialRequirementExtractor.extractDetailed(new File(tempDir.toFile(), "missing.litematic"),
                        false, 1_000L, ONE_MEGABYTE).getAvailability());
        assertEquals(MaterialAvailability.EXTRACTION_FAILED,
                MaterialRequirementExtractor.extractDetailed(writeCompound("empty.litematic", new NbtCompound()),
                        false, 1_000L, ONE_MEGABYTE).getAvailability());
    }

    @Test
    void reportsAvailableWhenTheSchematicFitsWithinTheLimits() throws IOException {
        final File file = writeSingleRegion("small.litematic", 2, 2, 2);

        assertEquals(MaterialAvailability.AVAILABLE,
                MaterialRequirementExtractor.extractDetailed(file, false, 1_000L, ONE_MEGABYTE)
                        .getAvailability());
    }

    @Test
    void includesContainerContentsStoredInTheModernItemStackFormat() throws IOException {
        final File file = writeContainerSchematic("modern-container.litematic", true, 12);

        final Map<MaterialKey, Integer> withoutContents =
                MaterialRequirementExtractor.extract(file, false, 10L, ONE_MEGABYTE);
        final Map<MaterialKey, Integer> withContents =
                MaterialRequirementExtractor.extract(file, true, 10L, ONE_MEGABYTE);

        assertEquals(0, count(withoutContents, "minecraft:diamond"));
        assertEquals(12, count(withContents, "minecraft:diamond"));
    }

    @Test
    void keepsSupportingContainerContentsStoredInTheLegacyItemStackFormat() throws IOException {
        final File file = writeContainerSchematic("legacy-container.litematic", false, 12);

        final Map<MaterialKey, Integer> withContents =
                MaterialRequirementExtractor.extract(file, true, 10L, ONE_MEGABYTE);

        assertEquals(12, count(withContents, "minecraft:diamond"));
    }

    @Test
    void wireCodesStayStableAcrossReorderings() {
        for (final MaterialAvailability availability : MaterialAvailability.values()) {
            assertEquals(availability, MaterialAvailability.fromCode(availability.getCode()));
        }
        assertEquals(MaterialAvailability.AVAILABLE, MaterialAvailability.fromCode(-1));
        assertEquals(MaterialAvailability.AVAILABLE, MaterialAvailability.fromCode(99));
    }

    /**
     * A region with only a size is enough to exercise the volume accounting; the
     * palette is deliberately absent so the extractor never touches the block
     * registry.
     */
    private File writeSingleRegion(final String fileName, final int x, final int y, final int z) throws IOException {
        final NbtCompound region = new NbtCompound();
        region.putIntArray("Size", new int[]{x, y, z});
        final NbtCompound regions = new NbtCompound();
        regions.put("region", region);
        final NbtCompound root = new NbtCompound();
        root.put("Regions", regions);
        return writeCompound(fileName, root);
    }

    private File writeContainerSchematic(final String fileName, final boolean modernFormat, final int count)
            throws IOException {
        final NbtCompound paletteEntry = new NbtCompound();
        final NbtList palette = new NbtList();
        palette.add(paletteEntry);

        final NbtCompound item = new NbtCompound();
        item.putString("id", "minecraft:diamond");
        if (modernFormat) {
            item.putInt("count", count);
        } else {
            item.putByte("Count", (byte) count);
        }
        final NbtList items = new NbtList();
        items.add(item);

        final NbtCompound container = new NbtCompound();
        container.put("Items", items);
        final NbtList tileEntities = new NbtList();
        tileEntities.add(container);

        final NbtCompound region = new NbtCompound();
        region.putIntArray("Size", new int[]{1, 1, 1});
        region.put("BlockStatePalette", palette);
        region.put("TileEntities", tileEntities);
        final NbtCompound regions = new NbtCompound();
        regions.put("region", region);
        final NbtCompound root = new NbtCompound();
        root.put("Regions", regions);
        return writeCompound(fileName, root);
    }

    private int count(final Map<MaterialKey, Integer> requirements, final String itemId) {
        return requirements.entrySet().stream()
                .filter(entry -> entry.getKey().itemId().toString().equals(itemId))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private File writeCompound(final String fileName, final NbtCompound root) throws IOException {
        final File file = new File(tempDir.toFile(), fileName);
        try (OutputStream output = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, output);
        }
        return file;
    }
}
