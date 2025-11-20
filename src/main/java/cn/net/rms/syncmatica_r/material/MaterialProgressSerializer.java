package cn.net.rms.syncmatica_r.material;

import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Identifier;

public final class MaterialProgressSerializer {
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_VARIANT = "variant";
    private static final String FIELD_REQUIRED = "required";
    private static final String FIELD_STOCK = "stockSupplied";
    private static final String FIELD_CLAIMERS = "claimers";

    private MaterialProgressSerializer() {
    }

    public static JsonObject toJson(final MaterialProgressState state) {
        final JsonObject root = new JsonObject();
        final JsonArray entries = new JsonArray();
        for (final MaterialProgressEntry entry : state.getEntries()) {
            final JsonObject node = new JsonObject();
            node.add(FIELD_ITEM, new JsonPrimitive(entry.getKey().itemId().toString()));
            if (!entry.getKey().variant().isEmpty()) {
                node.add(FIELD_VARIANT, new JsonPrimitive(entry.getKey().variant()));
            }
            node.add(FIELD_REQUIRED, new JsonPrimitive(entry.getRequiredAmount()));
            node.add(FIELD_STOCK, new JsonPrimitive(entry.getStockingSupplied()));
            if (!entry.getClaimants().isEmpty()) {
                final JsonArray claimers = new JsonArray();
                for (final PlayerIdentifier p : entry.getClaimants()) {
                    claimers.add(p.toJson());
                }
                node.add(FIELD_CLAIMERS, claimers);
            }
            entries.add(node);
        }
        root.add("entries", entries);
        return root;
    }

    public static void fromJson(final JsonObject root, final MaterialProgressState state,
                                final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider provider) {
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
//#if MC >= 12005
//$$             final Identifier itemId = Identifier.of(node.get(FIELD_ITEM).getAsString());
//#else
            final Identifier itemId = new Identifier(node.get(FIELD_ITEM).getAsString());
//#endif
            final String variant = node.has(FIELD_VARIANT) ? node.get(FIELD_VARIANT).getAsString() : "";
            final MaterialProgressEntry entry = state.getOrCreate(new MaterialKey(itemId, variant), node.get(FIELD_REQUIRED).getAsInt());
            if (node.has(FIELD_STOCK)) {
                entry.setStockingSupplied(node.get(FIELD_STOCK).getAsInt());
            }
            if (node.has(FIELD_CLAIMERS)) {
                entry.clearClaimants();
                for (final JsonElement e : node.getAsJsonArray(FIELD_CLAIMERS)) {
                    if (e != null && e.isJsonObject()) {
                        entry.addClaimer(provider.fromJson(e.getAsJsonObject()));
                    }
                }
            }
        }
    }
}
