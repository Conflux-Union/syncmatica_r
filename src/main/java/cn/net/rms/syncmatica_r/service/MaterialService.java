package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.ServerPosition;
import cn.net.rms.syncmatica_r.communication.MessageType;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.*;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import cn.net.rms.syncmatica_r.util.NbtHelper;
import cn.net.rms.syncmatica_r.util.InventoryScanner;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import cn.net.rms.syncmatica_r.util.WorldResolver;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
//#if MC >= 12001
//$$ import net.minecraft.registry.RegistryKeys;
//$$ import net.minecraft.block.entity.SignText;
//#else
import net.minecraft.util.registry.Registry;
//#endif
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MaterialService extends AbstractService {
    public static final boolean ENABLED_DEFAULT = true;
    public static final int SCAN_INTERVAL_DEFAULT = 200;
    public static final boolean INCLUDE_CONTAINER_CONTENTS_DEFAULT = false;
    public static final boolean ALLOW_OWNER_STOCKING_AREA_MANAGEMENT_DEFAULT = true;
    public static final int SCAN_BLOCK_ENTITIES_PER_TICK_DEFAULT = 2048;
    public static final int MAX_SCHEMATIC_MEGABYTES_DEFAULT = 64;
    public static final int MAX_SCHEMATIC_BLOCKS_DEFAULT = (int) ProtocolLimits.DEFAULT_MAX_SCHEMATIC_BLOCKS;
    public static final int MAX_STOCKING_AREA_BLOCKS_DEFAULT = 1_000_000;
    private static final int MIN_SCAN_BLOCK_ENTITIES_PER_TICK = 64;
    private static final int MAX_SCAN_BLOCK_ENTITIES_PER_TICK = 8_192;
    private static final int MAX_SCHEMATIC_MEGABYTES = 64;
    private static final int MAX_SCHEMATIC_BLOCKS = 64_000_000;
    private static final int MAX_STOCKING_AREA_BLOCKS = 64_000_000;
    private static final int MAX_QUEUED_EXTRACTIONS = 64;
    private static final Logger LOGGER = LogManager.getLogger(MaterialService.class);
    private final Map<UUID, ServerPlacement> placements = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> requiredTotals = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> stockingTotals = new HashMap<>();

    private final StockingAreaRegistry stockingAreaRegistry = new StockingAreaRegistry();

    // Placements whose dangling stocking-area reference was already reported;
    // keeps the periodic resolve from spamming the log with the same warning.
    private final Set<UUID> warnedDanglingStockingRefs = new HashSet<>();

    private boolean enabled = ENABLED_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private boolean includeContainerContents = INCLUDE_CONTAINER_CONTENTS_DEFAULT;
    private boolean ownerStockingAreaManagementEnabled = ALLOW_OWNER_STOCKING_AREA_MANAGEMENT_DEFAULT;
    private int scanBlockEntitiesPerTick = SCAN_BLOCK_ENTITIES_PER_TICK_DEFAULT;
    private int maxSchematicMegabytes = MAX_SCHEMATIC_MEGABYTES_DEFAULT;
    private int maxSchematicBlocks = MAX_SCHEMATIC_BLOCKS_DEFAULT;
    private int maxStockingAreaBlocks = MAX_STOCKING_AREA_BLOCKS_DEFAULT;
    private int tickCounter = 0;

    private final Map<UUID, PlacementScanState> activePlacementScans = new HashMap<>();
    private final ArrayDeque<UUID> placementScanQueue = new ArrayDeque<>();
    private DefaultStockingScanState defaultScanState;
    private boolean processDefaultScanNext;
    private final ExecutorService requirementExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_EXTRACTIONS),
            runnable -> {
                final Thread thread = new Thread(runnable, "syncmatica_r-material-extractor");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final Queue<RequirementExtractionResult> completedExtractions = new ConcurrentLinkedQueue<>();
    private final Map<UUID, UUID> pendingExtractionTokens = new HashMap<>();
    private final ArrayDeque<UUID> deferredExtractions = new ArrayDeque<>();
    private final Set<UUID> deferredExtractionIds = new HashSet<>();

    public enum ClaimOutcome {
        CLAIMED,
        RELEASED,
        ALREADY_CLAIMED,
        ALREADY_RELEASED,
        CLAIMED_BY_OTHER,
        UNKNOWN_MATERIAL,
        DISABLED
    }

    public enum ReleaseClaimsOutcome {
        RELEASED,
        ALREADY_RELEASED,
        UNKNOWN_PLACEMENT,
        DISABLED
    }

    public enum StockingAreaDeleteOutcome {
        DELETED,
        IN_USE,
        NOT_FOUND,
        RESERVED_NAME
    }

    public enum StockingAreaBindOutcome {
        BOUND,
        CLEARED,
        UNKNOWN_AREA
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOwnerStockingAreaManagementEnabled() {
        return ownerStockingAreaManagementEnabled;
    }

    public ClaimOutcome setClaim(final ServerPlacement placement, final MaterialKey key,
                                 final PlayerIdentifier player, final boolean claimed) {
        if (!enabled) {
            return ClaimOutcome.DISABLED;
        }
        if (placement == null || key == null || player == null) {
            return ClaimOutcome.UNKNOWN_MATERIAL;
        }
        final MaterialProgressEntry entry = placement.getMaterialProgress().get(key);
        if (entry == null || entry.getRequiredAmount() <= 0) {
            return ClaimOutcome.UNKNOWN_MATERIAL;
        }
        if (claimed) {
            if (entry.hasClaimer(player)) {
                return ClaimOutcome.ALREADY_CLAIMED;
            }
            if (!entry.getClaimants().isEmpty()) {
                return ClaimOutcome.CLAIMED_BY_OTHER;
            }
            entry.addClaimer(player);
        } else {
            if (!entry.hasClaimer(player)) {
                return ClaimOutcome.ALREADY_RELEASED;
            }
            entry.removeClaimer(player);
        }
        placement.setLastModifiedBy(player);
        placement.touchModified(System.currentTimeMillis());
        persistAndBroadcast(placement);
        return claimed ? ClaimOutcome.CLAIMED : ClaimOutcome.RELEASED;
    }

    public ClaimOutcome toggleClaim(final ServerPlacement placement, final MaterialKey key,
                                    final PlayerIdentifier player) {
        return setClaim(placement, key, player, !isClaimedBy(placement, key, player));
    }

    public ReleaseClaimsOutcome releaseClaims(final ServerPlacement placement,
                                              final PlayerIdentifier player) {
        if (!enabled) {
            return ReleaseClaimsOutcome.DISABLED;
        }
        if (placement == null || player == null) {
            return ReleaseClaimsOutcome.UNKNOWN_PLACEMENT;
        }
        boolean changed = false;
        for (final MaterialProgressEntry entry : placement.getMaterialProgress().getEntries()) {
            if (entry.hasClaimer(player)) {
                entry.removeClaimer(player);
                changed = true;
            }
        }
        if (!changed) {
            return ReleaseClaimsOutcome.ALREADY_RELEASED;
        }
        placement.setLastModifiedBy(player);
        placement.touchModified(System.currentTimeMillis());
        persistAndBroadcast(placement);
        return ReleaseClaimsOutcome.RELEASED;
    }

    public boolean isClaimedBy(final ServerPlacement placement, final MaterialKey key,
                               final PlayerIdentifier player) {
        if (placement == null || key == null) {
            return false;
        }
        final MaterialProgressEntry entry = placement.getMaterialProgress().get(key);
        return entry != null && entry.hasClaimer(player);
    }

    public PlayerIdentifier getClaimant(final ServerPlacement placement, final MaterialKey key) {
        if (placement == null || key == null) {
            return null;
        }
        final MaterialProgressEntry entry = placement.getMaterialProgress().get(key);
        if (entry == null || entry.getClaimants().isEmpty()) {
            return null;
        }
        return entry.getClaimants().iterator().next();
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

    UUID pendingExtractionToken(final UUID placementId) {
        return pendingExtractionTokens.get(placementId);
    }

    boolean hasDefaultStockingScan() {
        return defaultScanState != null;
    }

    public void attachPlacement(final ServerPlacement placement) {
        placements.put(placement.getId(), placement);
        cancelPlacementScan(placement.getId());
        seedFromExistingSnapshot(placement);
        if (enabled && placement.getMaterialProgress().isEmpty()) {
            scheduleRequirementsLoad(placement);
            return;
        }
        rebuildSnapshot(placement, false);
    }

    public void detachPlacement(final ServerPlacement placement) {
        placements.remove(placement.getId());
        requiredTotals.remove(placement.getId());
        stockingTotals.remove(placement.getId());
        pendingExtractionTokens.remove(placement.getId());
        deferredExtractionIds.remove(placement.getId());
        deferredExtractions.removeIf(id -> id.equals(placement.getId()));
        cancelPlacementScan(placement.getId());
    }

    public void replaceRequirements(final UUID placementId, final Map<MaterialKey, Integer> required) {
        final Map<MaterialKey, Integer> replacement = new HashMap<>(required);
        if (replacement.equals(requiredTotals.get(placementId))) {
            return;
        }
        requiredTotals.put(placementId, replacement);
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            rebuildSnapshot(placement, true);
        }
    }

    public void setStockingContributions(final UUID placementId, final Map<MaterialKey, Integer> totals) {
        final Map<MaterialKey, Integer> replacement = new HashMap<>(totals);
        if (replacement.equals(stockingTotals.get(placementId))) {
            return;
        }
        stockingTotals.put(placementId, replacement);
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            rebuildSnapshot(placement, true);
        }
    }

    /**
     * @deprecated Coordinate-based areas no longer drive scans; retained only
     *     until the web facade switches to {@link #bindStockingArea}.
     */
    @Deprecated
    public void setStockingArea(final ServerPlacement placement, final StockingAreaDefinition area) {
        placement.setResolvedStockingArea(area);
    }

    public StockingAreaRegistry getStockingAreaRegistry() {
        return stockingAreaRegistry;
    }

    public StockingAreaDefinition resolveStockingArea(final ServerPlacement placement) {
        if (placement == null) {
            return null;
        }
        if (placement.getStockingAreaRef() != null) {
            final StockingAreaRegistry.Entry entry = stockingAreaRegistry.getById(placement.getStockingAreaRef());
            if (entry != null) {
                warnedDanglingStockingRefs.remove(placement.getId());
                return entry.getDefinition();
            }
            if (warnedDanglingStockingRefs.add(placement.getId())) {
                LOGGER.warn("Placement '{}' references missing stocking area {}; falling back to default",
                        placement.getName(), placement.getStockingAreaRef());
            }
        }
        final StockingAreaRegistry.Entry fallback = stockingAreaRegistry.getDefaultArea();
        return fallback == null ? null : fallback.getDefinition();
    }

    public StockingAreaDefinition getStockingArea(final UUID placementId) {
        return resolveStockingArea(placements.get(placementId));
    }

    public boolean hasBoundStockingArea(final ServerPlacement placement) {
        return placement != null && placement.getStockingAreaRef() != null;
    }

    public List<ServerPlacement> getPlacementsReferencing(final UUID areaId) {
        final List<ServerPlacement> result = new ArrayList<>();
        for (final ServerPlacement placement : placements.values()) {
            if (areaId.equals(placement.getStockingAreaRef())) {
                result.add(placement);
            }
        }
        return result;
    }

    public StockingAreaRegistry.CreateOutcome createStockingArea(final String name,
                                                                 final StockingAreaDefinition definition,
                                                                 final UUID ownerPlayerId) {
        // Only the server bootstrap (migration, legacy default load) may write
        // the reserved default entry; a player asking for that name would
        // otherwise silently overwrite the default area.
        if (StockingAreaRegistry.RESERVED_DEFAULT_NAME.equals(name) && ownerPlayerId != null) {
            return StockingAreaRegistry.CreateOutcome.RESERVED_NAME;
        }
        // A null-owner create of the reserved name delegates to the registry's
        // default bootstrap; every other name takes the regular create path.
        final StockingAreaRegistry.CreateOutcome outcome =
                stockingAreaRegistry.create(name, definition, ownerPlayerId, maxStockingAreaBlocks);
        if (outcome == StockingAreaRegistry.CreateOutcome.CREATED) {
            markStockingAreaRegistryDirty();
        }
        return outcome;
    }

    /**
     * Redefines an area and pushes the new definition to every referencing
     * placement. Scanning is left to the caller, which holds the server.
     */
    public StockingAreaRegistry.UpdateOutcome updateStockingArea(final UUID areaId,
                                                                 final StockingAreaDefinition definition) {
        final StockingAreaRegistry.UpdateOutcome outcome =
                stockingAreaRegistry.update(areaId, definition, maxStockingAreaBlocks);
        if (outcome != StockingAreaRegistry.UpdateOutcome.UPDATED) {
            return outcome;
        }
        markStockingAreaRegistryDirty();
        final StockingAreaRegistry.Entry entry = stockingAreaRegistry.getById(areaId);
        if (entry != null && StockingAreaRegistry.RESERVED_DEFAULT_NAME.equals(entry.getName())) {
            // The default area changed; drop the in-flight sign-dispatch scan so
            // the next tick rescans with the new definition.
            defaultScanState = null;
        }
        refreshPlacementsBoundTo(areaId);
        return outcome;
    }

    public StockingAreaDeleteOutcome deleteStockingArea(final UUID areaId, final boolean force) {
        final StockingAreaRegistry.Entry entry = stockingAreaRegistry.getById(areaId);
        if (entry == null) {
            return StockingAreaDeleteOutcome.NOT_FOUND;
        }
        if (StockingAreaRegistry.RESERVED_DEFAULT_NAME.equals(entry.getName())) {
            return StockingAreaDeleteOutcome.RESERVED_NAME;
        }
        final List<ServerPlacement> referencing = getPlacementsReferencing(areaId);
        if (!referencing.isEmpty() && !force) {
            return StockingAreaDeleteOutcome.IN_USE;
        }
        final StockingAreaRegistry.DeleteOutcome outcome = stockingAreaRegistry.delete(areaId);
        if (outcome != StockingAreaRegistry.DeleteOutcome.DELETED) {
            return StockingAreaDeleteOutcome.valueOf(outcome.name());
        }
        markStockingAreaRegistryDirty();
        // Unbound placements fall back to the default area on their next resolve;
        // restart the sign-dispatch scan so they rejoin it with current aliases.
        if (!referencing.isEmpty()) {
            defaultScanState = null;
        }
        for (final ServerPlacement placement : referencing) {
            placement.setStockingAreaRef(null);
            refreshPlacement(placement, resolveStockingArea(placement));
        }
        return StockingAreaDeleteOutcome.DELETED;
    }

    /** null clears the binding; the placement then falls back to the default area. */
    public StockingAreaBindOutcome bindStockingArea(final ServerPlacement placement, final UUID areaId) {
        if (areaId != null && stockingAreaRegistry.getById(areaId) == null) {
            return StockingAreaBindOutcome.UNKNOWN_AREA;
        }
        if (Objects.equals(placement.getStockingAreaRef(), areaId)) {
            return areaId == null ? StockingAreaBindOutcome.CLEARED : StockingAreaBindOutcome.BOUND;
        }
        placement.setStockingAreaRef(areaId);
        cancelPlacementScan(placement.getId());
        placement.touchModified(System.currentTimeMillis());
        if (context != null) {
            context.getSyncmaticManager().updateServerPlacement(placement);
            if (context.getCommunicationManager() instanceof ServerCommunicationManager) {
                ((ServerCommunicationManager) context.getCommunicationManager())
                        .broadcastPlacementUpdate(placement);
            }
        }
        return areaId == null ? StockingAreaBindOutcome.CLEARED : StockingAreaBindOutcome.BOUND;
    }

    /**
     * Re-runs scans for a set of placements; command and packet handlers own
     * the {@link MinecraftServer} reference, so they drive this after a
     * registry mutation instead of the mutation scanning on its own.
     */
    public void rescanPlacements(final MinecraftServer server, final List<ServerPlacement> placementsToScan) {
        for (final ServerPlacement placement : placementsToScan) {
            scanNow(server, placement);
        }
    }

    private void refreshPlacementsBoundTo(final UUID areaId) {
        for (final ServerPlacement placement : getPlacementsReferencing(areaId)) {
            refreshPlacement(placement, resolveStockingArea(placement));
        }
    }

    private void refreshPlacement(final ServerPlacement placement, final StockingAreaDefinition resolved) {
        placement.setResolvedStockingArea(resolved);
        placement.touchModified(System.currentTimeMillis());
        persistAndBroadcast(placement);
    }

    private void markStockingAreaRegistryDirty() {
        if (context != null && context.isServer()) {
            context.getSyncmaticManager().markDefaultStockingAreaDirty();
        }
    }

    public void loadStockingAreaState(final JsonObject meta) {
        final StockingAreaRegistry loaded =
                StockingAreaRegistry.fromJson(meta, maxStockingAreaBlocks);
        stockingAreaRegistry.restoreFrom(loaded);
    }

    public JsonObject stockingAreaStateJson() {
        return stockingAreaRegistry.toJson();
    }

    private StockingAreaDefinition defaultArea() {
        final StockingAreaRegistry.Entry entry = stockingAreaRegistry.getDefaultArea();
        return entry == null ? null : entry.getDefinition();
    }

    public StockingAreaDefinition getDefaultStockingArea() {
        return defaultArea();
    }

    public boolean isStockingAreaAllowed(final StockingAreaDefinition area) {
        return area == null || area.getVolume() <= maxStockingAreaBlocks;
    }

    public void tick(final MinecraftServer server) {
        if (!enabled) {
            return;
        }
        applyCompletedExtractions();
        scheduleDeferredExtraction();
        processNextScan();
        tickCounter++;
        if (tickCounter < scanInterval) {
            return;
        }
        tickCounter = 0;
        schedulePlacementScans(server);
    }

    private void processNextScan() {
        if (defaultScanState != null && (placementScanQueue.isEmpty() || processDefaultScanNext)) {
            processDefaultScan();
        } else {
            processPlacementScans();
        }
        processDefaultScanNext = !processDefaultScanNext;
    }

    private void processPlacementScans() {
        if (placementScanQueue.isEmpty()) {
            return;
        }
        final UUID placementId = placementScanQueue.pollFirst();
        final PlacementScanState state = activePlacementScans.get(placementId);
        if (state == null) {
            return;
        }
        state.process(Math.max(1, scanBlockEntitiesPerTick));
        if (state.isFinished()) {
            if (state.hasLoadedChunks()) {
                finalizePlacementScan(placementId, state);
            }
            activePlacementScans.remove(placementId);
        } else {
            placementScanQueue.addLast(placementId);
        }
    }

    private void processDefaultScan() {
        if (defaultScanState == null) {
            return;
        }
        defaultScanState.process(Math.max(1, scanBlockEntitiesPerTick));
        if (defaultScanState.isFinished()) {
            if (defaultScanState.hasLoadedChunks()) {
                LOGGER.debug("Default stocking area scan finished in {} us: {}/{} chunks loaded/fetched, "
                                + "{} block entities inspected, {} containers counted, {} project totals",
                        defaultScanState.elapsedMicros(), defaultScanState.getChunksLoaded(),
                        defaultScanState.getChunksFetched(), defaultScanState.getBlockEntitiesInspected(),
                        defaultScanState.getContainersCounted(), defaultScanState.getTotals().size());
                applyDefaultScanResults(defaultScanState.getTotals());
            }
            defaultScanState = null;
        }
    }

    private void schedulePlacementScans(final MinecraftServer server) {
        for (final ServerPlacement placement : placements.values()) {
            if (!hasBoundStockingArea(placement)) {
                continue;
            }
            if (activePlacementScans.containsKey(placement.getId())) {
                continue;
            }
            final StockingAreaDefinition area = resolveStockingArea(placement);
            if (area == null) {
                continue;
            }
            queuePlacementScan(server, placement, area);
        }
        final StockingAreaDefinition defaultDefinition = defaultArea();
        if (defaultDefinition != null) {
            if (defaultScanState == null || defaultScanState.isFinished()) {
                defaultScanState = newDefaultStockingScanState(server, defaultDefinition);
            }
        } else {
            defaultScanState = null;
        }
    }

    public void scanNow(final MinecraftServer server, final ServerPlacement placement) {
        if (!enabled) {
            return;
        }
        if (hasBoundStockingArea(placement)) {
            final StockingAreaDefinition area = resolveStockingArea(placement);
            if (area != null) {
                cancelPlacementScan(placement.getId());
                queuePlacementScan(server, placement, area);
            }
            return;
        }
        final StockingAreaDefinition defaultDefinition = defaultArea();
        if (defaultDefinition != null) {
            defaultScanState = newDefaultStockingScanState(server, defaultDefinition);
        }
    }

    public void scanDefaultNow(final MinecraftServer server) {
        final StockingAreaDefinition defaultDefinition = defaultArea();
        if (!enabled || defaultDefinition == null) {
            return;
        }
        defaultScanState = newDefaultStockingScanState(server, defaultDefinition);
    }

    private DefaultStockingScanState newDefaultStockingScanState(final MinecraftServer server,
                                                                 final StockingAreaDefinition area) {
        return new DefaultStockingScanState(
                resolveView(server, area),
                area,
                buildPlacementAliases(placements.values()),
                isStockingAreaAllowed(area)
        );
    }

    private StockingScanWorldView resolveView(final MinecraftServer server, final StockingAreaDefinition area) {
        final ServerWorld world = resolveWorld(server, area.getDimensionId());
        return world == null ? null : new ServerWorldStockingScanWorld(world);
    }

    private void queuePlacementScan(final MinecraftServer server, final ServerPlacement placement,
                                    final StockingAreaDefinition area) {
        cancelPlacementScan(placement.getId());
        final PlacementScanState state = new PlacementScanState(
                resolveView(server, area), area, isStockingAreaAllowed(area));
        if (state.isFinished()) {
            return;
        }
        activePlacementScans.put(placement.getId(), state);
        placementScanQueue.addLast(placement.getId());
    }

    private void finalizePlacementScan(final UUID placementId, final PlacementScanState state) {
        final ServerPlacement placement = placements.get(placementId);
        LOGGER.debug("Stocking area scan for placement '{}' finished in {} us: {}/{} chunks loaded/fetched, "
                        + "{} block entities inspected, {} containers counted, {} material entries",
                placement != null ? placement.getName() : placementId,
                state.elapsedMicros(), state.getChunksLoaded(), state.getChunksFetched(),
                state.getBlockEntitiesInspected(), state.getContainersCounted(), state.getTotals().size());
        setStockingContributions(placementId, state.getTotals());
    }

    private void applyDefaultScanResults(final Map<String, Map<MaterialKey, Integer>> totals) {
        for (final ServerPlacement placement : placements.values()) {
            if (hasBoundStockingArea(placement)) {
                continue;
            }
            final Map<MaterialKey, Integer> contribution = totals.getOrDefault(placement.getName(), Collections.emptyMap());
            setStockingContributions(placement.getId(), contribution);
        }
    }

    private void cancelPlacementScan(final UUID placementId) {
        activePlacementScans.remove(placementId);
        placementScanQueue.removeIf(id -> id.equals(placementId));
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        final ConfigRegistry registry = new ConfigRegistry();
        registerConfigOptions(registry);
        registry.saveDefaults(getConfigKey(), configuration);
    }

    @Override
    public String getConfigKey() {
        return "materials";
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        migrateLegacyScanBudget(configuration);
        configuration.loadBoolean("enabled", this::setEnabled);
        configuration.loadInteger("scan_interval", this::setScanInterval);
        configuration.loadBoolean("include_container_contents", this::setIncludeContainerContents);
        configuration.loadBoolean("allow_owner_stocking_area_management",
                this::setOwnerStockingAreaManagementEnabled);
        configuration.loadInteger("scan_block_entities_per_tick", this::setScanBlockEntitiesPerTick);
        configuration.loadInteger("max_schematic_megabytes", this::setMaxSchematicMegabytes);
        configuration.loadInteger("max_schematic_blocks", this::setMaxSchematicBlocks);
        configuration.loadInteger("max_stocking_area_blocks", this::setMaxStockingAreaBlocks);
    }

    /**
     * Carries the legacy "scan_blocks_per_tick" value into its replacement so
     * tuned servers keep a sane budget after the budget unit changed from
     * visited blocks to inspected block entities, then drops the legacy key
     * so it does not linger as an ignored orphan in the config file.
     */
    private static void migrateLegacyScanBudget(final IServiceConfiguration configuration) {
        final Integer legacy = configuration.readInteger("scan_blocks_per_tick");
        configuration.removeKey("scan_blocks_per_tick");
        if (legacy == null || configuration.readInteger("scan_block_entities_per_tick") != null) {
            return;
        }
        final int clamped = Math.max(MIN_SCAN_BLOCK_ENTITIES_PER_TICK,
                Math.min(MAX_SCAN_BLOCK_ENTITIES_PER_TICK, legacy));
        configuration.replaceInteger("scan_block_entities_per_tick", clamped);
    }

    public void registerConfigOptions(final ConfigRegistry registry) {
        registry.add(ConfigOption.bool(
                getConfigKey(), "enabled", ENABLED_DEFAULT, () -> enabled, this::setEnabled));
        registry.add(ConfigOption.integer(
                getConfigKey(), "scan_interval", SCAN_INTERVAL_DEFAULT, 20, Integer.MAX_VALUE,
                () -> scanInterval, this::setScanInterval));
        registry.add(ConfigOption.bool(
                getConfigKey(), "include_container_contents", INCLUDE_CONTAINER_CONTENTS_DEFAULT,
                () -> includeContainerContents, this::setIncludeContainerContents));
        registry.add(ConfigOption.bool(
                getConfigKey(), "allow_owner_stocking_area_management",
                ALLOW_OWNER_STOCKING_AREA_MANAGEMENT_DEFAULT,
                () -> ownerStockingAreaManagementEnabled, this::setOwnerStockingAreaManagementEnabled));
        registry.add(ConfigOption.integer(
                getConfigKey(), "scan_block_entities_per_tick", SCAN_BLOCK_ENTITIES_PER_TICK_DEFAULT,
                MIN_SCAN_BLOCK_ENTITIES_PER_TICK, MAX_SCAN_BLOCK_ENTITIES_PER_TICK,
                () -> scanBlockEntitiesPerTick, this::setScanBlockEntitiesPerTick));
        registry.add(ConfigOption.integer(
                getConfigKey(), "max_schematic_megabytes", MAX_SCHEMATIC_MEGABYTES_DEFAULT,
                1, MAX_SCHEMATIC_MEGABYTES, () -> maxSchematicMegabytes, this::setMaxSchematicMegabytes));
        registry.add(ConfigOption.integer(
                getConfigKey(), "max_schematic_blocks", MAX_SCHEMATIC_BLOCKS_DEFAULT,
                1_000_000, MAX_SCHEMATIC_BLOCKS, () -> maxSchematicBlocks, this::setMaxSchematicBlocks));
        registry.add(ConfigOption.integer(
                getConfigKey(), "max_stocking_area_blocks", MAX_STOCKING_AREA_BLOCKS_DEFAULT,
                1_024, MAX_STOCKING_AREA_BLOCKS, () -> maxStockingAreaBlocks, this::setMaxStockingAreaBlocks));
    }

    private void setEnabled(final boolean value) {
        final boolean changed = enabled != value;
        enabled = value;
        if (!changed || context == null || !context.isStarted()) {
            return;
        }
        if (enabled) {
            refreshAllRequirements();
        }
        context.serverFeaturesChanged();
    }

    private void setScanInterval(final int value) {
        scanInterval = Math.max(20, value);
    }

    private void setIncludeContainerContents(final boolean value) {
        final boolean changed = includeContainerContents != value;
        includeContainerContents = value;
        if (changed && context != null && context.isStarted()) {
            refreshAllRequirements();
        }
    }

    private void setOwnerStockingAreaManagementEnabled(final boolean value) {
        ownerStockingAreaManagementEnabled = value;
    }

    private void setScanBlockEntitiesPerTick(final int value) {
        scanBlockEntitiesPerTick = Math.max(MIN_SCAN_BLOCK_ENTITIES_PER_TICK,
                Math.min(MAX_SCAN_BLOCK_ENTITIES_PER_TICK, value));
    }

    private void setMaxSchematicMegabytes(final int value) {
        final int normalized = Math.max(1, Math.min(MAX_SCHEMATIC_MEGABYTES, value));
        final boolean changed = maxSchematicMegabytes != normalized;
        maxSchematicMegabytes = normalized;
        if (changed && context != null && context.isStarted()) {
            refreshAllRequirements();
        }
    }

    private void setMaxSchematicBlocks(final int value) {
        final int normalized = Math.max(1_000_000, Math.min(MAX_SCHEMATIC_BLOCKS, value));
        final boolean changed = maxSchematicBlocks != normalized;
        maxSchematicBlocks = normalized;
        if (changed && context != null && context.isStarted()) {
            refreshAllRequirements();
        }
    }

    private void setMaxStockingAreaBlocks(final int value) {
        final int normalized = Math.max(1_024, Math.min(MAX_STOCKING_AREA_BLOCKS, value));
        final boolean changed = maxStockingAreaBlocks != normalized;
        maxStockingAreaBlocks = normalized;
        if (!changed || context == null || !context.isStarted()) {
            return;
        }
        activePlacementScans.clear();
        placementScanQueue.clear();
        defaultScanState = null;
        tickCounter = scanInterval;
    }

    private void refreshAllRequirements() {
        pendingExtractionTokens.clear();
        deferredExtractions.clear();
        deferredExtractionIds.clear();
        for (final ServerPlacement placement : placements.values()) {
            scheduleRequirementsLoad(placement);
        }
    }

    @Override
    public void startup() {
        tickCounter = 0;
    }

    @Override
    public void shutdown() {
        placements.clear();
        requiredTotals.clear();
        stockingTotals.clear();
        warnedDanglingStockingRefs.clear();
        activePlacementScans.clear();
        placementScanQueue.clear();
        completedExtractions.clear();
        pendingExtractionTokens.clear();
        deferredExtractions.clear();
        deferredExtractionIds.clear();
        requirementExecutor.shutdownNow();
    }

    private void rebuildSnapshot(final ServerPlacement placement, final boolean notify) {
        if (!enabled) {
            if (notify) {
                context.getSyncmaticManager().updateServerPlacement(placement);
            }
            return;
        }
        final UUID placementId = placement.getId();
        final Map<MaterialKey, Integer> required = requiredTotals.getOrDefault(placementId, Collections.emptyMap());
        final Map<MaterialKey, Integer> stock = stockingTotals.getOrDefault(placementId, Collections.emptyMap());

        final MaterialProgressState snapshot = placement.getMaterialProgress();

        final java.util.Map<MaterialKey, java.util.Collection<cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier>> previousClaimants = new java.util.HashMap<>();
        for (final MaterialProgressEntry e : snapshot.getEntries()) {
            previousClaimants.put(e.getKey(), new java.util.ArrayList<>(e.getClaimants()));
        }
        snapshot.clear();
        for (final java.util.Map.Entry<MaterialKey, Integer> requirement : required.entrySet()) {
            final MaterialKey key = requirement.getKey();
            final int requiredAmount = requirement.getValue();
            if (requiredAmount <= 0) {
                continue;
            }
            final MaterialProgressEntry entry = snapshot.getOrCreate(key, requiredAmount);
            entry.setStockingSupplied(stock.getOrDefault(key, 0));
            final java.util.Collection<cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier> claim = previousClaimants.get(key);
            if (claim != null) {
                for (final cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier p : claim) {
                    entry.addClaimer(p);
                }
            }
        }
        if (notify) {
            context.getSyncmaticManager().updateServerPlacement(placement);
            if (context.getCommunicationManager() instanceof ServerCommunicationManager) {
                ((ServerCommunicationManager) context.getCommunicationManager()).broadcastPlacementUpdate(placement);
            }
        }
    }

    private void seedFromExistingSnapshot(final ServerPlacement placement) {
        placement.getMaterialList().updateFrom(placement.getMaterialProgress());
        final UUID placementId = placement.getId();
        final Map<MaterialKey, Integer> required = requiredTotals.computeIfAbsent(placementId, unused -> new HashMap<>());
        final Map<MaterialKey, Integer> stock = stockingTotals.computeIfAbsent(placementId, unused -> new HashMap<>());
        required.clear();
        stock.clear();
        placement.getMaterialProgress().getEntries().forEach(entry -> {
            required.put(entry.getKey(), entry.getRequiredAmount());
            stock.put(entry.getKey(), entry.getStockingSupplied());
        });
    }

    private void scheduleRequirementsLoad(final ServerPlacement placement) {
        if (placement == null
                || pendingExtractionTokens.containsKey(placement.getId())
                || deferredExtractionIds.contains(placement.getId())) {
            return;
        }
        final File file = context.getFileStorage().getLocalLitematic(placement);
        if (file == null) {
            LOGGER.warn("Cannot load material requirements for placement '{}' (hash: {}): file not found",
                    placement.getName(), placement.getHash());
            reportAvailability(placement, MaterialAvailability.EXTRACTION_FAILED, "");
            return;
        }
        final long byteLimit = getMaxSchematicBytes();
        if (file.length() > byteLimit) {
            LOGGER.warn("Skipping material extraction for '{}' ({} bytes exceeds limit {} bytes)",
                    placement.getName(), file.length(), byteLimit);
            reportAvailability(placement, MaterialAvailability.FILE_TOO_LARGE,
                    SyncmaticaUtil.formatMegabytes(file.length()) + " > " + SyncmaticaUtil.formatMegabytes(byteLimit));
            return;
        }
        final UUID token = UUID.randomUUID();
        final UUID placementId = placement.getId();
        final UUID placementHash = placement.getHash();
        final String placementName = placement.getName();
        final boolean includeContents = includeContainerContents;
        final int blockLimit = Math.max(1, maxSchematicBlocks);
        pendingExtractionTokens.put(placementId, token);
        try {
            requirementExecutor.execute(() -> {
                LOGGER.debug("Loading material requirements from: {} (exists={})", file.getAbsolutePath(), file.exists());
                final MaterialRequirementExtractor.ExtractionOutcome outcome =
                        MaterialRequirementExtractor.extractDetailed(
                                file,
                                includeContents,
                                blockLimit,
                                byteLimit
                        );
                completedExtractions.add(new RequirementExtractionResult(
                        placementId,
                        placementHash,
                        token,
                        outcome.getRequirements(),
                        outcome.getAvailability()
                ));
                LOGGER.debug("Extracted {} material types from placement '{}'",
                        outcome.getRequirements().size(), placementName);
            });
        } catch (final RejectedExecutionException exception) {
            pendingExtractionTokens.remove(placementId);
            if (placements.containsKey(placementId) && deferredExtractionIds.add(placementId)) {
                deferredExtractions.addLast(placementId);
            }
            LOGGER.debug("Material extraction queue is full or shutting down", exception);
        }
    }

    private void scheduleDeferredExtraction() {
        final UUID placementId = deferredExtractions.pollFirst();
        if (placementId == null) {
            return;
        }
        deferredExtractionIds.remove(placementId);
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            scheduleRequirementsLoad(placement);
        }
    }

    private void applyCompletedExtractions() {
        RequirementExtractionResult result;
        while ((result = completedExtractions.poll()) != null) {
            if (!result.token.equals(pendingExtractionTokens.get(result.placementId))) {
                continue;
            }
            pendingExtractionTokens.remove(result.placementId);
            final ServerPlacement placement = placements.get(result.placementId);
            if (placement == null || !result.placementHash.equals(placement.getHash())) {
                continue;
            }
            if (result.availability.isBlocked()) {
                reportAvailability(placement, result.availability, blockedDetail(result.availability));
                continue;
            }
            // Clearing a previous rejection needs its own broadcast: identical
            // requirements make replaceRequirements a no-op.
            reportAvailability(placement, MaterialAvailability.AVAILABLE, "");
            if (!result.requirements.isEmpty()) {
                replaceRequirements(result.placementId, result.requirements);
            }
        }
    }

    private String blockedDetail(final MaterialAvailability availability) {
        return availability == MaterialAvailability.TOO_MANY_BLOCKS
                ? "> " + Math.max(1, maxSchematicBlocks)
                : "";
    }

    /**
     * Records why a material list is missing and makes it visible: the state
     * rides along with every placement update, and the owner additionally gets a
     * one-shot notification carrying the offending numbers.
     */
    private void reportAvailability(final ServerPlacement placement, final MaterialAvailability availability,
                                    final String detail) {
        if (!placement.setMaterialAvailability(availability)) {
            return;
        }
        if (context == null || !context.isServer()
                || !(context.getCommunicationManager() instanceof ServerCommunicationManager)) {
            return;
        }
        final ServerCommunicationManager manager = (ServerCommunicationManager) context.getCommunicationManager();
        context.getSyncmaticManager().updateServerPlacement(placement);
        manager.broadcastPlacementUpdate(placement);
        if (availability.isBlocked() && placement.getOwner() != null) {
            manager.sendMessageToPlayer(
                    placement.getOwner().uuid,
                    MessageType.ERROR,
                    availability.getMessageKey(),
                    detail
            );
        }
    }

    public void refreshPlacement(final ServerPlacement placement) {
        if (enabled) {
            scheduleRequirementsLoad(placement);
        }
    }


    private static net.minecraft.text.Text getSignLine(final net.minecraft.block.entity.SignBlockEntity sign, final int row) {
//#if MC >= 12001
//#if MC >= 260300
//$$         final net.minecraft.network.chat.Component frontLine = sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(row);
//$$         if (!frontLine.getString().isEmpty()) {
//$$             return frontLine;
//$$         }
//$$         return sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.BACK).getMessages(false).get(row);
//#else
//$$         final SignText front = sign.getFrontText();
//$$         final net.minecraft.text.Text frontLine = front.getMessage(row, false);
//$$         if (!frontLine.getString().isEmpty()) {
//$$             return frontLine;
//$$         }
//$$         return sign.getBackText().getMessage(row, false);
//#endif
//#else
        return sign.getTextOnRow(row, false);
//#endif
    }

    private static java.util.List<String> readSignNames(final net.minecraft.block.entity.SignBlockEntity sign) {
        final java.util.List<String> names = new java.util.ArrayList<>(1);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            try {
                final net.minecraft.text.Text line = getSignLine(sign, i);
                if (line == null) {
                    continue;
                }
                final String value = line.getString();
                if (value == null) {
                    continue;
                }
                final String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    sb.append(trimmed);
                }
            } catch (final Throwable ignored) {

            }
        }
        if (sb.length() > 0) {
            names.add(sb.toString());
        }
        return names;
    }

    private static BlockPos resolveContainerPosForSign(final StockingScanWorldView view, final BlockPos signPos) {
        final net.minecraft.block.BlockState state = view.getBlockState(signPos);
        BlockPos candidate = null;
        if (state.getBlock() instanceof net.minecraft.block.WallSignBlock) {
            final net.minecraft.util.math.Direction facing = state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING);
            if (facing != null) {
                candidate = signPos.offset(facing.getOpposite());
            }
        } else if (state.getBlock() instanceof net.minecraft.block.SignBlock) {
            candidate = signPos.down();
        }
        if (candidate == null) {
            return null;
        }
        final BlockEntity be = view.getBlockEntity(candidate);
        if (!(be instanceof Inventory)) {
            return null;
        }
        return candidate;
    }

    private static Inventory getInventoryAt(final StockingScanWorldView view, final BlockPos pos) {
        final net.minecraft.block.BlockState state = view.getBlockState(pos);
        final BlockEntity be = view.getBlockEntity(pos);
        if (!(be instanceof Inventory primary)) {
            return null;
        }

        if (state.getBlock() instanceof net.minecraft.block.ChestBlock) {
            final net.minecraft.block.entity.BlockEntityType<?> type = be.getType();

            for (final net.minecraft.util.math.Direction dir : new net.minecraft.util.math.Direction[]{
                    net.minecraft.util.math.Direction.NORTH,
                    net.minecraft.util.math.Direction.SOUTH,
                    net.minecraft.util.math.Direction.EAST,
                    net.minecraft.util.math.Direction.WEST}) {
                final BlockPos otherPos = pos.offset(dir);
                final BlockEntity otherBe = view.getBlockEntity(otherPos);
                if (otherBe != null && otherBe.getType() == type && otherBe instanceof Inventory) {
                    return new net.minecraft.inventory.DoubleInventory(primary, (Inventory) otherBe);
                }
            }
        }
        return (Inventory) be;
    }

    /**
     * Incremental stocking-area scan that enumerates loaded chunks and their
     * block-entity maps instead of visiting every block in the area. One
     * budget unit pays for either one chunk fetch or one inspected block
     * entity, so the per-tick main-thread cost is bounded by the configured
     * budget while total work is proportional to the chunk and container
     * count rather than the area volume.
     *
     * <p>Only the per-chunk entry snapshot and the chunk cursor survive across
     * ticks; the live block-entity map is never iterated across a tick
     * boundary because the world may mutate it between ticks.
     */
    abstract static class ChunkAreaScanState {
        private final StockingScanWorldView view;
        private final StockingAreaDefinition area;
        private final long startedNanos = System.nanoTime();

        // Chunk cursor, x inner, z outer.
        private int cursorChunkX;
        private int cursorChunkZ;
        private List<Map.Entry<BlockPos, BlockEntity>> currentChunkEntries = Collections.emptyList();
        private int entryIndex;

        private boolean finished;
        private boolean hasLoadedChunks;

        private int chunksFetched;
        private int chunksLoaded;
        private int blockEntitiesInspected;
        private int containersCounted;

        ChunkAreaScanState(final StockingScanWorldView view, final StockingAreaDefinition area,
                           final boolean areaAllowed) {
            this.view = view;
            this.area = area;
            if (view == null || area == null || !areaAllowed) {
                finished = true;
            } else {
                cursorChunkX = area.getMinChunkX();
                cursorChunkZ = area.getMinChunkZ();
            }
        }

        final void process(final int budget) {
            if (finished) {
                return;
            }
            int remaining = Math.max(1, budget);
            while (remaining > 0) {
                if (entryIndex < currentChunkEntries.size()) {
                    remaining--;
                    blockEntitiesInspected++;
                    final Map.Entry<BlockPos, BlockEntity> entry = currentChunkEntries.get(entryIndex++);
                    if (area.contains(entry.getKey())) {
                        inspect(entry.getKey(), entry.getValue());
                    }
                    continue;
                }
                if (cursorChunkZ > area.getMaxChunkZ()) {
                    finished = true;
                    return;
                }
                remaining--;
                chunksFetched++;
                final Map<BlockPos, BlockEntity> entities = view.getBlockEntities(cursorChunkX, cursorChunkZ);
                // Copied so the live map is never iterated across ticks.
                currentChunkEntries = entities == null
                        ? Collections.emptyList()
                        : new ArrayList<>(entities.entrySet());
                entryIndex = 0;
                if (entities != null) {
                    hasLoadedChunks = true;
                    chunksLoaded++;
                }
                cursorChunkX++;
                if (cursorChunkX > area.getMaxChunkX()) {
                    cursorChunkX = area.getMinChunkX();
                    cursorChunkZ++;
                }
            }
        }

        /** Handles one block entity whose position lies inside the area. */
        abstract void inspect(final BlockPos pos, final BlockEntity blockEntity);

        final void countContainer() {
            containersCounted++;
        }

        final StockingScanWorldView view() {
            return view;
        }

        final boolean isFinished() {
            return finished;
        }

        final boolean hasLoadedChunks() {
            return hasLoadedChunks;
        }

        final int getChunksFetched() {
            return chunksFetched;
        }

        final int getChunksLoaded() {
            return chunksLoaded;
        }

        final int getBlockEntitiesInspected() {
            return blockEntitiesInspected;
        }

        final int getContainersCounted() {
            return containersCounted;
        }

        final long elapsedMicros() {
            return (System.nanoTime() - startedNanos) / 1_000L;
        }
    }

    static final class PlacementScanState extends ChunkAreaScanState {
        private final Map<MaterialKey, Integer> totals = new HashMap<>();

        PlacementScanState(final StockingScanWorldView view, final StockingAreaDefinition area,
                           final boolean areaAllowed) {
            super(view, area, areaAllowed);
        }

        @Override
        void inspect(final BlockPos pos, final BlockEntity blockEntity) {
            if (blockEntity instanceof Inventory inventory) {
                countContainer();
                scanInventory(inventory, totals);
            }
        }

        Map<MaterialKey, Integer> getTotals() {
            return totals;
        }
    }

    /**
     * Builds the sign-text alias table for the default stocking area scan. Each
     * placement answers to both its embedded display name and the file name it
     * was shared under; both aliases resolve to the display name because that
     * is the key {@link #applyDefaultScanResults} looks totals up by. Display
     * names win over file names on collision, so an embedded name cannot be
     * shadowed by another placement's file name.
     */
    static Map<String, String> buildPlacementAliases(final Collection<ServerPlacement> placements) {
        final Map<String, String> aliases = new HashMap<>();
        for (final ServerPlacement placement : placements) {
            final String canonical = placement.getName();
            if (canonical != null && !canonical.isEmpty()) {
                aliases.put(canonical, canonical);
            }
        }
        for (final ServerPlacement placement : placements) {
            final String fileName = placement.getFileName();
            if (fileName == null || fileName.isEmpty()) {
                continue;
            }
            final String canonical = aliases.get(fileName);
            if (canonical == null) {
                aliases.put(fileName, placement.getName());
            } else {
                aliases.put(fileName, canonical);
            }
        }
        return aliases;
    }

    static final class DefaultStockingScanState extends ChunkAreaScanState {
        private final Map<String, Map<MaterialKey, Integer>> totals = new HashMap<>();
        private final Map<String, Set<BlockPos>> scannedContainers = new HashMap<>();
        private final Map<String, String> placementAliases;

        DefaultStockingScanState(final StockingScanWorldView view, final StockingAreaDefinition area,
                                 final Map<String, String> placementAliases, final boolean areaAllowed) {
            super(view, area, areaAllowed);
            this.placementAliases = placementAliases;
        }

        private static BlockPos getCanonicalContainerPos(final StockingScanWorldView view,
                                                         final BlockPos containerPos) {
            final net.minecraft.block.BlockState state = view.getBlockState(containerPos);
            if (!(state.getBlock() instanceof net.minecraft.block.ChestBlock)) {
                return containerPos;
            }
            final BlockEntity be = view.getBlockEntity(containerPos);
            if (be == null) {
                return containerPos;
            }
            final net.minecraft.block.entity.BlockEntityType<?> type = be.getType();
            for (final net.minecraft.util.math.Direction dir : new net.minecraft.util.math.Direction[]{
                    net.minecraft.util.math.Direction.NORTH,
                    net.minecraft.util.math.Direction.SOUTH,
                    net.minecraft.util.math.Direction.EAST,
                    net.minecraft.util.math.Direction.WEST}) {
                final BlockPos otherPos = containerPos.offset(dir);
                final BlockEntity otherBe = view.getBlockEntity(otherPos);
                if (otherBe != null && otherBe.getType() == type && otherBe instanceof Inventory) {
                    final int minX = Math.min(containerPos.getX(), otherPos.getX());
                    final int minY = Math.min(containerPos.getY(), otherPos.getY());
                    final int minZ = Math.min(containerPos.getZ(), otherPos.getZ());
                    return new BlockPos(minX, minY, minZ);
                }
            }
            return containerPos;
        }

        @Override
        void inspect(final BlockPos pos, final BlockEntity blockEntity) {
            if (!(blockEntity instanceof net.minecraft.block.entity.SignBlockEntity sign)) {
                return;
            }
            final java.util.List<String> names = readSignNames(sign);
            names.replaceAll(placementAliases::get);
            names.removeIf(Objects::isNull);
            if (names.isEmpty()) {
                return;
            }
            final BlockPos containerPos = resolveContainerPosForSign(view(), pos);
            if (containerPos == null) {
                return;
            }
            final BlockPos canonicalPos = getCanonicalContainerPos(view(), containerPos).toImmutable();

            // Check if all projects have already scanned this container
            boolean needsScan = false;
            for (final String projectName : names) {
                if (!scannedContainers.computeIfAbsent(projectName, key -> new HashSet<>()).contains(canonicalPos)) {
                    needsScan = true;
                    break;
                }
            }
            if (!needsScan) {
                return;
            }

            final Inventory inventory = getInventoryAt(view(), containerPos);
            if (inventory == null) {
                return;
            }
            for (final String projectName : names) {
                final Set<BlockPos> scanned = scannedContainers.computeIfAbsent(projectName, key -> new HashSet<>());
                if (scanned.contains(canonicalPos)) {
                    continue;
                }
                scanned.add(canonicalPos);
                countContainer();
                final Map<MaterialKey, Integer> projectTotals = totals.computeIfAbsent(projectName, key -> new HashMap<>());
                scanInventory(inventory, projectTotals);
            }
        }

        Map<String, Map<MaterialKey, Integer>> getTotals() {
            return totals;
        }
    }

    private static void scanInventory(final Inventory inventory, final Map<MaterialKey, Integer> totals) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            final ItemStack stack = inventory.getStack(slot);
            InventoryScanner.scanItemStack(stack, totals);
        }
    }

    public long getMaxSchematicBytes() {
        return Math.max(1L, maxSchematicMegabytes) * 1024L * 1024L;
    }

    private static final class RequirementExtractionResult {
        private final UUID placementId;
        private final UUID placementHash;
        private final UUID token;
        private final Map<MaterialKey, Integer> requirements;
        private final MaterialAvailability availability;

        private RequirementExtractionResult(final UUID placementId,
                                            final UUID placementHash,
                                            final UUID token,
                                            final Map<MaterialKey, Integer> requirements,
                                            final MaterialAvailability availability) {
            this.placementId = placementId;
            this.placementHash = placementHash;
            this.token = token;
            this.requirements = new HashMap<>(requirements);
            this.availability = availability;
        }
    }

    private ServerWorld resolveWorld(final MinecraftServer server, final String dimensionId) {
        return WorldResolver.resolve(server, dimensionId);
    }
}
