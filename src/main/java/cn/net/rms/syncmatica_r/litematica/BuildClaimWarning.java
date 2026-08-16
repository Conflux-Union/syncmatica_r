package cn.net.rms.syncmatica_r.litematica;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.client.BuildWarningPreferences;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.gui.Message;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a player when they are about to build inside a sub-region somebody else
 * signed up for.
 *
 * <p>The check belongs on the client: the claim list already arrives with every
 * region broadcast, and Litematica has itself worked out where each sub-region
 * sits, so nothing has to be recomputed or asked for. A player who never loaded
 * the schematic is deliberately left alone — they are not part of this build's
 * division of labour, and a claim coordinates work rather than protecting it.
 */
public final class BuildClaimWarning {

    private static final long THROTTLE_MILLIS = 5_000L;
    /** Guards against unbounded growth when a player roams across many regions. */
    private static final int MAX_TRACKED_REGIONS = 64;

    private static final Map<String, Long> LAST_WARNED = new HashMap<>();

    private BuildClaimWarning() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() || hitResult == null) {
                return ActionResult.PASS;
            }
            // Only a block in hand counts as building; opening a chest inside
            // someone else's region is not worth a warning.
            if (!(player.getStackInHand(hand).getItem() instanceof BlockItem)) {
                return ActionResult.PASS;
            }
            // The block normally lands against the hit face, but clicking a
            // replaceable block (grass, snow, fluid) puts it in that block's own
            // spot, so both positions have to be considered.
            final BlockPos hit = hitResult.getBlockPos();
            warnIfForeign(player, hit.offset(hitResult.getSide()), hit);
            return ActionResult.PASS;
        });
    }

    public static void clear() {
        LAST_WARNED.clear();
    }

    private static void warnIfForeign(final PlayerEntity player, final BlockPos... candidates) {
        if (!BuildWarningPreferences.isEnabled()) {
            return;
        }
        final Context context = Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT);
        if (context == null || context.getSyncmaticManager() == null) {
            return;
        }
        final UUID self = SyncmaticaUtil.getProfileId(player.getGameProfile());
        if (self == null) {
            return;
        }
        final LitematicManager manager = LitematicManager.getInstance();
        final String dimension = manager.getPlayerDimension();
        for (final BlockPos candidate : candidates) {
            if (warnForPosition(context, manager, dimension, candidate, self)) {
                return;
            }
        }
    }

    /**
     * @return true once a claimed region covers this position, whether or not the
     *         reminder was actually shown, so the caller stops looking
     */
    private static boolean warnForPosition(final Context context, final LitematicManager manager,
                                           final String dimension, final BlockPos pos, final UUID self) {
        for (final ServerPlacement placement : context.getSyncmaticManager().getAll()) {
            if (!dimension.equals(placement.getDimension())) {
                continue;
            }
            // A placement the player never loaded has no boxes to test against.
            final SchematicPlacement rendered = manager.schematicFromSyncmatic(placement);
            if (rendered == null) {
                continue;
            }
            // ANY rather than PLACEMENT_ENABLED: hiding a sub-region from the
            // renderer does not hand its work back to everybody else.
            final Map<String, Box> boxes = rendered.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.ANY);
            for (final Map.Entry<String, Box> region : boxes.entrySet()) {
                if (!contains(region.getValue(), pos)) {
                    continue;
                }
                final PlayerIdentifier owner = claimantOf(placement, region.getKey());
                if (owner == null || self.equals(owner.uuid)) {
                    continue;
                }
                warn(placement.getId() + "/" + region.getKey(), region.getKey(), owner.getName());
                return true;
            }
        }
        return false;
    }

    private static PlayerIdentifier claimantOf(final ServerPlacement placement, final String regionName) {
        final BuildRegion entry = placement.getBuildRegions().get(regionName);
        if (entry == null || !entry.isClaimed()) {
            return null;
        }
        return entry.getClaimants().iterator().next();
    }

    private static void warn(final String throttleKey, final String regionName, final String ownerName) {
        if (!shouldWarn(throttleKey)) {
            return;
        }
        ScreenHelper.ifPresent(screen -> screen.addMessage(
                Message.MessageType.WARNING, "syncmatica_r.warning.build.foreign", regionName, ownerName));
    }

    private static boolean shouldWarn(final String throttleKey) {
        final long now = System.currentTimeMillis();
        final Long last = LAST_WARNED.get(throttleKey);
        if (last != null && now - last < THROTTLE_MILLIS) {
            return false;
        }
        if (LAST_WARNED.size() >= MAX_TRACKED_REGIONS) {
            LAST_WARNED.clear();
        }
        LAST_WARNED.put(throttleKey, now);
        return true;
    }

    private static boolean contains(final Box box, final BlockPos pos) {
        return containsBetween(box.getPos1(), box.getPos2(), pos);
    }

    /**
     * Litematica hands back the two corners in whatever order the region was
     * defined, so neither is guaranteed to be the minimum.
     */
    static boolean containsBetween(final BlockPos first, final BlockPos second, final BlockPos pos) {
        if (first == null || second == null || pos == null) {
            return false;
        }
        return within(pos.getX(), first.getX(), second.getX())
                && within(pos.getY(), first.getY(), second.getY())
                && within(pos.getZ(), first.getZ(), second.getZ());
    }

    private static boolean within(final int value, final int one, final int other) {
        return value >= Math.min(one, other) && value <= Math.max(one, other);
    }
}
