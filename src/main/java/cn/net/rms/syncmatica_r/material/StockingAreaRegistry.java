package cn.net.rms.syncmatica_r.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-owned set of named stocking areas. Entries are addressed by UUID so
 * the display name stays a mutable label, and every mutation path (command,
 * packet, web) funnels through the same validation. The registry itself knows
 * nothing about placements; reference checks live in MaterialService.
 */
public final class StockingAreaRegistry {
    private static final Logger LOGGER = LogManager.getLogger(StockingAreaRegistry.class);
    public static final String RESERVED_DEFAULT_NAME = "default";
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final String FIELD_AREAS = "areas";
    private static final String FIELD_ID = "id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_OWNER = "owner";

    private final Map<UUID, Entry> areasById = new LinkedHashMap<>();
    private final Map<String, UUID> idsByName = new LinkedHashMap<>();

    public enum CreateOutcome {
        CREATED, INVALID_NAME, DUPLICATE_NAME, RESERVED_NAME, TOO_LARGE
    }

    public enum UpdateOutcome {
        UPDATED, TOO_LARGE, NOT_FOUND
    }

    public enum DeleteOutcome {
        DELETED, RESERVED_NAME, NOT_FOUND
    }

    public static final class Entry {
        private final UUID id;
        private final String name;
        private StockingAreaDefinition definition;
        private final UUID ownerPlayerId;

        Entry(final UUID id, final String name, final StockingAreaDefinition definition,
              final UUID ownerPlayerId) {
            this.id = id;
            this.name = name;
            this.definition = definition;
            this.ownerPlayerId = ownerPlayerId;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public StockingAreaDefinition getDefinition() {
            return definition;
        }

        /** Null means server-owned (created by console or migration), editable by elevated users only. */
        public UUID getOwnerPlayerId() {
            return ownerPlayerId;
        }
    }

    public CreateOutcome create(final String name, final StockingAreaDefinition definition,
                                final UUID ownerPlayerId, final long maxBlocks) {
        if (!isValidName(name)) {
            return CreateOutcome.INVALID_NAME;
        }
        if (RESERVED_DEFAULT_NAME.equals(name)) {
            // Only the server bootstrap (null owner: console or migration) may write the
            // reserved entry; the guard stays intact for every player-owned create.
            return ownerPlayerId == null
                    ? createDefault(definition, maxBlocks)
                    : CreateOutcome.RESERVED_NAME;
        }
        if (idsByName.containsKey(name)) {
            return CreateOutcome.DUPLICATE_NAME;
        }
        if (definition == null || definition.getVolume() > maxBlocks) {
            return CreateOutcome.TOO_LARGE;
        }
        final UUID id = UUID.randomUUID();
        areasById.put(id, new Entry(id, name, definition, ownerPlayerId));
        idsByName.put(name, id);
        return CreateOutcome.CREATED;
    }

    public UpdateOutcome update(final UUID id, final StockingAreaDefinition definition,
                                final long maxBlocks) {
        final Entry entry = areasById.get(id);
        if (entry == null) {
            return UpdateOutcome.NOT_FOUND;
        }
        if (definition == null || definition.getVolume() > maxBlocks) {
            return UpdateOutcome.TOO_LARGE;
        }
        entry.definition = definition;
        return UpdateOutcome.UPDATED;
    }

    public DeleteOutcome delete(final UUID id) {
        final Entry entry = areasById.get(id);
        if (entry == null) {
            return DeleteOutcome.NOT_FOUND;
        }
        if (RESERVED_DEFAULT_NAME.equals(entry.name)) {
            return DeleteOutcome.RESERVED_NAME;
        }
        areasById.remove(id);
        idsByName.remove(entry.name);
        return DeleteOutcome.DELETED;
    }

    public Entry getById(final UUID id) {
        return id == null ? null : areasById.get(id);
    }

    public Entry getByName(final String name) {
        final UUID id = idsByName.get(name);
        return id == null ? null : areasById.get(id);
    }

    public Entry getDefaultArea() {
        return getByName(RESERVED_DEFAULT_NAME);
    }

    public Collection<Entry> getAll() {
        return Collections.unmodifiableCollection(areasById.values());
    }

    public static boolean isValidName(final String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Migration helper: returns null when {@code baseName} is free, otherwise
     * the first free {@code baseName-<n>} for n starting at 2.
     */
    public String findUniqueIdFor(final String baseName) {
        if (!idsByName.containsKey(baseName)) {
            return null;
        }
        for (int suffix = 2; ; suffix++) {
            final String candidate = baseName + "-" + suffix;
            if (!idsByName.containsKey(candidate)) {
                return candidate;
            }
        }
    }

    CreateOutcome createDefault(final StockingAreaDefinition definition, final long maxBlocks) {
        if (definition == null || definition.getVolume() > maxBlocks) {
            return CreateOutcome.TOO_LARGE;
        }
        final Entry existing = getDefaultArea();
        if (existing != null) {
            areasById.remove(existing.id);
        }
        final UUID id = UUID.randomUUID();
        areasById.put(id, new Entry(id, RESERVED_DEFAULT_NAME, definition, null));
        idsByName.put(RESERVED_DEFAULT_NAME, id);
        return CreateOutcome.CREATED;
    }

    /**
     * Persistence seam for {@code MaterialService.loadStockingAreaState}:
     * replaces the live registry with a deserialized one, keeping entry ids
     * stable so persisted placement references survive reloads.
     */
    public void restoreFrom(final StockingAreaRegistry other) {
        areasById.clear();
        idsByName.clear();
        areasById.putAll(other.areasById);
        idsByName.putAll(other.idsByName);
    }

    /**
     * Serializes to the persisted file shape {@code {"areas":[{id,name,owner,
     * dimension,minX..maxZ}]}}: the definition fields are flattened into each
     * entry, and {@code owner} is omitted for server-owned areas.
     */
    public JsonObject toJson() {
        final JsonArray array = new JsonArray();
        for (final Entry entry : areasById.values()) {
            final JsonObject obj = entry.definition.toJson();
            obj.add(FIELD_ID, new JsonPrimitive(entry.id.toString()));
            obj.add(FIELD_NAME, new JsonPrimitive(entry.name));
            if (entry.ownerPlayerId != null) {
                obj.add(FIELD_OWNER, new JsonPrimitive(entry.ownerPlayerId.toString()));
            }
            array.add(obj);
        }
        final JsonObject json = new JsonObject();
        json.add(FIELD_AREAS, array);
        return json;
    }

    /**
     * Pure inverse of {@link #toJson()}: silently skips entries with invalid
     * or duplicate names (hand-edited duplicates: first wins), unparsable
     * definitions or UUIDs, or oversized areas instead of failing the whole
     * load.
     */
    public static StockingAreaRegistry fromJson(final JsonObject json, final long maxBlocks) {
        final StockingAreaRegistry registry = new StockingAreaRegistry();
        if (json == null || !json.has(FIELD_AREAS) || !json.get(FIELD_AREAS).isJsonArray()) {
            return registry;
        }
        int index = 0;
        for (final JsonElement element : json.getAsJsonArray(FIELD_AREAS)) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject obj = element.getAsJsonObject();
            try {
                if (!obj.has(FIELD_ID) || !obj.has(FIELD_NAME)) {
                    continue;
                }
                final String name = obj.get(FIELD_NAME).getAsString();
                if (!isValidName(name) || registry.idsByName.containsKey(name)) {
                    continue;
                }
                final StockingAreaDefinition definition = StockingAreaDefinition.fromJson(obj);
                if (definition == null || definition.getVolume() > maxBlocks) {
                    continue;
                }
                final UUID owner = obj.has(FIELD_OWNER)
                        ? UUID.fromString(obj.get(FIELD_OWNER).getAsString())
                        : null;
                final UUID id = UUID.fromString(obj.get(FIELD_ID).getAsString());
                registry.areasById.put(id, new Entry(id, name, definition, owner));
                registry.idsByName.put(name, id);
            } catch (final RuntimeException exception) {
                // Hand-edited files may carry an unparsable UUID or a broken
                // definition object; the entry is skipped like the other
                // invalid cases instead of aborting the whole load.
                LOGGER.warn("Skipping malformed stocking area entry at index {}", index, exception);
            }
            index++;
        }
        return registry;
    }
}
