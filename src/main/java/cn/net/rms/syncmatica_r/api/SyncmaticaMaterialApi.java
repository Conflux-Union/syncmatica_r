package cn.net.rms.syncmatica_r.api;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.MaterialProgressEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Read-only integration seam for client mods that consume Syncmatica material claims.
 *
 * <p>Callers must invoke this interface on the Minecraft client thread. Returned
 * amounts reflect the server stocking-area deficit and deliberately do not
 * subtract items already carried by the player.</p>
 */
public final class SyncmaticaMaterialApi {

    private SyncmaticaMaterialApi() {
    }

    /**
     * Returns an immutable snapshot of outstanding materials claimed by a player.
     *
     * @param playerId the player's game profile UUID
     * @return claimed requirements, or an empty list when no client context exists
     */
    public static List<MaterialRequirement> getClaimedMaterialRequirements(final UUID playerId) {
        if (playerId == null) {
            return Collections.emptyList();
        }
        final Context context = Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT);
        if (context == null || context.getSyncmaticManager() == null) {
            return Collections.emptyList();
        }

        final Map<MaterialKey, Integer> missingByMaterial = new TreeMap<>(
                Comparator.comparing((MaterialKey key) -> key.itemId().toString())
                        .thenComparing(MaterialKey::variant)
        );
        for (final ServerPlacement placement : context.getSyncmaticManager().getAll()) {
            for (final MaterialProgressEntry entry : placement.getMaterialProgress().getEntries()) {
                final int missingAmount = entry.getMissingAmount();
                if (missingAmount <= 0 || !isClaimedBy(entry, playerId)) {
                    continue;
                }
                missingByMaterial.merge(entry.getKey(), missingAmount, SyncmaticaMaterialApi::saturatingAdd);
            }
        }
        final List<MaterialRequirement> requirements = new ArrayList<>(missingByMaterial.size());
        for (final Map.Entry<MaterialKey, Integer> entry : missingByMaterial.entrySet()) {
            requirements.add(new MaterialRequirement(
                    entry.getKey().itemId().toString(),
                    entry.getKey().variant(),
                    entry.getValue()
            ));
        }
        return Collections.unmodifiableList(requirements);
    }

    private static int saturatingAdd(final int left, final int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }

    private static boolean isClaimedBy(final MaterialProgressEntry entry, final UUID playerId) {
        for (final PlayerIdentifier claimant : entry.getClaimants()) {
            if (playerId.equals(claimant.uuid)) {
                return true;
            }
        }
        return false;
    }
}
