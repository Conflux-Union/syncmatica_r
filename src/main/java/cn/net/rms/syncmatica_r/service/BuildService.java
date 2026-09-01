package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.build_management.BuildScanStore;
import cn.net.rms.syncmatica_r.build_management.BuildScanTracker;
import cn.net.rms.syncmatica_r.build_management.RegionBlocks;
import cn.net.rms.syncmatica_r.build_management.RegionBounds;
import cn.net.rms.syncmatica_r.build_management.RegionBoundsResolver;
import cn.net.rms.syncmatica_r.build_management.RegionColumnHeights;
import cn.net.rms.syncmatica_r.build_management.RegionGeometry;
import cn.net.rms.syncmatica_r.build_management.RegionLayoutExtractor;
import cn.net.rms.syncmatica_r.build_management.RegionLocalMapper;
import cn.net.rms.syncmatica_r.build_management.RegionScanCache;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import cn.net.rms.syncmatica_r.util.WorldResolver;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
    public static final boolean COMPLETION_ENABLED_DEFAULT = true;
    public static final int SCAN_BLOCKS_PER_TICK_DEFAULT = 4096;
    public static final int SCAN_INTERVAL_DEFAULT = 1200;
    public static final int FULL_RESCAN_INTERVAL_DEFAULT = 36_000;
    private static final int MAX_SCAN_BLOCKS_PER_TICK = 65_536;
    private static final int MIN_SCAN_BLOCKS_PER_TICK = 64;
    private static final int MIN_SCAN_INTERVAL = 100;
    private static final int MIN_FULL_RESCAN_INTERVAL = 1200;
    private static final String CONFIG_KEY = "build";
    private static final int MAX_QUEUED_EXTRACTIONS = 64;
    /** How often the tracker is told where the placements are. */
    private static final int COVERAGE_REBUILD_TICKS = 100;
    /**
     * Ceiling on the decoded block data held between passes. Re-reading the
     * litematic for every pass is what this replaces; holding every schematic on
     * a busy server forever is not an improvement on it.
     */
    private static final long MAX_CACHED_BLOCK_BYTES = 64L * 1024L * 1024L;
    /** How many columns a scan may pass over for the price of reading one block. */
    private static final int COLUMNS_PER_BUDGET_UNIT = 16;
    private static final Logger LOGGER = LogManager.getLogger(BuildService.class);

    private final Map<UUID, ServerPlacement> placements = new HashMap<>();
    private final Map<UUID, Map<String, Long>> regionBlocks = new HashMap<>();
    private final Map<UUID, Map<String, RegionGeometry>> regionGeometry = new HashMap<>();

    private boolean enabled = ENABLED_DEFAULT;
    private boolean completionEnabled = COMPLETION_ENABLED_DEFAULT;
    private int scanBlocksPerTick = SCAN_BLOCKS_PER_TICK_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private int fullRescanInterval = FULL_RESCAN_INTERVAL_DEFAULT;
    private long scanTick;
    private int fullRescanCounter;
    private int coverageCounter = COVERAGE_REBUILD_TICKS;

    private final ArrayDeque<UUID> scanQueue = new ArrayDeque<>();
    private final Set<UUID> queuedForScan = new HashSet<>();
    private final Map<UUID, Long> lastScanTick = new HashMap<>();
    private final BuildScanTracker tracker = new BuildScanTracker();
    private CompletionScan activeScan;
    private BuildScanStore scanStore;
    private UUID pendingBlockLoad;
    private UUID pendingBlockToken;
    private final Queue<LoadedRegionBlocks> loadedBlocks = new ConcurrentLinkedQueue<>();
    /** Access ordered, so the oldest unused placement is the first one evicted. */
    private final Map<UUID, CachedBlocks> blocksCache = new LinkedHashMap<>(16, 0.75f, true);

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

    UUID pendingLayoutToken(final UUID placementId) {
        return pendingLayoutTokens.get(placementId);
    }

    boolean hasPendingCompletionScan(final UUID placementId) {
        return tracker.hasWork(placementId);
    }

    public void attachPlacement(final ServerPlacement placement) {
        placements.put(placement.getId(), placement);
        seedFromExistingSnapshot(placement);
        if (enabled) {
            loadScanData(placement);
            scheduleLayoutLoad(placement);
            // Nothing was watching this placement while the server was down, so
            // the first pass has to take the counts on trust from nobody.
            tracker.requestFullPass(placement.getId());
        }
    }

    public void detachPlacement(final ServerPlacement placement) {
        final UUID placementId = placement.getId();
        placements.remove(placementId);
        regionBlocks.remove(placementId);
        regionGeometry.remove(placementId);
        pendingLayoutTokens.remove(placementId);
        deferredLayoutIds.remove(placementId);
        deferredLayouts.removeIf(placementId::equals);
        lastScanTick.remove(placementId);
        blocksCache.remove(placementId);
        cancelScanFor(placementId);
        tracker.forget(placementId);
        final BuildScanStore store = scanStore();
        if (store != null) {
            store.delete(placementId);
        }
    }

    /**
     * The per-chunk counts live in the world they were measured in, not beside
     * the placements: a world restored from a backup brings its own counts with
     * it, which is the only way the measurement survives a rollback without
     * somebody remembering to order a rescan.
     */
    private BuildScanStore scanStore() {
        if (scanStore == null) {
            final File worldFolder = context == null ? null : context.getWorldFolder();
            if (worldFolder == null) {
                return null;
            }
            scanStore = new BuildScanStore(worldFolder);
        }
        return scanStore;
    }

    private void loadScanData(final ServerPlacement placement) {
        final BuildScanStore store = scanStore();
        if (store != null) {
            store.load(placement.getId(), placement.getBuildRegions());
        }
    }

    private void saveScanData(final ServerPlacement placement) {
        final BuildScanStore store = scanStore();
        if (store != null) {
            store.save(placement.getId(), placement.getBuildRegions());
        }
    }

    public void tick(final MinecraftServer server) {
        if (!enabled) {
            return;
        }
        applyCompletedLayouts();
        scheduleDeferredLayout();
        tickCompletionScan(server);
    }

    /**
     * @return the world box of every region of this placement, empty while the
     *         shapes are still unknown
     */
    public Map<String, RegionBounds> getRegionBounds(final ServerPlacement placement) {
        if (placement == null) {
            return Collections.emptyMap();
        }
        final Map<String, RegionGeometry> geometry = regionGeometry.get(placement.getId());
        if (geometry == null || geometry.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, SubRegionPlacementModification> overrides = overridesOf(placement);
        final Map<String, RegionBounds> bounds = new LinkedHashMap<>();
        for (final Map.Entry<String, RegionGeometry> region : geometry.entrySet()) {
            final RegionBounds resolved = RegionBoundsResolver.resolve(
                    region.getValue(), placement.getPosition(), placement.getRotation(), placement.getMirror(),
                    overrides.get(region.getKey()));
            if (resolved != null) {
                bounds.put(region.getKey(), resolved);
            }
        }
        return bounds;
    }

    private static Map<String, SubRegionPlacementModification> overridesOf(final ServerPlacement placement) {
        final Map<String, SubRegionPlacementModification> data =
                placement.getSubRegionData() == null ? null : placement.getSubRegionData().getModificationData();
        return data == null ? Collections.emptyMap() : data;
    }

    /**
     * Drives the completion scan. At most one placement is scanned at a time and
     * each tick spends a fixed block budget, so a large schematic costs a long
     * wall-clock scan rather than a server stall.
     *
     * <p>What gets scanned is decided by {@link BuildScanTracker}: a placement
     * nothing has touched is not queued at all, and a queued one only re-counts
     * the columns that were reported changed. Columns nobody has managed to count
     * yet — the ones that were out of view every time — are retried on the
     * {@code scan_interval} timer, and {@code full_rescan_interval} re-counts
     * everything to recover from edits the tracker never saw.
     */
    private void tickCompletionScan(final MinecraftServer server) {
        if (!completionEnabled || server == null) {
            return;
        }
        scanTick++;
        refreshCoverage(server);
        adoptLoadedBlocks(server);
        if (activeScan != null) {
            activeScan.process(scanBlocksPerTick);
            if (activeScan.isFinished()) {
                final CompletionScan finished = activeScan;
                activeScan = null;
                applyScanResults(finished);
            }
            return;
        }
        if (pendingBlockLoad != null) {
            return;
        }
        if (fullRescanInterval > 0 && ++fullRescanCounter >= fullRescanInterval) {
            fullRescanCounter = 0;
            tracker.requestFullPass();
        }
        enqueueScans();
        requestNextScan(server);
    }

    /**
     * Tells the tracker where the placements currently sit. Rebuilt on a timer
     * rather than hooked to everything that can move a placement: it is cheap,
     * and coverage that quietly stops matching is worse than coverage that lags
     * a few seconds.
     */
    private void refreshCoverage(final MinecraftServer server) {
        if (++coverageCounter < COVERAGE_REBUILD_TICKS) {
            return;
        }
        coverageCounter = 0;
        final List<BuildScanTracker.RegionColumns> coverage = new ArrayList<>();
        for (final ServerPlacement placement : placements.values()) {
            final Map<String, RegionBounds> bounds = getRegionBounds(placement);
            if (bounds.isEmpty()) {
                continue;
            }
            final ServerWorld world = WorldResolver.resolve(server, placement.getDimension());
            if (world == null) {
                continue;
            }
            for (final RegionBounds region : bounds.values()) {
                coverage.add(new BuildScanTracker.RegionColumns(placement.getId(), world, region));
            }
        }
        tracker.replaceCoverage(coverage);
    }

    private void enqueueScans() {
        for (final UUID placementId : regionGeometry.keySet()) {
            if (queuedForScan.contains(placementId)) {
                continue;
            }
            final ServerPlacement placement = placements.get(placementId);
            if (placement == null) {
                continue;
            }
            if (!tracker.hasWork(placementId) && !dueForUncountedRetry(placementId, placement)) {
                continue;
            }
            queuedForScan.add(placementId);
            scanQueue.addLast(placementId);
        }
    }

    /**
     * A column out of view every time it was reached is never reported changed —
     * nothing changed in it — so the only way it ever gets a number is to go back
     * and look. That retry is what {@code scan_interval} paces.
     */
    private boolean dueForUncountedRetry(final UUID placementId, final ServerPlacement placement) {
        final Long last = lastScanTick.get(placementId);
        if (last != null && scanTick - last < scanInterval) {
            return false;
        }
        for (final BuildRegion region : placement.getBuildRegions().getRegions()) {
            final RegionScanCache cache = region.getScanCache();
            if (cache == null || cache.getCountedColumnCount() < cache.getColumnCount()) {
                return true;
            }
        }
        return false;
    }

    private void requestNextScan(final MinecraftServer server) {
        while (!scanQueue.isEmpty()) {
            final UUID placementId = scanQueue.pollFirst();
            queuedForScan.remove(placementId);
            final ServerPlacement placement = placements.get(placementId);
            if (placement == null || !regionGeometry.containsKey(placementId) || context == null) {
                continue;
            }
            // Resolved before anything is decoded: a placement in a dimension
            // this server does not have cannot be scanned, and reading its file
            // to discover that every tick would be the whole cost of the feature.
            final ServerWorld world = WorldResolver.resolve(server, placement.getDimension());
            if (world == null) {
                continue;
            }
            final CachedBlocks cached = blocksCache.get(placementId);
            if (cached != null && cached.placementHash.equals(placement.getHash())) {
                if (startScan(placement, world, cached.blocks)) {
                    return;
                }
                continue;
            }
            final File file = context.getFileStorage().getLocalLitematic(placement);
            if (file == null || !file.isFile()) {
                continue;
            }
            final UUID token = UUID.randomUUID();
            final UUID placementHash = placement.getHash();
            final long maxBytes = context.getMaxTransferBytes();
            pendingBlockLoad = placementId;
            pendingBlockToken = token;
            try {
                layoutExecutor.execute(() -> loadedBlocks.add(new LoadedRegionBlocks(
                        placementId, placementHash, token,
                        RegionLayoutExtractor.extractRegionBlocks(file, maxBytes))));
            } catch (final RejectedExecutionException exception) {
                pendingBlockLoad = null;
                pendingBlockToken = null;
                LOGGER.debug("Region block loader is busy or shutting down", exception);
            }
            return;
        }
    }

    /** @return false when the placement has no layout to scan against */
    private boolean startScan(final ServerPlacement placement, final ServerWorld world,
                              final Map<String, RegionBlocks> blocks) {
        final Map<String, RegionGeometry> geometry = regionGeometry.get(placement.getId());
        if (geometry == null || blocks.isEmpty()) {
            return false;
        }
        activeScan = new CompletionScan(placement, world, geometry, blocks, tracker.take(placement.getId()));
        lastScanTick.put(placement.getId(), scanTick);
        return true;
    }

    private void adoptLoadedBlocks(final MinecraftServer server) {
        LoadedRegionBlocks loaded;
        while ((loaded = loadedBlocks.poll()) != null) {
            if (!loaded.token.equals(pendingBlockToken)) {
                continue;
            }
            pendingBlockLoad = null;
            pendingBlockToken = null;
            final ServerPlacement placement = placements.get(loaded.placementId);
            // A placement re-shared mid-read now points at a different file.
            if (placement == null || loaded.blocks.isEmpty()
                    || !loaded.placementHash.equals(placement.getHash())) {
                continue;
            }
            final ServerWorld world = WorldResolver.resolve(server, placement.getDimension());
            if (world == null) {
                continue;
            }
            cacheBlocks(loaded.placementId, loaded.placementHash, loaded.blocks);
            startScan(placement, world, loaded.blocks);
        }
    }

    /**
     * Keeps a decoded schematic around for the next pass. Decoding is by far the
     * most expensive part of a pass that finds nothing changed, and with the
     * tracker in place most passes find exactly that.
     */
    private void cacheBlocks(final UUID placementId, final UUID placementHash,
                             final Map<String, RegionBlocks> blocks) {
        long bytes = 0L;
        for (final RegionBlocks region : blocks.values()) {
            bytes += region.getStoredBytes();
        }
        // One placement over the whole budget would evict everything else and
        // still not fit, so it is decoded per pass instead.
        if (bytes > MAX_CACHED_BLOCK_BYTES) {
            blocksCache.remove(placementId);
            return;
        }
        blocksCache.remove(placementId);
        blocksCache.put(placementId, new CachedBlocks(placementHash, blocks, bytes));
        long total = 0L;
        for (final CachedBlocks entry : blocksCache.values()) {
            total += entry.bytes;
        }
        final Iterator<Map.Entry<UUID, CachedBlocks>> iterator = blocksCache.entrySet().iterator();
        while (total > MAX_CACHED_BLOCK_BYTES && iterator.hasNext()) {
            total -= iterator.next().getValue().bytes;
            iterator.remove();
        }
    }

    private void applyScanResults(final CompletionScan scan) {
        final ServerPlacement placement = placements.get(scan.placementId);
        if (placement == null || scan.poseChanged(placement)) {
            // The counts describe a box the region no longer occupies. Nothing
            // was measured, so nothing was answered either.
            tracker.restore(scan.placementId, scan.request);
            return;
        }
        if (scan.results.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        boolean changed = false;
        for (final Map.Entry<String, Long> result : scan.results.entrySet()) {
            final BuildRegion region = placement.getBuildRegions().get(result.getKey());
            if (region != null) {
                region.recordScan(result.getValue(), now);
                changed = true;
            }
        }
        if (changed) {
            persistAndBroadcast(placement);
            saveScanData(placement);
        }
    }

    /**
     * Forgets everything measured about a placement and queues a fresh pass.
     *
     * <p>Counting per chunk column rests on a block never changing while its
     * chunk is unloaded. Nothing inside the game can break that, and a world
     * restored from a backup brings its own counts back with it, so what is left
     * is editing the world with the server down or writing region files with
     * another tool. This is the way back from those.
     *
     * @return false when build management or completion tracking is switched off
     */
    public boolean rescan(final ServerPlacement placement) {
        if (!enabled || !completionEnabled || placement == null) {
            return false;
        }
        for (final BuildRegion region : placement.getBuildRegions().getRegions()) {
            region.forgetScan();
        }
        final UUID placementId = placement.getId();
        cancelScanFor(placementId);
        final BuildScanStore store = scanStore();
        if (store != null) {
            store.delete(placementId);
        }
        lastScanTick.remove(placementId);
        tracker.requestFullPass(placementId);
        if (regionGeometry.containsKey(placementId) && queuedForScan.add(placementId)) {
            scanQueue.addFirst(placementId);
        }
        persistAndBroadcast(placement);
        return true;
    }

    private void cancelScanFor(final UUID placementId) {
        if (activeScan != null && activeScan.placementId.equals(placementId)) {
            tracker.restore(placementId, activeScan.request);
            activeScan = null;
        }
        if (placementId.equals(pendingBlockLoad)) {
            pendingBlockLoad = null;
            pendingBlockToken = null;
        }
        if (queuedForScan.remove(placementId)) {
            scanQueue.remove(placementId);
        }
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
    public void replaceGeometry(final UUID placementId, final Map<String, RegionGeometry> geometry) {
        if (!enabled) {
            return;
        }
        if (geometry == null || geometry.isEmpty()) {
            regionGeometry.remove(placementId);
        } else {
            regionGeometry.put(placementId, new LinkedHashMap<>(geometry));
        }
        cancelScanFor(placementId);
        // A fresh layout means a fresh decode, and nothing measured against the
        // old one can be trusted to describe the new one.
        blocksCache.remove(placementId);
        lastScanTick.remove(placementId);
        tracker.requestFullPass(placementId);
    }

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
                    RegionLayoutExtractor.extractLayout(file, byteLimit))));
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
            if (result.layout.isEmpty()) {
                LOGGER.debug("Region layout of '{}' came back empty", placement.getName());
                continue;
            }
            replaceGeometry(result.placementId, result.layout.getGeometry());
            replaceRegions(result.placementId, result.layout.getBlockCounts());
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

    /**
     * Walks a placement's regions one chunk column at a time, comparing the block
     * that is there against the one the schematic asks for.
     *
     * <p>Only the block is compared, not its full state: orientation is not
     * tracked, matching how the material list counts by item rather than by
     * state. Each column's result is kept in the region's {@link RegionScanCache}
     * rather than in this pass, so a column out of view keeps the number it was
     * last given instead of costing the region its whole measurement.
     */
    private final class CompletionScan {
        private final ServerPlacement placement;
        private final UUID placementId;
        private final ServerWorld world;
        private final BlockPos origin;
        private final BlockRotation rotation;
        private final BlockMirror mirror;
        private final Map<String, SubRegionPlacementModification> overrides;
        private final Map<String, RegionGeometry> geometry;
        private final Map<String, RegionBlocks> blocks;
        private final BuildScanTracker.ScanRequest request;
        private final Iterator<String> remainingRegions;
        private final Map<String, Long> results = new LinkedHashMap<>();
        private final int[] span = new int[2];

        private String currentRegion;
        private RegionGeometry currentGeometry;
        private RegionBlocks currentBlocks;
        private Block[] currentPalette;
        private RegionScanCache currentCache;
        private RegionLocalMapper currentMapper;
        private RegionColumnHeights currentHeights;
        private Iterator<Long> remainingColumns;

        private boolean columnActive;
        private int skippedColumns;
        private int columnX;
        private int columnZ;
        private WorldChunk currentChunk;
        private Iterator<BlockPos> positions;
        private int columnMatched;
        private boolean finished;

        private CompletionScan(final ServerPlacement placement, final ServerWorld world,
                               final Map<String, RegionGeometry> geometry,
                               final Map<String, RegionBlocks> blocks,
                               final BuildScanTracker.ScanRequest request) {
            this.placement = placement;
            placementId = placement.getId();
            this.world = world;
            origin = placement.getPosition();
            rotation = placement.getRotation();
            mirror = placement.getMirror();
            overrides = new LinkedHashMap<>(overridesOf(placement));
            this.geometry = geometry;
            this.blocks = blocks;
            this.request = request;
            final List<String> names = new ArrayList<>(geometry.keySet());
            remainingRegions = names.iterator();
        }

        /**
         * @return true when the schematic no longer sits where these counts were
         *         taken, whether the whole placement moved or one sub-region did
         */
        private boolean poseChanged(final ServerPlacement placement) {
            return !origin.equals(placement.getPosition())
                    || rotation != placement.getRotation()
                    || mirror != placement.getMirror()
                    || !sameOverrides(overrides, overridesOf(placement));
        }

        private boolean isFinished() {
            return finished;
        }

        private void process(final int budget) {
            // Chunks are loaded and unloaded on the server thread, so the loaded
            // set cannot change while this runs: checking the column once per
            // tick is as exact as checking it once per block, and far cheaper.
            if (columnActive && !world.isChunkLoaded(columnX, columnZ)) {
                abandonColumn();
            }
            int remaining = Math.max(1, budget);
            while (remaining > 0) {
                if (positions != null && positions.hasNext()) {
                    countAt(positions.next());
                    remaining--;
                    continue;
                }
                // Examining a column costs a unit too, so a region spread over
                // thousands of columns cannot stall a tick. Passing over one
                // costs a hash lookup rather than a block read though, and
                // charging that a full unit would leave a wide region no budget
                // to spend on the handful of columns that did change.
                if (commitColumn()) {
                    remaining--;
                } else if (++skippedColumns >= COLUMNS_PER_BUDGET_UNIT) {
                    skippedColumns = 0;
                    remaining--;
                }
                if (remainingColumns != null && remainingColumns.hasNext()) {
                    beginColumn(remainingColumns.next());
                    continue;
                }
                closeCurrentRegion();
                if (!remainingRegions.hasNext()) {
                    finished = true;
                    return;
                }
                beginRegion(remainingRegions.next());
            }
        }

        private void countAt(final BlockPos pos) {
            final int localX = currentMapper.localX(pos.getX(), pos.getZ());
            final int localY = currentMapper.localY(pos.getY());
            final int localZ = currentMapper.localZ(pos.getX(), pos.getZ());
            if (!currentMapper.containsLocal(localX, localY, localZ)) {
                return;
            }
            final int paletteIndex = currentBlocks.paletteIndexAt(localX, localY, localZ);
            if (paletteIndex < 0) {
                return;
            }
            final Block expected = currentPalette[paletteIndex];
            // Read through the chunk the column already resolved to, rather than
            // making the world find it again for every position in it.
            if (expected != null && currentChunk.getBlockState(pos).getBlock() == expected) {
                columnMatched++;
            }
        }

        private void beginRegion(final String regionName) {
            currentRegion = regionName;
            currentGeometry = geometry.get(regionName);
            currentBlocks = blocks.get(regionName);
            currentPalette = null;
            currentCache = null;
            currentMapper = null;
            currentHeights = null;
            remainingColumns = null;
            final BuildRegion region = placement.getBuildRegions().get(regionName);
            if (region == null || currentGeometry == null || currentBlocks == null) {
                return;
            }
            final SubRegionPlacementModification override = overrides.get(regionName);
            final RegionBounds bounds =
                    RegionBoundsResolver.resolve(currentGeometry, origin, rotation, mirror, override);
            final RegionLocalMapper mapper =
                    RegionLocalMapper.of(currentGeometry, origin, rotation, mirror, override);
            if (bounds == null || mapper == null) {
                return;
            }
            RegionScanCache cache = region.getScanCache();
            if (cache == null || !cache.matches(bounds)) {
                cache = new RegionScanCache(bounds);
                region.setScanCache(cache);
            }
            currentCache = cache;
            currentPalette = resolvePalette(currentBlocks.getPalette());
            currentMapper = mapper;
            currentHeights = currentBlocks.getColumnHeights();
            remainingColumns = cache.columns();
        }

        private void beginColumn(final long packedColumn) {
            columnX = RegionScanCache.columnX(packedColumn);
            columnZ = RegionScanCache.columnZ(packedColumn);
            columnMatched = 0;
            columnActive = false;
            positions = null;
            currentChunk = null;
            // A column that already has a number and that nothing was reported to
            // have touched still holds that number.
            if (currentCache.isCounted(columnX, columnZ) && !request.covers(packedColumn)) {
                return;
            }
            // An unloaded column keeps whatever it was last counted as, which is
            // still what is there: nothing can have been built inside it since.
            if (!world.isChunkLoaded(columnX, columnZ)) {
                return;
            }
            final RegionBounds box = currentCache.columnBounds(columnX, columnZ);
            if (box == null) {
                return;
            }
            final BlockPos min = box.getMin();
            final BlockPos max = box.getMax();
            resolveWorldYSpan(min, max);
            // A column the schematic leaves empty has been examined and found to
            // hold nothing, which is an answer and not the absence of one.
            columnActive = true;
            if (span[0] > span[1]) {
                return;
            }
            currentChunk = world.getChunk(columnX, columnZ);
            positions = BlockPos.iterate(
                    new BlockPos(min.getX(), span[0], min.getZ()),
                    new BlockPos(max.getX(), span[1], max.getZ())).iterator();
        }

        /**
         * Narrows a column to the layers the schematic actually fills, leaving
         * the world Y range to walk in {@link #span}. An empty range says there
         * is nothing here to walk.
         *
         * <p>The index is keyed by schematic column, so the world box has to be
         * carried into region coordinates first. Rotation and mirroring only ever
         * permute and flip the horizontal axes, so a world box maps onto a region
         * box and the corners are enough to find it.
         */
        private void resolveWorldYSpan(final BlockPos min, final BlockPos max) {
            if (currentHeights == null) {
                span[0] = min.getY();
                span[1] = max.getY();
                return;
            }
            final boolean occupied = currentHeights.occupiedSpan(
                    currentMapper.lowestLocalX(min.getX(), min.getZ(), max.getX(), max.getZ()),
                    currentMapper.lowestLocalZ(min.getX(), min.getZ(), max.getX(), max.getZ()),
                    currentMapper.highestLocalX(min.getX(), min.getZ(), max.getX(), max.getZ()),
                    currentMapper.highestLocalZ(min.getX(), min.getZ(), max.getX(), max.getZ()),
                    span);
            if (!occupied) {
                span[0] = Integer.MAX_VALUE;
                span[1] = Integer.MIN_VALUE;
                return;
            }
            // A region built upside down maps its lowest layer to the highest
            // world Y, so order the pair after mapping rather than before.
            final int oneEnd = currentMapper.worldY(span[0]);
            final int otherEnd = currentMapper.worldY(span[1]);
            span[0] = Math.max(min.getY(), Math.min(oneEnd, otherEnd));
            span[1] = Math.min(max.getY(), Math.max(oneEnd, otherEnd));
        }

        /**
         * Publishes a column only once every position in it has been read.
         *
         * @return true when this column was examined rather than passed over
         */
        private boolean commitColumn() {
            final boolean examined = columnActive;
            if (examined) {
                currentCache.record(columnX, columnZ, columnMatched);
            }
            abandonColumn();
            return examined;
        }

        private void abandonColumn() {
            columnActive = false;
            positions = null;
            currentChunk = null;
            columnMatched = 0;
        }

        private void closeCurrentRegion() {
            if (currentRegion != null && currentCache != null) {
                results.put(currentRegion, currentCache.getTotal());
            }
            currentRegion = null;
            currentCache = null;
            remainingColumns = null;
        }
    }

    /**
     * {@link SubRegionPlacementModification} carries no equality of its own, and
     * the pose it describes is exactly what decides whether counts taken earlier
     * still apply.
     */
    private static boolean sameOverrides(final Map<String, SubRegionPlacementModification> left,
                                         final Map<String, SubRegionPlacementModification> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (final Map.Entry<String, SubRegionPlacementModification> entry : left.entrySet()) {
            final SubRegionPlacementModification other = right.get(entry.getKey());
            final SubRegionPlacementModification mine = entry.getValue();
            if (other == null
                    || other.rotation != mine.rotation
                    || other.mirror != mine.mirror
                    || !java.util.Objects.equals(other.position, mine.position)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves palette identifiers to blocks once per region. Entries without an
     * item form are dropped here, which is the last of the rules that keep the
     * completion count aligned with what the region asked for.
     */
    private static Block[] resolvePalette(final Identifier[] palette) {
        final Block[] resolved = new Block[palette.length];
        for (int index = 0; index < palette.length; index++) {
            if (palette[index] == null) {
                continue;
            }
            final Block block = Registry.BLOCK.getOrEmpty(palette[index]).orElse(Blocks.AIR);
            if (block != Blocks.AIR && block.asItem() != Items.AIR) {
                resolved[index] = block;
            }
        }
        return resolved;
    }

    private static final class LayoutResult {
        private final UUID placementId;
        private final UUID placementHash;
        private final UUID token;
        private final RegionLayoutExtractor.RegionLayout layout;

        private LayoutResult(final UUID placementId, final UUID placementHash, final UUID token,
                             final RegionLayoutExtractor.RegionLayout layout) {
            this.placementId = placementId;
            this.placementHash = placementHash;
            this.token = token;
            this.layout = layout;
        }
    }

    private static final class LoadedRegionBlocks {
        private final UUID placementId;
        private final UUID placementHash;
        private final UUID token;
        private final Map<String, RegionBlocks> blocks;

        private LoadedRegionBlocks(final UUID placementId, final UUID placementHash, final UUID token,
                                   final Map<String, RegionBlocks> blocks) {
            this.placementId = placementId;
            this.placementHash = placementHash;
            this.token = token;
            this.blocks = blocks;
        }
    }

    /** A decoded schematic held for the next pass, and what it is holding. */
    private static final class CachedBlocks {
        private final UUID placementHash;
        private final Map<String, RegionBlocks> blocks;
        private final long bytes;

        private CachedBlocks(final UUID placementHash, final Map<String, RegionBlocks> blocks, final long bytes) {
            this.placementHash = placementHash;
            this.blocks = blocks;
            this.bytes = bytes;
        }
    }

    @Override
    public String getConfigKey() {
        return CONFIG_KEY;
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        final ConfigRegistry registry = new ConfigRegistry();
        registerConfigOptions(registry);
        registry.saveDefaults(getConfigKey(), configuration);
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", this::setEnabled);
        configuration.loadBoolean("completion_enabled", this::setCompletionEnabled);
        configuration.loadInteger("scan_blocks_per_tick", this::setScanBlocksPerTick);
        configuration.loadInteger("scan_interval", this::setScanInterval);
        // Zero switches the sweep off, which is a real choice on a server where
        // nothing writes to the world behind the game's back.
        configuration.loadInteger("full_rescan_interval", this::setFullRescanInterval);
    }

    public void registerConfigOptions(final ConfigRegistry registry) {
        registry.add(ConfigOption.bool(
                getConfigKey(), "enabled", ENABLED_DEFAULT, () -> enabled, this::setEnabled));
        registry.add(ConfigOption.bool(
                getConfigKey(), "completion_enabled", COMPLETION_ENABLED_DEFAULT,
                () -> completionEnabled, this::setCompletionEnabled));
        registry.add(ConfigOption.integer(
                getConfigKey(), "scan_blocks_per_tick", SCAN_BLOCKS_PER_TICK_DEFAULT,
                MIN_SCAN_BLOCKS_PER_TICK, MAX_SCAN_BLOCKS_PER_TICK,
                () -> scanBlocksPerTick, this::setScanBlocksPerTick));
        registry.add(ConfigOption.integer(
                getConfigKey(), "scan_interval", SCAN_INTERVAL_DEFAULT,
                MIN_SCAN_INTERVAL, Integer.MAX_VALUE, () -> scanInterval, this::setScanInterval));
        registry.add(ConfigOption.integer(
                getConfigKey(), "full_rescan_interval", FULL_RESCAN_INTERVAL_DEFAULT,
                value -> value == 0 || value >= MIN_FULL_RESCAN_INTERVAL,
                "Expected zero or an integer from " + MIN_FULL_RESCAN_INTERVAL + " through "
                        + Integer.MAX_VALUE,
                () -> fullRescanInterval, this::setFullRescanInterval));
    }

    private void setEnabled(final boolean value) {
        final boolean changed = enabled != value;
        enabled = value;
        if (!changed || context == null || !context.isStarted()) {
            return;
        }
        if (enabled) {
            refreshAllLayouts();
        } else {
            for (final UUID placementId : placements.keySet()) {
                cancelScanFor(placementId);
                tracker.forget(placementId);
            }
        }
        context.serverFeaturesChanged();
    }

    private void setCompletionEnabled(final boolean value) {
        final boolean changed = completionEnabled != value;
        completionEnabled = value;
        if (changed && completionEnabled && context != null && context.isStarted()) {
            for (final UUID placementId : placements.keySet()) {
                tracker.requestFullPass(placementId);
            }
        }
    }

    private void setScanBlocksPerTick(final int value) {
        scanBlocksPerTick =
                Math.max(MIN_SCAN_BLOCKS_PER_TICK, Math.min(MAX_SCAN_BLOCKS_PER_TICK, value));
    }

    private void setScanInterval(final int value) {
        scanInterval = Math.max(MIN_SCAN_INTERVAL, value);
    }

    private void setFullRescanInterval(final int value) {
        fullRescanInterval = value <= 0 ? 0 : Math.max(MIN_FULL_RESCAN_INTERVAL, value);
    }

    private void refreshAllLayouts() {
        pendingLayoutTokens.clear();
        deferredLayouts.clear();
        deferredLayoutIds.clear();
        for (final ServerPlacement placement : placements.values()) {
            cancelScanFor(placement.getId());
            blocksCache.remove(placement.getId());
            lastScanTick.remove(placement.getId());
            scheduleLayoutLoad(placement);
            tracker.requestFullPass(placement.getId());
        }
    }

    public boolean isCompletionEnabled() {
        return completionEnabled;
    }

    @Override
    public void startup() {
        // Attached unconditionally. With nothing being tracked the hot path is
        // one failed map lookup, and this way neither the startup order of the
        // configuration nor a reload of it can leave the tracker detached.
        tracker.install();
    }

    @Override
    public void shutdown() {
        tracker.uninstall();
        placements.clear();
        regionBlocks.clear();
        regionGeometry.clear();
        scanQueue.clear();
        queuedForScan.clear();
        lastScanTick.clear();
        blocksCache.clear();
        loadedBlocks.clear();
        activeScan = null;
        pendingBlockLoad = null;
        pendingBlockToken = null;
        pendingLayoutTokens.clear();
        deferredLayouts.clear();
        deferredLayoutIds.clear();
        completedLayouts.clear();
        scanStore = null;
        layoutExecutor.shutdownNow();
    }
}
