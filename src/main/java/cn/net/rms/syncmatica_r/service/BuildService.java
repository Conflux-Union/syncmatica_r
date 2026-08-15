package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.build_management.BuildScanStore;
import cn.net.rms.syncmatica_r.build_management.RegionBlocks;
import cn.net.rms.syncmatica_r.build_management.RegionBounds;
import cn.net.rms.syncmatica_r.build_management.RegionBoundsResolver;
import cn.net.rms.syncmatica_r.build_management.RegionGeometry;
import cn.net.rms.syncmatica_r.build_management.RegionLayoutExtractor;
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
    public static final int SCAN_BLOCKS_PER_TICK_DEFAULT = 1024;
    public static final int SCAN_INTERVAL_DEFAULT = 1200;
    private static final int MAX_SCAN_BLOCKS_PER_TICK = 65_536;
    private static final int MIN_SCAN_BLOCKS_PER_TICK = 64;
    private static final int MIN_SCAN_INTERVAL = 100;
    private static final String CONFIG_KEY = "build";
    private static final int MAX_QUEUED_EXTRACTIONS = 64;
    private static final Logger LOGGER = LogManager.getLogger(BuildService.class);

    private final Map<UUID, ServerPlacement> placements = new HashMap<>();
    private final Map<UUID, Map<String, Long>> regionBlocks = new HashMap<>();
    private final Map<UUID, Map<String, RegionGeometry>> regionGeometry = new HashMap<>();

    private boolean enabled = ENABLED_DEFAULT;
    private boolean completionEnabled = COMPLETION_ENABLED_DEFAULT;
    private int scanBlocksPerTick = SCAN_BLOCKS_PER_TICK_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private int tickCounter;

    private final ArrayDeque<UUID> scanQueue = new ArrayDeque<>();
    private final Set<UUID> queuedForScan = new HashSet<>();
    private CompletionScan activeScan;
    private BuildScanStore scanStore;
    private UUID pendingBlockLoad;
    private UUID pendingBlockToken;
    private final Queue<LoadedRegionBlocks> loadedBlocks = new ConcurrentLinkedQueue<>();

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
            loadScanData(placement);
            scheduleLayoutLoad(placement);
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
        cancelScanFor(placementId);
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
     */
    private void tickCompletionScan(final MinecraftServer server) {
        if (!completionEnabled || server == null) {
            return;
        }
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
        if (++tickCounter >= scanInterval) {
            tickCounter = 0;
            enqueueAllPlacements();
        }
        requestNextScan();
    }

    private void enqueueAllPlacements() {
        for (final UUID placementId : regionGeometry.keySet()) {
            if (placements.containsKey(placementId) && queuedForScan.add(placementId)) {
                scanQueue.addLast(placementId);
            }
        }
    }

    private void requestNextScan() {
        while (!scanQueue.isEmpty()) {
            final UUID placementId = scanQueue.pollFirst();
            queuedForScan.remove(placementId);
            final ServerPlacement placement = placements.get(placementId);
            if (placement == null || !regionGeometry.containsKey(placementId) || context == null) {
                continue;
            }
            final File file = context.getFileStorage().getLocalLitematic(placement);
            if (file == null || !file.isFile()) {
                continue;
            }
            final UUID token = UUID.randomUUID();
            final long maxBytes = context.getMaxTransferBytes();
            pendingBlockLoad = placementId;
            pendingBlockToken = token;
            try {
                layoutExecutor.execute(() -> loadedBlocks.add(new LoadedRegionBlocks(
                        placementId, token, RegionLayoutExtractor.extractRegionBlocks(file, maxBytes))));
            } catch (final RejectedExecutionException exception) {
                pendingBlockLoad = null;
                pendingBlockToken = null;
                LOGGER.debug("Region block loader is busy or shutting down", exception);
            }
            return;
        }
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
            final Map<String, RegionGeometry> geometry = regionGeometry.get(loaded.placementId);
            if (placement == null || geometry == null || loaded.blocks.isEmpty()) {
                continue;
            }
            final ServerWorld world = WorldResolver.resolve(server, placement.getDimension());
            if (world == null) {
                continue;
            }
            activeScan = new CompletionScan(placement, world, geometry, loaded.blocks);
        }
    }

    private void applyScanResults(final CompletionScan scan) {
        final ServerPlacement placement = placements.get(scan.placementId);
        if (placement == null || scan.results.isEmpty() || scan.poseChanged(placement)) {
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
        if (regionGeometry.containsKey(placementId) && queuedForScan.add(placementId)) {
            scanQueue.addFirst(placementId);
        }
        persistAndBroadcast(placement);
        return true;
    }

    private void cancelScanFor(final UUID placementId) {
        if (activeScan != null && activeScan.placementId.equals(placementId)) {
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
        private final Iterator<String> remainingRegions;
        private final Map<String, Long> results = new LinkedHashMap<>();

        private String currentRegion;
        private RegionGeometry currentGeometry;
        private RegionBlocks currentBlocks;
        private Block[] currentPalette;
        private RegionScanCache currentCache;
        private Iterator<Long> remainingColumns;

        private boolean columnActive;
        private int columnX;
        private int columnZ;
        private Iterator<BlockPos> positions;
        private int columnMatched;
        private boolean finished;

        private CompletionScan(final ServerPlacement placement, final ServerWorld world,
                               final Map<String, RegionGeometry> geometry,
                               final Map<String, RegionBlocks> blocks) {
            this.placement = placement;
            placementId = placement.getId();
            this.world = world;
            origin = placement.getPosition();
            rotation = placement.getRotation();
            mirror = placement.getMirror();
            overrides = new LinkedHashMap<>(overridesOf(placement));
            this.geometry = geometry;
            this.blocks = blocks;
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
                commitColumn();
                // Examining a column costs a unit too, so a region spread over
                // thousands of unloaded columns cannot stall a tick.
                remaining--;
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
            final BlockPos local = RegionBoundsResolver.toLocalPosition(
                    pos, currentGeometry, origin, rotation, mirror, overrides.get(currentRegion));
            if (local == null) {
                return;
            }
            final int paletteIndex = currentBlocks.paletteIndexAt(local.getX(), local.getY(), local.getZ());
            if (paletteIndex < 0) {
                return;
            }
            final Block expected = currentPalette[paletteIndex];
            if (expected != null && world.getBlockState(pos).getBlock() == expected) {
                columnMatched++;
            }
        }

        private void beginRegion(final String regionName) {
            currentRegion = regionName;
            currentGeometry = geometry.get(regionName);
            currentBlocks = blocks.get(regionName);
            currentPalette = null;
            currentCache = null;
            remainingColumns = null;
            final BuildRegion region = placement.getBuildRegions().get(regionName);
            if (region == null || currentGeometry == null || currentBlocks == null) {
                return;
            }
            final RegionBounds bounds = RegionBoundsResolver.resolve(
                    currentGeometry, origin, rotation, mirror, overrides.get(regionName));
            if (bounds == null) {
                return;
            }
            RegionScanCache cache = region.getScanCache();
            if (cache == null || !cache.matches(bounds)) {
                cache = new RegionScanCache(bounds);
                region.setScanCache(cache);
            }
            currentCache = cache;
            currentPalette = resolvePalette(currentBlocks.getPalette());
            remainingColumns = cache.columns();
        }

        private void beginColumn(final long packedColumn) {
            columnX = RegionScanCache.columnX(packedColumn);
            columnZ = RegionScanCache.columnZ(packedColumn);
            columnMatched = 0;
            columnActive = false;
            positions = null;
            // An unloaded column keeps whatever it was last counted as, which is
            // still what is there: nothing can have been built inside it since.
            if (!world.isChunkLoaded(columnX, columnZ)) {
                return;
            }
            final RegionBounds box = currentCache.columnBounds(columnX, columnZ);
            if (box == null) {
                return;
            }
            columnActive = true;
            positions = BlockPos.iterate(box.getMin(), box.getMax()).iterator();
        }

        /** Publishes a column only once every position in it has been read. */
        private void commitColumn() {
            if (columnActive) {
                currentCache.record(columnX, columnZ, columnMatched);
            }
            abandonColumn();
        }

        private void abandonColumn() {
            columnActive = false;
            positions = null;
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
        private final UUID token;
        private final Map<String, RegionBlocks> blocks;

        private LoadedRegionBlocks(final UUID placementId, final UUID token,
                                   final Map<String, RegionBlocks> blocks) {
            this.placementId = placementId;
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
        configuration.saveBoolean("completion_enabled", COMPLETION_ENABLED_DEFAULT);
        configuration.saveInteger("scan_blocks_per_tick", SCAN_BLOCKS_PER_TICK_DEFAULT);
        configuration.saveInteger("scan_interval", SCAN_INTERVAL_DEFAULT);
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", value -> enabled = value);
        configuration.loadBoolean("completion_enabled", value -> completionEnabled = value);
        configuration.loadInteger("scan_blocks_per_tick", value ->
                scanBlocksPerTick = Math.max(MIN_SCAN_BLOCKS_PER_TICK, Math.min(MAX_SCAN_BLOCKS_PER_TICK, value)));
        configuration.loadInteger("scan_interval", value ->
                scanInterval = Math.max(MIN_SCAN_INTERVAL, value));
    }

    public boolean isCompletionEnabled() {
        return completionEnabled;
    }

    @Override
    public void startup() {
    }

    @Override
    public void shutdown() {
        placements.clear();
        regionBlocks.clear();
        regionGeometry.clear();
        scanQueue.clear();
        queuedForScan.clear();
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
