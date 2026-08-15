package cn.net.rms.syncmatica_r.litematica;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.client.BuildVisibilityPreferences;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Switches a Litematica sub-region on when this player claims it and off again
 * when they drop it.
 *
 * <p>Splitting a build across players normally means each of them turning off
 * the regions somebody else is responsible for, by hand, every time the
 * assignment changes. The claim list already says who has what, so the toggling
 * can follow it.
 *
 * <p>Only the regions whose claim actually changed are touched. Regions nobody
 * claimed, and regions claimed by others, keep whatever the player set them to —
 * this fills in the tedious part of the bookkeeping rather than taking the
 * setting over.
 *
 * <p>Claims are tracked per placement against the last state this player was
 * seen holding, not against what Litematica currently shows, so a region the
 * player deliberately re-enabled stays enabled until its claim changes again.
 */
public final class ClaimedRegionVisibility {

    private static final ClaimedRegionVisibility INSTANCE = new ClaimedRegionVisibility();

    private final Consumer<ServerPlacement> listener = this::onPlacementUpdated;
    /** Which regions of each placement this player was last known to hold. */
    private final Map<UUID, Set<String>> claimed = new HashMap<>();

    private Context context;

    private ClaimedRegionVisibility() {
    }

    public static ClaimedRegionVisibility getInstance() {
        return INSTANCE;
    }

    /** Called from the network actor once the client context exists. */
    public void bindToClientContext(final Context ctx) {
        if (ctx == context) {
            return;
        }
        detach();
        if (ctx == null) {
            return;
        }
        context = ctx;
        ctx.getSyncmaticManager().addServerPlacementConsumer(listener);
    }

    public void reset() {
        detach();
    }

    /**
     * Applies the current claims straight away rather than waiting for the next
     * server update, which is what the option being switched on has to do to
     * look like it did anything.
     */
    public void refresh() {
        if (context == null || context.getSyncmaticManager() == null) {
            return;
        }
        for (final ServerPlacement placement : new ArrayList<>(context.getSyncmaticManager().getAll())) {
            onPlacementUpdated(placement);
        }
    }

    private void detach() {
        if (context != null) {
            context.getSyncmaticManager().removeServerPlacementConsumer(listener);
        }
        context = null;
        claimed.clear();
    }

    private void onPlacementUpdated(final ServerPlacement placement) {
        if (placement == null) {
            return;
        }
        final UUID placementId = placement.getId();
        if (!BuildVisibilityPreferences.isFollowClaimsEnabled()) {
            // Nothing is tracked while the option is off, so switching it back on
            // starts from what is claimed then instead of from a stale set.
            claimed.remove(placementId);
            return;
        }
        final SchematicPlacement litematica = LitematicManager.getInstance().schematicFromSyncmatic(placement);
        if (litematica == null) {
            // Not placed yet, so there is nothing to switch. Dropping the baseline
            // is what makes the claims apply once it is.
            claimed.remove(placementId);
            return;
        }
        final UUID self = selfId();
        if (self == null) {
            return;
        }
        final Set<String> current = collectOwnClaims(placement.getBuildRegions(), self);
        final Set<String> previous = claimed.put(placementId, current);
        apply(litematica, changeBetween(previous == null ? Collections.emptySet() : previous, current));
    }

    private static void apply(final SchematicPlacement litematica, final ClaimChange change) {
        final List<SubRegionPlacement> toEnable = subRegionsOf(litematica, change.toEnable);
        final List<SubRegionPlacement> toDisable = subRegionsOf(litematica, change.toDisable);
        if (toEnable.isEmpty() && toDisable.isEmpty()) {
            return;
        }
        // A shared placement is kept locked so a stray drag cannot move it; the
        // lock has to come off for the change and go straight back on.
        final boolean wasLocked = litematica.isLocked();
        if (wasLocked) {
            litematica.toggleLocked();
        }
        if (!toEnable.isEmpty()) {
            litematica.setSubRegionsEnabledState(true, toEnable, null);
        }
        if (!toDisable.isEmpty()) {
            litematica.setSubRegionsEnabledState(false, toDisable, null);
        }
        if (wasLocked) {
            litematica.toggleLocked();
        }
    }

    static Set<String> collectOwnClaims(final BuildRegionState regions, final UUID self) {
        final Set<String> mine = new HashSet<>();
        if (regions == null || self == null) {
            return mine;
        }
        for (final BuildRegion region : regions.getRegions()) {
            for (final PlayerIdentifier claimer : region.getClaimants()) {
                if (self.equals(claimer.uuid)) {
                    mine.add(region.getRegionName());
                    break;
                }
            }
        }
        return mine;
    }

    /**
     * What the visibility has to follow: only the regions whose claim actually
     * moved. Everything else is left alone, including regions somebody else
     * claimed and regions the player enabled by hand.
     */
    static ClaimChange changeBetween(final Set<String> previous, final Set<String> current) {
        return new ClaimChange(difference(current, previous), difference(previous, current));
    }

    /** The regions to switch on and off, named rather than resolved. */
    static final class ClaimChange {
        final Set<String> toEnable;
        final Set<String> toDisable;

        private ClaimChange(final Set<String> toEnable, final Set<String> toDisable) {
            this.toEnable = toEnable;
            this.toDisable = toDisable;
        }
    }

    private static List<SubRegionPlacement> subRegionsOf(final SchematicPlacement litematica, final Set<String> names) {
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        final List<SubRegionPlacement> found = new ArrayList<>(names.size());
        for (final String name : names) {
            final SubRegionPlacement subRegion = litematica.getRelativeSubRegionPlacement(name);
            if (subRegion != null) {
                found.add(subRegion);
            }
        }
        return found;
    }

    private static Set<String> difference(final Set<String> from, final Set<String> without) {
        if (from.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<String> result = new HashSet<>(from);
        result.removeAll(without);
        return result;
    }

    private static UUID selfId() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return null;
        }
        return SyncmaticaUtil.getProfileId(client.player.getGameProfile());
    }
}
