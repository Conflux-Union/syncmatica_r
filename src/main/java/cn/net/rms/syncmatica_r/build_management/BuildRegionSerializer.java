package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class BuildRegionSerializer {
    private static final String FIELD_REGIONS = "regions";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_BLOCKS = "blocks";
    private static final String FIELD_CLAIMERS = "claimers";

    private BuildRegionSerializer() {
    }

    public static JsonObject toJson(final BuildRegionState state) {
        final JsonObject root = new JsonObject();
        final JsonArray regions = new JsonArray();
        for (final BuildRegion region : state.getRegions()) {
            final JsonObject node = new JsonObject();
            node.add(FIELD_NAME, new JsonPrimitive(region.getRegionName()));
            node.add(FIELD_BLOCKS, new JsonPrimitive(region.getRequiredBlocks()));
            if (!region.getClaimants().isEmpty()) {
                final JsonArray claimers = new JsonArray();
                for (final PlayerIdentifier claimer : region.getClaimants()) {
                    claimers.add(claimer.toJson());
                }
                node.add(FIELD_CLAIMERS, claimers);
            }
            regions.add(node);
        }
        root.add(FIELD_REGIONS, regions);
        return root;
    }

    /**
     * The same limits the wire format enforces apply here: the file on disk is
     * rewritten from whatever a peer once sent, so it is no more trustworthy than
     * the packet was.
     */
    public static void fromJson(final JsonObject root, final BuildRegionState state,
                                final PlayerIdentifierProvider provider) {
        state.clear();
        if (root == null || !root.has(FIELD_REGIONS)) {
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
            final String regionName = node.get(FIELD_NAME).getAsString();
            if (regionName.isEmpty() || regionName.length() > ProtocolLimits.MAX_SUBREGION_NAME_LENGTH) {
                continue;
            }
            final long blocks = node.has(FIELD_BLOCKS) ? node.get(FIELD_BLOCKS).getAsLong() : 0L;
            final BuildRegion region = state.getOrCreate(regionName, blocks);
            if (node.has(FIELD_CLAIMERS)) {
                region.clearClaimants();
                int claimantCount = 0;
                for (final JsonElement claimer : node.getAsJsonArray(FIELD_CLAIMERS)) {
                    if (claimantCount++ >= ProtocolLimits.MAX_CLAIMANTS_PER_REGION) {
                        break;
                    }
                    if (claimer != null && claimer.isJsonObject()) {
                        region.addClaimer(provider.fromJson(claimer.getAsJsonObject()));
                    }
                }
            }
        }
    }
}
