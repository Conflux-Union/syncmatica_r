package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the per-chunk build counts inside the world they were measured in.
 *
 * <p>The counts describe world blocks rather than the schematic, and they are
 * only true of the world state they were read from. Storing them in the save
 * directory is what makes that hold: restoring a backup or rolling the world
 * back brings the matching counts along with it, so the rule the counting rests
 * on — that a block cannot change while its chunk is unloaded — survives the one
 * everyday operation that would otherwise break it without anyone noticing.
 *
 * <p>Claims and region names stay with the placement, where they belong: those
 * describe who agreed to build what, which a rollback of the world has no
 * opinion about.
 */
public final class BuildScanStore {

    private static final Logger LOGGER = LogManager.getLogger(BuildScanStore.class);
    private static final String FOLDER_NAME = "build_scan";
    private static final String FILE_SUFFIX = ".scan.json";
    private static final String FIELD_REGIONS = "regions";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";
    private static final String FIELD_SCANNED_AT = "scannedAt";
    private static final String FIELD_COLUMNS = "columns";
    private static final int COLUMN_STRIDE = 3;
    /**
     * Far beyond any region worth scanning, and only here so a damaged file
     * cannot be read into an unbounded map.
     */
    private static final int MAX_COLUMNS = 1_048_576;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File folder;

    public BuildScanStore(final File worldFolder) {
        folder = new File(new File(worldFolder, Syncmatica.MOD_ID), FOLDER_NAME);
    }

    /**
     * Restores what has been counted, and the totals that follow from it, onto
     * regions that still exist. Counts stored for a region the schematic no
     * longer has are dropped with it.
     */
    public void load(final UUID placementId, final BuildRegionState regions) {
        final File file = fileOf(placementId);
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            final JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) {
                return;
            }
            readRegions(root.getAsJsonObject(), regions);
        } catch (final IOException | RuntimeException exception) {
            LOGGER.warn("Failed to read build scan data from {}", file, exception);
        }
    }

    public void save(final UUID placementId, final BuildRegionState regions) {
        final JsonArray nodes = new JsonArray();
        for (final BuildRegion region : regions.getRegions()) {
            final JsonObject node = regionToJson(region);
            if (node != null) {
                nodes.add(node);
            }
        }
        final File file = fileOf(placementId);
        if (nodes.size() == 0) {
            delete(placementId);
            return;
        }
        if (!folder.isDirectory() && !folder.mkdirs()) {
            LOGGER.warn("Failed to create build scan folder {}", folder);
            return;
        }
        final JsonObject root = new JsonObject();
        root.add(FIELD_REGIONS, nodes);
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (final IOException | RuntimeException exception) {
            LOGGER.warn("Failed to write build scan data to {}", file, exception);
        }
    }

    public void delete(final UUID placementId) {
        final File file = fileOf(placementId);
        if (file.isFile() && !file.delete()) {
            LOGGER.warn("Failed to delete build scan data {}", file);
        }
    }

    private void readRegions(final JsonObject root, final BuildRegionState regions) {
        if (!root.has(FIELD_REGIONS) || !root.get(FIELD_REGIONS).isJsonArray()) {
            return;
        }
        int regionCount = 0;
        for (final JsonElement element : root.getAsJsonArray(FIELD_REGIONS)) {
            if (regionCount++ >= ProtocolLimits.MAX_REGION_ENTRIES) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject node = element.getAsJsonObject();
            if (!node.has(FIELD_NAME)) {
                continue;
            }
            final BuildRegion region = regions.get(node.get(FIELD_NAME).getAsString());
            if (region == null) {
                continue;
            }
            final RegionScanCache cache = cacheFromJson(node);
            if (cache == null) {
                continue;
            }
            region.setScanCache(cache);
            // The counts are the measurement; the total is only their sum, so it
            // is taken from them rather than stored twice and trusted to agree.
            region.recordScan(cache.getTotal(), scannedAtFromJson(node));
        }
    }

    private static JsonObject regionToJson(final BuildRegion region) {
        final RegionScanCache cache = region.getScanCache();
        if (cache == null || cache.getCountedColumnCount() == 0) {
            return null;
        }
        final JsonObject node = new JsonObject();
        node.addProperty(FIELD_NAME, region.getRegionName());
        node.add(FIELD_MIN, positionToJson(cache.getBounds().getMin()));
        node.add(FIELD_MAX, positionToJson(cache.getBounds().getMax()));
        node.addProperty(FIELD_SCANNED_AT, region.getLastScanMillis());
        final JsonArray columns = new JsonArray();
        for (final Map.Entry<Long, Integer> column : cache.getCounts().entrySet()) {
            columns.add(RegionScanCache.columnX(column.getKey()));
            columns.add(RegionScanCache.columnZ(column.getKey()));
            columns.add(column.getValue());
        }
        node.add(FIELD_COLUMNS, columns);
        return node;
    }

    /**
     * @return the stored counts, or null when the entry is unusable. Nothing here
     *         needs to know where the placement currently sits: a cache whose
     *         bounds no longer match is dropped when the scan resolves the box.
     */
    private static RegionScanCache cacheFromJson(final JsonObject node) {
        final BlockPos min = positionFromJson(node.get(FIELD_MIN));
        final BlockPos max = positionFromJson(node.get(FIELD_MAX));
        if (min == null || max == null || !node.has(FIELD_COLUMNS) || !node.get(FIELD_COLUMNS).isJsonArray()) {
            return null;
        }
        final JsonArray columns = node.getAsJsonArray(FIELD_COLUMNS);
        final RegionScanCache cache = new RegionScanCache(new RegionBounds(min, max));
        final int entries = Math.min(columns.size() / COLUMN_STRIDE, MAX_COLUMNS);
        for (int entry = 0; entry < entries; entry++) {
            final int offset = entry * COLUMN_STRIDE;
            cache.record(
                    columns.get(offset).getAsInt(),
                    columns.get(offset + 1).getAsInt(),
                    columns.get(offset + 2).getAsInt()
            );
        }
        return cache;
    }

    /** A region restored without a timestamp still counts as measured. */
    private static long scannedAtFromJson(final JsonObject node) {
        final long scannedAt = node.has(FIELD_SCANNED_AT) ? node.get(FIELD_SCANNED_AT).getAsLong() : 0L;
        return scannedAt > 0L ? scannedAt : System.currentTimeMillis();
    }

    private static JsonArray positionToJson(final BlockPos pos) {
        final JsonArray array = new JsonArray();
        array.add(pos.getX());
        array.add(pos.getY());
        array.add(pos.getZ());
        return array;
    }

    private static BlockPos positionFromJson(final JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        final JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return null;
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private File fileOf(final UUID placementId) {
        return new File(folder, placementId + FILE_SUFFIX);
    }
}
