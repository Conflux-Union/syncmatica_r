package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.schematic.LitematicNbt;
import cn.net.rms.syncmatica_r.schematic.PackedBlockStateArray;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import cn.net.rms.syncmatica_r.util.NbtHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads which sub-regions a shared schematic is divided into and how much work
 * each of them is.
 *
 * <p>Build management reads the litematic itself rather than riding along with
 * the material extractor. The two features answer different questions about the
 * same file and are switched on independently, so neither should stop working
 * because the other is off. What they do share is the decoding underneath, in
 * {@link LitematicNbt} and {@link PackedBlockStateArray}.
 *
 * <p>A position counts as work when the material list would ask for an item to
 * fill it. The skip rules below therefore have to stay in step with
 * {@code MaterialRequirementExtractor.resolvePaletteEntry}; a region that
 * counted positions the material list ignores would report a completion the
 * builder can never reach.
 */
public final class RegionLayoutExtractor {

    private static final Logger LOGGER = LogManager.getLogger(RegionLayoutExtractor.class);

    private RegionLayoutExtractor() {
    }

    /**
     * @return the number of block positions needing a material, per region name,
     *         or an empty map when the file cannot be read. A region with no such
     *         positions is still reported, at zero, so an all-air region stays
     *         claimable.
     */
    public static Map<String, Long> extractBlockCounts(final File litematicFile, final long maxNbtBytes) {
        final Map<String, Long> counts = new LinkedHashMap<>();
        if (litematicFile == null || !litematicFile.isFile() || maxNbtBytes <= 0L) {
            return counts;
        }
        try {
            final NbtCompound regions =
                    NbtHelper.getCompound(LitematicNbt.readRoot(litematicFile, maxNbtBytes), "Regions");
            if (regions == null) {
                LOGGER.warn("Region layout extraction found no regions in {}", litematicFile);
                return counts;
            }
            for (final String regionName : regions.getKeys()) {
                if (counts.size() >= ProtocolLimits.MAX_REGION_ENTRIES) {
                    LOGGER.warn("Ignoring regions past {} in {}", ProtocolLimits.MAX_REGION_ENTRIES, litematicFile);
                    break;
                }
                if (regionName.isEmpty() || regionName.length() > ProtocolLimits.MAX_SUBREGION_NAME_LENGTH) {
                    continue;
                }
                final NbtCompound region = NbtHelper.getCompound(regions, regionName);
                if (region == null) {
                    continue;
                }
                final int[] size = LitematicNbt.resolveSize(region);
                if (size == null) {
                    continue;
                }
                counts.put(regionName, countRegion(region, size));
            }
        } catch (final Exception exception) {
            LOGGER.warn("Failed to read the region layout of {}", litematicFile, exception);
            counts.clear();
        }
        return counts;
    }

    private static long countRegion(final NbtCompound region, final int[] size) {
        final NbtList paletteData = NbtHelper.getList(region, "BlockStatePalette");
        if (paletteData == null || paletteData.isEmpty()) {
            return 0L;
        }
        return countPositions(region, size, resolvePalette(paletteData));
    }

    /**
     * Counts the positions of a region whose palette entry asks for a material.
     *
     * <p>Split from the palette resolution above so the walk can be exercised
     * without a block registry: deciding whether an entry needs a material is the
     * only part that needs one.
     *
     * @param needsMaterial one flag per palette index, whose length is also the
     *                      palette size the packing width is derived from
     */
    static long countPositions(final NbtCompound region, final int[] size, final boolean[] needsMaterial) {
        final long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact((long) size[0], size[1]), size[2]);
        } catch (final ArithmeticException exception) {
            // A region this large cannot be built, let alone scanned.
            return 0L;
        }
        if (needsMaterial.length == 0) {
            return 0L;
        }
        if (needsMaterial.length == 1) {
            // A single-entry palette needs no packed array: every position is it.
            return needsMaterial[0] ? volume : 0L;
        }
        final long[] blockStates = LitematicNbt.resolveBlockStates(region);
        if (blockStates.length == 0) {
            return 0L;
        }
        final PackedBlockStateArray packed = new PackedBlockStateArray(blockStates, needsMaterial.length);
        long counted = 0L;
        for (long index = 0; index < volume; index++) {
            final int paletteIndex = packed.get(index);
            if (paletteIndex >= 0 && paletteIndex < needsMaterial.length && needsMaterial[paletteIndex]) {
                counted++;
            }
        }
        return counted;
    }

    /** @return whether each palette entry asks for an item to be placed */
    private static boolean[] resolvePalette(final NbtList paletteData) {
        final boolean[] needsMaterial = new boolean[paletteData.size()];
        for (int index = 0; index < paletteData.size(); index++) {
            needsMaterial[index] = needsMaterial(NbtHelper.getCompound(paletteData, index));
        }
        return needsMaterial;
    }

    private static boolean needsMaterial(final NbtCompound entry) {
        if (entry == null) {
            return false;
        }
        final String name = NbtHelper.getString(entry, "Name");
        if (name.isEmpty()) {
            return false;
        }
        // The upper half of a door or bed is placed by the lower one, so only the
        // lower one costs a material.
        final NbtCompound props = NbtHelper.getCompound(entry, "Properties");
        if (props != null
                && ("upper".equals(NbtHelper.getString(props, "half"))
                || "head".equals(NbtHelper.getString(props, "part")))) {
            return false;
        }
        final Optional<Identifier> blockId = IdentifierUtil.tryParse(name);
        if (!blockId.isPresent()) {
            return false;
        }
        final Block block = Registry.BLOCK.getOrEmpty(blockId.get()).orElse(Blocks.AIR);
        return block != Blocks.AIR && block.asItem() != Items.AIR;
    }
}
