package ch.endte.syncmatica.material;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MaterialRequirementExtractor {

    private static final Logger LOGGER = LogManager.getLogger(MaterialRequirementExtractor.class);

    private MaterialRequirementExtractor() {
    }

    public static Map<MaterialKey, Integer> extract(final File litematicFile) {
        return extract(litematicFile, false);
    }

    public static Map<MaterialKey, Integer> extract(final File litematicFile, final boolean includeContainerContents) {
        return extract(litematicFile, includeContainerContents, Long.MAX_VALUE);
    }

    public static Map<MaterialKey, Integer> extract(final File litematicFile,
                                                    final boolean includeContainerContents,
                                                    final long maxBlocks) {
        final Map<MaterialKey, Integer> requirements = new HashMap<>();
        if (litematicFile == null || !litematicFile.isFile()) {
            return requirements;
        }
        try (InputStream input = new FileInputStream(litematicFile)) {
            final NbtCompound root = NbtIo.readCompressed(input);
            if (root == null || !root.contains("Regions", NbtElement.COMPOUND_TYPE)) {
                return requirements;
            }
            final NbtCompound regions = root.getCompound("Regions");
            long processedBlocks = 0L;
            final long effectiveLimit = (maxBlocks <= 0L) ? Long.MAX_VALUE : maxBlocks;
            for (final String regionName : regions.getKeys()) {
                final NbtCompound region = regions.getCompound(regionName);
                processedBlocks = accumulateRegion(region, requirements, includeContainerContents,
                        processedBlocks, effectiveLimit);
            }
        } catch (final IOException exception) {
            LOGGER.warn("Failed to read material requirements from {}", litematicFile, exception);
        } catch (final ExtractionLimitExceededException limitExceededException) {
            LOGGER.warn("Aborted material extraction for {} after {} blocks exceeded limit {}",
                    litematicFile,
                    limitExceededException.getSeenBlocks(),
                    maxBlocks);
        } catch (final Exception exception) {
            LOGGER.warn("Failed to parse material requirements from {}", litematicFile, exception);
        }
        return requirements;
    }

    private static long accumulateRegion(final NbtCompound region, final Map<MaterialKey, Integer> totals,
                                         final boolean includeContainerContents,
                                         final long processedBlocks,
                                         final long maxBlocks) {
        if (region == null) {
            return processedBlocks;
        }
        final int[] dimensions = resolveSize(region);
        if (dimensions == null) {
            return processedBlocks;
        }
        final long volume = (long) dimensions[0] * dimensions[1] * dimensions[2];
        if (volume <= 0) {
            return processedBlocks;
        }
        final long updatedBlocks = processedBlocks + volume;
        if (updatedBlocks > maxBlocks) {
            throw new ExtractionLimitExceededException(updatedBlocks);
        }
        final NbtList paletteData = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
        if (paletteData.isEmpty()) {
            return updatedBlocks;
        }
        final List<MaterialKey> palette = buildPalette(paletteData);
        if (palette.isEmpty()) {
            return updatedBlocks;
        }
        final long[] blockStates = resolveBlockStates(region);
        if (palette.size() == 1) {
            final MaterialKey key = palette.get(0);
            if (key != null) {
                totals.merge(key, (int) volume, Integer::sum);
            }
        } else if (blockStates.length != 0) {
            final int bits = bitsForPalette(palette.size());
            final long mask = bits >= Long.SIZE ? -1L : (1L << bits) - 1L;
            for (long index = 0; index < volume; index++) {
                final int bitIndex = (int) (index * bits);
                final int arrayIndex = bitIndex >>> 6;
                final int bitOffset = bitIndex & 63;
                long value = 0L;
                if (arrayIndex < blockStates.length) {
                    value = blockStates[arrayIndex] >>> bitOffset;
                }
                if (bitOffset + bits > Long.SIZE && arrayIndex + 1 < blockStates.length) {
                    value |= blockStates[arrayIndex + 1] << (Long.SIZE - bitOffset);
                }
                final int paletteIndex = (int) (value & mask);
                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    continue;
                }
                final MaterialKey key = palette.get(paletteIndex);
                if (key != null) {
                    totals.merge(key, 1, Integer::sum);
                }
            }
        }
        if (includeContainerContents && region.contains("TileEntities", NbtElement.LIST_TYPE)) {
            final NbtList tileEntities = region.getList("TileEntities", NbtElement.COMPOUND_TYPE);
            if (!tileEntities.isEmpty()) {
                accumulateTileEntityContents(tileEntities, totals);
            }
        }
        return updatedBlocks;
    }

    private static List<MaterialKey> buildPalette(final NbtList paletteData) {
        final List<MaterialKey> palette = new ArrayList<>(paletteData.size());
        for (int index = 0; index < paletteData.size(); index++) {
            final NbtCompound entry = paletteData.getCompound(index);
            palette.add(resolvePaletteEntry(entry));
        }
        return palette;
    }

    private static MaterialKey resolvePaletteEntry(final NbtCompound entry) {
        if (entry == null || !entry.contains("Name", NbtElement.STRING_TYPE)) {
            return null;
        }
        final String name = entry.getString("Name");
        if (name.isEmpty()) {
            return null;
        }
        final Identifier blockId = new Identifier(name);
        final Block block = Registry.BLOCK.getOrEmpty(blockId).orElse(Blocks.AIR);
        if (block == Blocks.AIR) {
            return null;
        }
        final Item item = block.asItem();
        if (item == Items.AIR) {
            return null;
        }
        final Identifier itemId = Registry.ITEM.getId(item);
        if (itemId == null) {
            return null;
        }
        return new MaterialKey(itemId, "");
    }

    private static int bitsForPalette(final int paletteSize) {
        if (paletteSize <= 1) {
            return 1;
        }
        return 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
    }

    private static int[] resolveSize(final NbtCompound region) {
        if (region.contains("Size", NbtElement.INT_ARRAY_TYPE)) {
            final int[] raw = region.getIntArray("Size");
            if (raw.length >= 3) {
                return new int[]{Math.abs(raw[0]), Math.abs(raw[1]), Math.abs(raw[2])};
            }
            return null;
        }
        if (region.contains("Size", NbtElement.COMPOUND_TYPE)) {
            final NbtCompound compound = region.getCompound("Size");
            if (compound.contains("x", NbtElement.INT_TYPE)
                    && compound.contains("y", NbtElement.INT_TYPE)
                    && compound.contains("z", NbtElement.INT_TYPE)) {
                final int sizeX = Math.abs(compound.getInt("x"));
                final int sizeY = Math.abs(compound.getInt("y"));
                final int sizeZ = Math.abs(compound.getInt("z"));
                if (sizeX == 0 || sizeY == 0 || sizeZ == 0) {
                    return null;
                }
                return new int[]{sizeX, sizeY, sizeZ};
            }
        }
        return null;
    }

    private static long[] resolveBlockStates(final NbtCompound region) {
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
    }

    private static void accumulateTileEntityContents(final NbtList entities, final Map<MaterialKey, Integer> totals) {
        for (int i = 0; i < entities.size(); i++) {
            final NbtCompound entity = entities.getCompound(i);
            if (entity == null || !entity.contains("Items", NbtElement.LIST_TYPE)) {
                continue;
            }
            final NbtList items = entity.getList("Items", NbtElement.COMPOUND_TYPE);
            accumulateItemList(items, totals);
        }
    }

    private static void accumulateItemList(final NbtList items, final Map<MaterialKey, Integer> totals) {
        for (int i = 0; i < items.size(); i++) {
            final NbtCompound item = items.getCompound(i);
            accumulateItemEntry(item, totals);
        }
    }

    private static void accumulateItemEntry(final NbtCompound item, final Map<MaterialKey, Integer> totals) {
        if (item == null || !item.contains("id", NbtElement.STRING_TYPE) || !item.contains("Count", NbtElement.NUMBER_TYPE)) {
            return;
        }
        final String id = item.getString("id");
        if (id.isEmpty()) {
            return;
        }
        final Identifier identifier;
        try {
            identifier = new Identifier(id);
        } catch (final Exception ignored) {
            return;
        }
        final int count = Byte.toUnsignedInt(item.getByte("Count"));
        if (count <= 0) {
            return;
        }
        totals.merge(new MaterialKey(identifier, ""), count, Integer::sum);
        if (item.contains("tag", NbtElement.COMPOUND_TYPE)) {
            final NbtCompound tag = item.getCompound("tag");
            accumulateNestedBlockEntity(tag, totals);
        }
    }

    private static void accumulateNestedBlockEntity(final NbtCompound tag, final Map<MaterialKey, Integer> totals) {
        if (tag == null || !tag.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) {
            return;
        }
        final NbtCompound blockEntityTag = tag.getCompound("BlockEntityTag");
        if (!blockEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
            return;
        }
        final NbtList nestedItems = blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE);
        accumulateItemList(nestedItems, totals);
    }

    private static final class ExtractionLimitExceededException extends RuntimeException {
        private final long seenBlocks;

        ExtractionLimitExceededException(final long seenBlocks) {
            super("Block limit exceeded");
            this.seenBlocks = seenBlocks;
        }

        long getSeenBlocks() {
            return seenBlocks;
        }
    }
}
