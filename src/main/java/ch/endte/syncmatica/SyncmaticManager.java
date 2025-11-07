package ch.endte.syncmatica;

import ch.endte.syncmatica.util.SyncmaticaUtil;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class SyncmaticManager {
    public static final String PLACEMENTS_JSON_KEY = "placements";
    public static final String DEFAULT_STOCKING_AREA_JSON_KEY = "defaultStockingArea";
    private static final String PLACEMENT_FILE_SUFFIX = ".placement.json";
    private static final String META_FILE_NAME = "meta.json";
    private static final long SAVE_DEBOUNCE_MILLIS = 1500L;
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Map<UUID, ServerPlacement> schematics = new HashMap<>();
    private final Collection<Consumer<ServerPlacement>> consumers = new ArrayList<>();
    private final java.util.Set<UUID> dirtyPlacements = new java.util.HashSet<>();
    private final java.util.Set<UUID> removedPlacements = new java.util.HashSet<>();
    private boolean metaDirty = false;

    Context context;
    private boolean savePending = false;
    private long lastSaveMillis = 0L;

    public void setContext(final Context con) {
        if (context == null) {
            context = con;
        } else {
            throw new Context.DuplicateContextAssignmentException("Duplicate Context assignment");
        }
    }

    public void addPlacement(final ServerPlacement placement) {
        schematics.put(placement.getId(), placement);
        if (context != null && context.getMaterialService() != null) {
            context.getMaterialService().attachPlacement(placement);
        }
        updateServerPlacement(placement);
        markPlacementDirty(placement.getId());
    }

    public ServerPlacement getPlacement(final UUID id) {
        return schematics.get(id);
    }

    public Collection<ServerPlacement> getAll() {
        return schematics.values();
    }

    public void removePlacement(final ServerPlacement placement) {
        schematics.remove(placement.getId());
        if (context != null && context.getMaterialService() != null) {
            context.getMaterialService().detachPlacement(placement);
        }
        markPlacementRemoved(placement.getId());
        updateServerPlacement(placement);
    }

    public void addServerPlacementConsumer(final Consumer<ServerPlacement> consumer) {
        consumers.add(consumer);
    }

    public void removeServerPlacementConsumer(final Consumer<ServerPlacement> consumer) {
        consumers.remove(consumer);
    }

    public void updateServerPlacement(final ServerPlacement updated) {
        for (final Consumer<ServerPlacement> consumer : consumers) {
            consumer.accept(updated);
        }

        if (context.isServer()) {
            markDirty();
            if (updated != null) {
                markPlacementDirty(updated.getId());
            }
        }
    }

    public void startup() {
        if (context.isServer()) {
            loadServer();
        }
    }

    public void shutdown() {
        if (context == null || !context.isServer()) {
            return;
        }
        saveServerState();
    }

    public void saveServerState() {
        if (!context.isServer()) {
            return;
        }
        dirtyPlacements.addAll(schematics.keySet());
        metaDirty = true;
        saveServer();
    }

    public void tickServer() {
        if (!context.isServer() || !savePending) {
            return;
        }
        if (System.currentTimeMillis() - lastSaveMillis < SAVE_DEBOUNCE_MILLIS) {
            return;
        }
        saveServer();
    }

    private void markDirty() {
        savePending = true;
    }

    private void saveServer() {
        if (!context.isServer()) {
            return;
        }
        if (!savePending && dirtyPlacements.isEmpty() && removedPlacements.isEmpty() && !metaDirty) {
            return;
        }
        savePending = false;
        lastSaveMillis = System.currentTimeMillis();

        final File storeFolder = getPlacementStoreFolder();
        if (!storeFolder.exists() && !storeFolder.mkdirs()) {
            LogManager.getLogger(SyncmaticManager.class).warn("Failed to create placement store folder: {}", storeFolder.getAbsolutePath());
            return;
        }

        final java.util.Set<UUID> currentDirty = new java.util.HashSet<>(dirtyPlacements);
        final java.util.Set<UUID> currentRemoved = new java.util.HashSet<>(removedPlacements);
        dirtyPlacements.clear();
        removedPlacements.clear();

        for (final UUID id : currentDirty) {
            final ServerPlacement placement = schematics.get(id);
            if (placement == null) {
                continue;
            }
            writePlacementFile(placement);
        }

        for (final UUID id : currentRemoved) {
            deletePlacementFile(id);
        }

        if (metaDirty || context.getMaterialService() != null) {
            writeMetaFile();
            metaDirty = false;
        }
    }

    private void loadServer() {
        if (loadFromPlacementStore()) {
            return;
        }
        final File f = new File(context.getConfigFolder(), "placements.json");
        if (f.exists() && f.isFile() && f.canRead()) {
            JsonElement element = null;
            try {
                final JsonParser parser = new JsonParser();
                try (final FileReader reader = new FileReader(f)) {
                    element = parser.parse(reader);
                }

            } catch (final Exception e) {
                e.printStackTrace();
            }
            if (element == null) {

                return;
            }
            try {
                final JsonObject obj = element.getAsJsonObject();
                if (obj == null) {
                    return;
                }

                if (obj.has(PLACEMENTS_JSON_KEY)) {
                    final JsonArray arr = obj.getAsJsonArray(PLACEMENTS_JSON_KEY);
                    for (final JsonElement elem : arr) {
                        final ServerPlacement placement = ServerPlacement.fromJson(elem.getAsJsonObject(), context);
                        schematics.put(placement.getId(), placement);
                        if (context.getMaterialService() != null) {
                            context.getMaterialService().attachPlacement(placement);
                        }
                    }
                }

                if (context.getMaterialService() != null && obj.has(DEFAULT_STOCKING_AREA_JSON_KEY)) {
                    final ch.endte.syncmatica.material.StockingAreaDefinition def =
                            ch.endte.syncmatica.material.StockingAreaDefinition.fromJson(
                                    obj.getAsJsonObject(DEFAULT_STOCKING_AREA_JSON_KEY));
                    context.getMaterialService().loadDefaultStockingArea(def);
                }

            } catch (final IllegalStateException | NullPointerException e) {
                e.printStackTrace();
            }
            dirtyPlacements.addAll(schematics.keySet());
            metaDirty = true;
            markDirty();
        }
    }

    private void loadDefaultStockingAreaFromMeta(final File folder) {
        final File meta = new File(folder, META_FILE_NAME);
        if (!meta.exists() || !meta.isFile()) {
            return;
        }
            try (final FileReader reader = new FileReader(meta)) {
            final JsonObject obj = new JsonParser().parse(reader).getAsJsonObject();
            if (obj == null || !obj.has(DEFAULT_STOCKING_AREA_JSON_KEY)) {
                return;
            }
            if (context.getMaterialService() != null) {
                final ch.endte.syncmatica.material.StockingAreaDefinition def =
                        ch.endte.syncmatica.material.StockingAreaDefinition.fromJson(
                                obj.getAsJsonObject(DEFAULT_STOCKING_AREA_JSON_KEY));
                context.getMaterialService().loadDefaultStockingArea(def);
            }
        } catch (final Exception exception) {
            LogManager.getLogger(SyncmaticManager.class).warn("Failed to load placement metadata", exception);
        }
    }

    private boolean hasMetaFile(final File folder) {
        final File meta = new File(folder, META_FILE_NAME);
        return meta.exists() && meta.isFile();
    }

    private void writePlacementFile(final ServerPlacement placement) {
        final File folder = getPlacementStoreFolder();
        final File current = new File(folder, placement.getId().toString() + PLACEMENT_FILE_SUFFIX);
        final File incoming = new File(folder, placement.getId().toString() + PLACEMENT_FILE_SUFFIX + ".new");
        final File backup = new File(folder, placement.getId().toString() + PLACEMENT_FILE_SUFFIX + ".bak");
        try (final FileWriter writer = new FileWriter(incoming)) {
            GSON.toJson(placement.toJson(), writer);
        } catch (final IOException e) {
            LogManager.getLogger(SyncmaticManager.class).warn("Failed to write placement file {}", current.getName(), e);
            return;
        }
        SyncmaticaUtil.backupAndReplace(backup.toPath(), current.toPath(), incoming.toPath());
    }

    private void deletePlacementFile(final UUID id) {
        final File folder = getPlacementStoreFolder();
        final File current = new File(folder, id.toString() + PLACEMENT_FILE_SUFFIX);
        final File backup = new File(folder, id.toString() + PLACEMENT_FILE_SUFFIX + ".bak");
        if (current.exists() && !current.delete()) {
            LogManager.getLogger(SyncmaticManager.class).warn("Failed to delete placement file {}", current.getName());
        }
        if (backup.exists()) {
            backup.delete();
        }
    }

    private void writeMetaFile() {
        final File folder = getPlacementStoreFolder();
        final File current = new File(folder, META_FILE_NAME);
        final File incoming = new File(folder, META_FILE_NAME + ".new");
        final File backup = new File(folder, META_FILE_NAME + ".bak");
        final JsonObject obj = new JsonObject();
        if (context.getMaterialService() != null) {
            final ch.endte.syncmatica.material.StockingAreaDefinition def =
                    context.getMaterialService().getDefaultStockingArea();
            if (def != null) {
                obj.add(DEFAULT_STOCKING_AREA_JSON_KEY, def.toJson());
            }
        }
        try (final FileWriter writer = new FileWriter(incoming)) {
            GSON.toJson(obj, writer);
        } catch (final IOException e) {
            LogManager.getLogger(SyncmaticManager.class).warn("Failed to write placement metadata", e);
            return;
        }
        SyncmaticaUtil.backupAndReplace(backup.toPath(), current.toPath(), incoming.toPath());
    }

    private File getPlacementStoreFolder() {
        return new File(context.getConfigFolder(), "placement_store");
    }

    private void markPlacementDirty(final UUID id) {
        if (id == null) {
            return;
        }
        if (context == null || !context.isServer()) {
            return;
        }
        removedPlacements.remove(id);
        dirtyPlacements.add(id);
        markDirty();
    }

    private void markPlacementRemoved(final UUID id) {
        if (id == null) {
            return;
        }
        if (context == null || !context.isServer()) {
            return;
        }
        dirtyPlacements.remove(id);
        removedPlacements.add(id);
        markDirty();
    }

    public void markDefaultStockingAreaDirty() {
        if (context == null || !context.isServer()) {
            return;
        }
        metaDirty = true;
        markDirty();
    }

    private boolean loadFromPlacementStore() {
        final File folder = getPlacementStoreFolder();
        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(PLACEMENT_FILE_SUFFIX));
        boolean loaded = false;
        if (files != null) {
            for (final File file : files) {
            try (final FileReader reader = new FileReader(file)) {
                final JsonObject obj = new JsonParser().parse(reader).getAsJsonObject();
                    final ServerPlacement placement = ServerPlacement.fromJson(obj, context);
                    schematics.put(placement.getId(), placement);
                    if (context.getMaterialService() != null) {
                        context.getMaterialService().attachPlacement(placement);
                    }
                    loaded = true;
                } catch (final Exception exception) {
                    LogManager.getLogger(SyncmaticManager.class).warn("Failed to load placement file {}", file.getName(), exception);
                }
            }
        }
        loadDefaultStockingAreaFromMeta(folder);
        return loaded || hasMetaFile(folder);
    }

}
