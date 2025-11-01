package ch.endte.syncmatica.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Identifier;

/**
 * Converts material progress state to JSON snapshots for persistence and sync.
 */
public final class MaterialProgressSerializer {
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_VARIANT = "variant";
    private static final String FIELD_REQUIRED = "required";
    private static final String FIELD_STOCK = "stockSupplied";

    private MaterialProgressSerializer() {
    }

    public static JsonObject toJson(final MaterialProgressState state) {
        final JsonObject root = new JsonObject();
        final JsonArray entries = new JsonArray();
        for (final MaterialProgressEntry entry : state.getEntries()) {
            final JsonObject node = new JsonObject();
            node.add(FIELD_ITEM, new JsonPrimitive(entry.getKey().getItemId().toString()));
            if (!entry.getKey().getVariant().isEmpty()) {
                node.add(FIELD_VARIANT, new JsonPrimitive(entry.getKey().getVariant()));
            }
            node.add(FIELD_REQUIRED, new JsonPrimitive(entry.getRequiredAmount()));
            node.add(FIELD_STOCK, new JsonPrimitive(entry.getStockingSupplied()));
            entries.add(node);
        }
        root.add("entries", entries);
        return root;
    }

    public static void fromJson(final JsonObject root, final MaterialProgressState state) {
        state.clear();
        if (root == null || !root.has("entries")) {
            return;
        }
        for (final JsonElement element : root.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject node = element.getAsJsonObject();
            if (!node.has(FIELD_ITEM) || !node.has(FIELD_REQUIRED)) {
                continue;
            }
            final Identifier itemId = new Identifier(node.get(FIELD_ITEM).getAsString());
            final String variant = node.has(FIELD_VARIANT) ? node.get(FIELD_VARIANT).getAsString() : "";
            final MaterialProgressEntry entry = state.getOrCreate(new MaterialKey(itemId, variant), node.get(FIELD_REQUIRED).getAsInt());
            if (node.has(FIELD_STOCK)) {
                entry.setStockingSupplied(node.get(FIELD_STOCK).getAsInt());
            }
        }
    }
}
