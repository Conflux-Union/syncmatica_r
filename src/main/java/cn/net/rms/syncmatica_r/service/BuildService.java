package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.build_management.RegionLayoutExtractor;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Tracks who is responsible for building each sub-region of a shared schematic.
 *
 * <p>The region layout is read from the litematic by this service, on its own
 * executor, rather than taken from the material extractor. Build management and
 * material tracking are switched on independently, and a feature that stops
 * working because an unrelated one was turned off is not independent. The cost
 * is one extra decode per placement when a server starts; the shared decoding
 * primitives keep it to that.
 */
public class BuildService extends AbstractService {
    public static final boolean ENABLED_DEFAULT = true;
    private static final String CONFIG_KEY = "build";
    private static final int MAX_QUEUED_EXTRACTIONS = 64;
    private static final Logger LOGGER = LogManager.getLogger(BuildService.class);

    private final Map<UUID, ServerPlacement> placements = new HashMap<>();
    private final Map<UUID, Map<String, Long>> regionBlocks = new HashMap<>();

    private boolean enabled = ENABLED_DEFAULT;

    private final ExecutorService layoutExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_EXTRACTIONS),
            runnable -> {
                final Thread thread = new Thread(runnable, "syncmatica_r-build-layout");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final Queue<LayoutResult> completedLayouts = new ConcurrentLinkedQueue<>();
    private final Map<UUID, UUID> pendingLayoutTokens = new HashMap<>();
    private final ArrayDeque<UUID> deferredLayouts = new ArrayDeque<>();
    private final Set<UUID> deferredLayoutIds = new HashSet<>();

    /** What a claim toggle did, so the caller can pick the right reply. */
    public enum ClaimOutcome {
        CLAIMED,
        RELEASED,
        ALREADY_CLAIMED,
        UNKNOWN_REGION,
        DISABLED
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void attachPlacement(final ServerPlacement placement) {
        placements.put(placement.getId(), placement);
        seedFromExistingSnapshot(placement);
        if (enabled) {
            scheduleLayoutLoad(placement);
        }
    }

    public void detachPlacement(final ServerPlacement placement) {
        final UUID placementId = placement.getId();
        placements.remove(placementId);
        regionBlocks.remove(placementId);
        pendingLayoutTokens.remove(placementId);
        deferredLayoutIds.remove(placementId);
        deferredLayouts.removeIf(placementId::equals);
    }

    public void tick(final MinecraftServer server) {
        if (!enabled) {
            return;
        }
        applyCompletedLayouts();
        scheduleDeferredLayout();
    }

    public ClaimOutcome toggleClaim(final ServerPlacement placement, final String regionName,
                                    final PlayerIdentifier player) {
        if (!enabled) {
            return ClaimOutcome.DISABLED;
        }
        if (placement == null || player == null) {
            return ClaimOutcome.UNKNOWN_REGION;
        }
        final BuildRegion region = placement.getBuildRegions().get(regionName);
        if (region == null) {
            return ClaimOutcome.UNKNOWN_REGION;
        }

        final ClaimOutcome outcome;
        if (region.hasClaimer(player)) {
            region.removeClaimer(player);
            outcome = ClaimOutcome.RELEASED;
        } else if (region.isClaimed()) {
            // One responsible player per region; taking over needs an explicit release.
            return ClaimOutcome.ALREADY_CLAIMED;
        } else {
            region.addClaimer(player);
            outcome = ClaimOutcome.CLAIMED;
        }

        placement.setLastModifiedBy(player);
        placement.touchModified(System.currentTimeMillis());
        persistAndBroadcast(placement);
        return outcome;
    }

    /**
     * @return the current holder of a region, or null when nobody claimed it or
     *         the region is unknown
     */
    public PlayerIdentifier getClaimant(final ServerPlacement placement, final String regionName) {
        if (placement == null) {
            return null;
        }
        final BuildRegion region = placement.getBuildRegions().get(regionName);
        if (region == null || !region.isClaimed()) {
            return null;
        }
        return region.getClaimants().iterator().next();
    }

    /**
     * Adopts a freshly decoded region layout. Claims already made against a
     * region name survive, which is what keeps assignments intact when a
     * schematic is re-extracted after a restart or a re-share.
     */
    public void replaceRegions(final UUID placementId, final Map<String, Long> blocks) {
        if (!enabled) {
            return;
        }
        final Map<String, Long> replacement = capped(blocks);
        if (replacement.equals(regionBlocks.get(placementId))) {
            return;
        }
        regionBlocks.put(placementId, replacement);
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            rebuildSnapshot(placement);
        }
    }

    private void scheduleLayoutLoad(final ServerPlacement placement) {
        if (placement == null
                || context == null
                || pendingLayoutTokens.containsKey(placement.getId())
                || deferredLayoutIds.contains(placement.getId())) {
            return;
        }
        final File file = context.getFileStorage().getLocalLitematic(placement);
        if (file == null || !file.isFile()) {
            LOGGER.debug("No local litematic for placement '{}', region layout stays unknown", placement.getName());
            return;
        }
        final long byteLimit = context.getMaxTransferBytes();
        if (file.length() > byteLimit) {
            LOGGER.warn("Skipping region layout for '{}' ({} bytes exceeds limit {} bytes)",
                    placement.getName(), file.length(), byteLimit);
            return;
        }
        final UUID placementId = placement.getId();
        final UUID placementHash = placement.getHash();
        final UUID token = UUID.randomUUID();
        pendingLayoutTokens.put(placementId, token);
        try {
            layoutExecutor.execute(() -> completedLayouts.add(new LayoutResult(
                    placementId,
                    placementHash,
                    token,
                    RegionLayoutExtractor.extractBlockCounts(file, byteLimit))));
        } catch (final RejectedExecutionException exception) {
            pendingLayoutTokens.remove(placementId);
            if (placements.containsKey(placementId) && deferredLayoutIds.add(placementId)) {
                deferredLayouts.addLast(placementId);
            }
            LOGGER.debug("Region layout queue is full or shutting down", exception);
        }
    }

    private void scheduleDeferredLayout() {
        final UUID placementId = deferredLayouts.pollFirst();
        if (placementId == null) {
            return;
        }
        deferredLayoutIds.remove(placementId);
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            scheduleLayoutLoad(placement);
        }
    }

    private void applyCompletedLayouts() {
        LayoutResult result;
        while ((result = completedLayouts.poll()) != null) {
            if (!result.token.equals(pendingLayoutTokens.get(result.placementId))) {
                continue;
            }
            pendingLayoutTokens.remove(result.placementId);
            final ServerPlacement placement = placements.get(result.placementId);
            // A placement re-shared mid-read now points at a different file, so
            // the layout that came back describes something else.
            if (placement == null || !result.placementHash.equals(placement.getHash())) {
                continue;
            }
            if (result.blocks.isEmpty()) {
                LOGGER.debug("Region layout of '{}' came back empty", placement.getName());
                continue;
            }
            replaceRegions(result.placementId, result.blocks);
        }
    }

    private void rebuildSnapshot(final ServerPlacement placement) {
        final Map<String, Long> blocks = regionBlocks.getOrDefault(placement.getId(), Collections.emptyMap());
        final BuildRegionState snapshot = placement.getBuildRegions();

        final Map<String, Collection<PlayerIdentifier>> previousClaimants = new HashMap<>();
        for (final BuildRegion region : snapshot.getRegions()) {
            previousClaimants.put(region.getRegionName(), new ArrayList<>(region.getClaimants()));
        }
        snapshot.clear();
        for (final Map.Entry<String, Long> entry : blocks.entrySet()) {
            final BuildRegion region = snapshot.getOrCreate(entry.getKey(), entry.getValue());
            final Collection<PlayerIdentifier> claim = previousClaimants.get(entry.getKey());
            if (claim != null) {
                claim.forEach(region::addClaimer);
            }
        }
        persistAndBroadcast(placement);
    }

    /**
     * The layout only ever arrives from an extraction, which takes a moment. Until
     * then the persisted regions stand on their own so claims made before a
     * restart stay visible rather than blinking out and back.
     */
    private void seedFromExistingSnapshot(final ServerPlacement placement) {
        final Map<String, Long> blocks = new LinkedHashMap<>();
        for (final BuildRegion region : placement.getBuildRegions().getRegions()) {
            blocks.put(region.getRegionName(), region.getRequiredBlocks());
        }
        if (!blocks.isEmpty()) {
            regionBlocks.put(placement.getId(), blocks);
        }
    }

    private void persistAndBroadcast(final ServerPlacement placement) {
        if (context == null) {
            return;
        }
        context.getSyncmaticManager().updateServerPlacement(placement);
        if (context.getCommunicationManager() instanceof ServerCommunicationManager) {
            ((ServerCommunicationManager) context.getCommunicationManager()).broadcastPlacementUpdate(placement);
        }
    }

    private Map<String, Long> capped(final Map<String, Long> blocks) {
        final Map<String, Long> replacement = new LinkedHashMap<>();
        if (blocks == null) {
            return replacement;
        }
        for (final Map.Entry<String, Long> region : blocks.entrySet()) {
            if (replacement.size() >= ProtocolLimits.MAX_REGION_ENTRIES) {
                break;
            }
            final String name = region.getKey();
            if (name == null || name.isEmpty() || name.length() > ProtocolLimits.MAX_SUBREGION_NAME_LENGTH) {
                continue;
            }
            replacement.put(name, Math.max(0L, region.getValue() == null ? 0L : region.getValue()));
        }
        return replacement;
    }

    private static final class LayoutResult {
        private final UUID placementId;
        private final UUID placementHash;
        private final UUID token;
        private final Map<String, Long> blocks;

        private LayoutResult(final UUID placementId, final UUID placementHash, final UUID token,
                             final Map<String, Long> blocks) {
            this.placementId = placementId;
            this.placementHash = placementHash;
            this.token = token;
            this.blocks = blocks;
        }
    }

    @Override
    public String getConfigKey() {
        return CONFIG_KEY;
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        configuration.saveBoolean("enabled", ENABLED_DEFAULT);
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", value -> enabled = value);
    }

    @Override
    public void startup() {
    }

    @Override
    public void shutdown() {
        placements.clear();
        regionBlocks.clear();
        pendingLayoutTokens.clear();
        deferredLayouts.clear();
        deferredLayoutIds.clear();
        completedLayouts.clear();
        layoutExecutor.shutdownNow();
    }
}
