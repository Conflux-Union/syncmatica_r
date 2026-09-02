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
    public static final int SCAN_BLOCKS_PER_TICK_DEFAULT = 2048;
    public static final int MAX_SCHEMATIC_MEGABYTES_DEFAULT = 64;
    public static final int MAX_SCHEMATIC_BLOCKS_DEFAULT = (int) ProtocolLimits.DEFAULT_MAX_SCHEMATIC_BLOCKS;
    public static final int MAX_STOCKING_AREA_BLOCKS_DEFAULT = 1_000_000;
    private static final int MAX_SCAN_BLOCKS_PER_TICK = 65_536;
    private static final int MAX_SCHEMATIC_MEGABYTES = 64;
    private static final int MAX_SCHEMATIC_BLOCKS = 64_000_000;
    private static final int MAX_STOCKING_AREA_BLOCKS = 64_000_000;
    private static final int MAX_QUEUED_EXTRACTIONS = 64;
    private static final Logger LOGGER = LogManager.getLogger(MaterialService.class);
    private final Map<UUID, ServerPlacement> placements = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> requiredTotals = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> stockingTotals = new HashMap<>();

    private final Map<UUID, StockingAreaDefinition> stockingAreas = new HashMap<>();

    private StockingAreaDefinition defaultStockingArea;

    private boolean enabled = ENABLED_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private boolean includeContainerContents = INCLUDE_CONTAINER_CONTENTS_DEFAULT;
    private boolean ownerStockingAreaManagementEnabled = ALLOW_OWNER_STOCKING_AREA_MANAGEMENT_DEFAULT;
    private int scanBlocksPerTick = SCAN_BLOCKS_PER_TICK_DEFAULT;
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
        stockingAreas.put(placement.getId(), placement.getStockingArea());
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
        stockingAreas.remove(placement.getId());
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

    public void setStockingArea(final ServerPlacement placement, final StockingAreaDefinition area) {
        if (!isStockingAreaAllowed(area)) {
            throw new IllegalArgumentException("Stocking area exceeds the configured block limit");
        }
        if (Objects.equals(stockingAreas.get(placement.getId()), area)) {
            return;
        }
        stockingAreas.put(placement.getId(), area);
        placement.setStockingArea(area);
        cancelPlacementScan(placement.getId());
        placement.touchModified(System.currentTimeMillis());
        if (context != null) {
            context.getSyncmaticManager().updateServerPlacement(placement);
            if (context.getCommunicationManager() instanceof ServerCommunicationManager) {
                ((ServerCommunicationManager) context.getCommunicationManager()).broadcastPlacementUpdate(placement);
            }
        }
    }

    public StockingAreaDefinition getStockingArea(final UUID placementId) {
        return stockingAreas.get(placementId);
    }

    public StockingAreaDefinition getDefaultStockingArea() {
        return defaultStockingArea;
    }

    public void setDefaultStockingArea(final StockingAreaDefinition area) {
        applyDefaultStockingArea(area, true);
    }

    public void loadDefaultStockingArea(final StockingAreaDefinition area) {
        applyDefaultStockingArea(area, false);
    }

    private void applyDefaultStockingArea(final StockingAreaDefinition area, final boolean notifyManager) {
        if (!isStockingAreaAllowed(area)) {
            LOGGER.warn("Ignoring stocking area with {} blocks; configured maximum is {}",
                    area == null ? 0L : area.getVolume(), maxStockingAreaBlocks);
            return;
        }
        if (Objects.equals(defaultStockingArea, area)) {
            return;
        }
        defaultStockingArea = area;
        defaultScanState = null;
        if (notifyManager && context != null && context.isServer()) {
            context.getSyncmaticManager().markDefaultStockingAreaDirty();
        }
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
        state.process(Math.max(1, scanBlocksPerTick));
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
        defaultScanState.process(Math.max(1, scanBlocksPerTick));
        if (defaultScanState.isFinished()) {
            if (defaultScanState.hasLoadedChunks()) {
                applyDefaultScanResults(defaultScanState.getTotals());
            }
            defaultScanState = null;
        }
    }

    private void schedulePlacementScans(final MinecraftServer server) {
        for (final ServerPlacement placement : placements.values()) {
            final StockingAreaDefinition area = stockingAreas.get(placement.getId());
            if (area == null) {
                continue;
            }
            if (activePlacementScans.containsKey(placement.getId())) {
                continue;
            }
            queuePlacementScan(server, placement, area);
        }
        if (defaultStockingArea != null) {
            if (defaultScanState == null || defaultScanState.isFinished()) {
                final ServerWorld world = resolveWorld(server, defaultStockingArea.getDimensionId());
                defaultScanState = new DefaultStockingScanState(world, defaultStockingArea);
            }
        } else {
            defaultScanState = null;
        }
    }

    public void scanNow(final MinecraftServer server, final ServerPlacement placement) {
        if (!enabled) {
            return;
        }
        final StockingAreaDefinition area = stockingAreas.get(placement.getId());
        if (area != null) {
            cancelPlacementScan(placement.getId());
            queuePlacementScan(server, placement, area);
            return;
        }
        if (defaultStockingArea != null) {
            defaultScanState = new DefaultStockingScanState(
                    resolveWorld(server, defaultStockingArea.getDimensionId()),
                    defaultStockingArea
            );
        }
    }

    public void scanDefaultNow(final MinecraftServer server) {
        if (!enabled || defaultStockingArea == null) {
            return;
        }
        defaultScanState = new DefaultStockingScanState(
                resolveWorld(server, defaultStockingArea.getDimensionId()),
                defaultStockingArea
        );
    }

    private void queuePlacementScan(final MinecraftServer server, final ServerPlacement placement,
                                    final StockingAreaDefinition area) {
        cancelPlacementScan(placement.getId());
        final ServerWorld world = resolveWorld(server, area.getDimensionId());
        final PlacementScanState state = new PlacementScanState(world, area);
        if (state.isFinished()) {
            return;
        }
        activePlacementScans.put(placement.getId(), state);
        placementScanQueue.addLast(placement.getId());
    }

    private void finalizePlacementScan(final UUID placementId, final PlacementScanState state) {
        setStockingContributions(placementId, state.getTotals());
    }

    private void applyDefaultScanResults(final Map<String, Map<MaterialKey, Integer>> totals) {
        for (final ServerPlacement placement : placements.values()) {
            if (stockingAreas.get(placement.getId()) != null) {
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
        configuration.loadBoolean("enabled", this::setEnabled);
        configuration.loadInteger("scan_interval", this::setScanInterval);
        configuration.loadBoolean("include_container_contents", this::setIncludeContainerContents);
        configuration.loadBoolean("allow_owner_stocking_area_management",
                this::setOwnerStockingAreaManagementEnabled);
        configuration.loadInteger("scan_blocks_per_tick", this::setScanBlocksPerTick);
        configuration.loadInteger("max_schematic_megabytes", this::setMaxSchematicMegabytes);
        configuration.loadInteger("max_schematic_blocks", this::setMaxSchematicBlocks);
        configuration.loadInteger("max_stocking_area_blocks", this::setMaxStockingAreaBlocks);
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
                getConfigKey(), "scan_blocks_per_tick", SCAN_BLOCKS_PER_TICK_DEFAULT,
                64, MAX_SCAN_BLOCKS_PER_TICK, () -> scanBlocksPerTick, this::setScanBlocksPerTick));
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

    private void setScanBlocksPerTick(final int value) {
        scanBlocksPerTick = Math.max(64, Math.min(MAX_SCAN_BLOCKS_PER_TICK, value));
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
        stockingAreas.clear();
        defaultStockingArea = null;
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


    private net.minecraft.text.Text getSignLine(final net.minecraft.block.entity.SignBlockEntity sign, final int row) {
//#if MC >= 12001
//$$         final SignText front = sign.getFrontText();
//$$         final net.minecraft.text.Text frontLine = front.getMessage(row, false);
//$$         if (!frontLine.getString().isEmpty()) {
//$$             return frontLine;
//$$         }
//$$         return sign.getBackText().getMessage(row, false);
//#else
        return sign.getTextOnRow(row, false);
//#endif
    }

    private java.util.List<String> readSignNames(final net.minecraft.block.entity.SignBlockEntity sign) {
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

    private Inventory resolveInventoryForSign(final ServerWorld world, final BlockPos signPos) {
        final BlockPos containerPos = resolveContainerPosForSign(world, signPos);
        if (containerPos == null) {
            return null;
        }
        return getInventoryAt(world, containerPos);
    }

    private BlockPos resolveContainerPosForSign(final ServerWorld world, final BlockPos signPos) {
        final net.minecraft.block.BlockState state = world.getBlockState(signPos);
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
        final BlockEntity be = world.getBlockEntity(candidate);
        if (!(be instanceof Inventory)) {
            return null;
        }
        return candidate;
    }

    private Inventory getInventoryAt(final ServerWorld world, final BlockPos pos) {
        final net.minecraft.block.BlockState state = world.getBlockState(pos);
        final BlockEntity be = world.getBlockEntity(pos);
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
                final BlockEntity otherBe = world.getBlockEntity(otherPos);
                if (otherBe != null && otherBe.getType() == type && otherBe instanceof Inventory) {
                    return new net.minecraft.inventory.DoubleInventory(primary, (Inventory) otherBe);
                }
            }
        }
        return (Inventory) be;
    }

    private final class PlacementScanState {
        private final ServerWorld world;
        private final Iterator<BlockPos> iterator;
        private final Map<MaterialKey, Integer> totals = new HashMap<>();
        private boolean finished;
        private boolean hasLoadedChunks;

        PlacementScanState(final ServerWorld world, final StockingAreaDefinition area) {
            this.world = world;
            if (world == null || area == null || !isStockingAreaAllowed(area)) {
                iterator = Collections.emptyIterator();
                finished = true;
            } else {
                iterator = BlockPos.iterate(area.getMin(), area.getMax()).iterator();
            }
        }

        void process(final int budget) {
            if (finished) {
                return;
            }
            if (world == null) {
                finished = true;
                return;
            }
            int remaining = Math.max(1, budget);
            while (remaining > 0 && iterator.hasNext()) {
                remaining--;
                final BlockPos pos = iterator.next();
                if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    continue;
                }
                hasLoadedChunks = true;
                final BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof Inventory inventory) {
                    scanInventory(inventory, totals);
                }
            }
            if (!iterator.hasNext()) {
                finished = true;
            }
        }

        boolean isFinished() {
            return finished;
        }

        Map<MaterialKey, Integer> getTotals() {
            return totals;
        }

        boolean hasLoadedChunks() {
            return hasLoadedChunks;
        }
    }

    private final class DefaultStockingScanState {
        private final ServerWorld world;
        private final Iterator<BlockPos> iterator;
        private final Map<String, Map<MaterialKey, Integer>> totals = new HashMap<>();
        private final Map<String, Set<BlockPos>> scannedContainers = new HashMap<>();
        private final Set<String> knownPlacementNames = new HashSet<>();
        private boolean finished;
        private boolean hasLoadedChunks;

        DefaultStockingScanState(final ServerWorld world, final StockingAreaDefinition area) {
            this.world = world;
            for (final ServerPlacement placement : placements.values()) {
                knownPlacementNames.add(placement.getName());
            }
            if (world == null || area == null || !isStockingAreaAllowed(area)) {
                iterator = Collections.emptyIterator();
                finished = true;
            } else {
                iterator = BlockPos.iterate(area.getMin(), area.getMax()).iterator();
            }
        }

        private BlockPos getCanonicalContainerPos(final BlockPos containerPos) {
            final net.minecraft.block.BlockState state = world.getBlockState(containerPos);
            if (!(state.getBlock() instanceof net.minecraft.block.ChestBlock)) {
                return containerPos;
            }
            final BlockEntity be = world.getBlockEntity(containerPos);
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
                final BlockEntity otherBe = world.getBlockEntity(otherPos);
                if (otherBe != null && otherBe.getType() == type && otherBe instanceof Inventory) {
                    final int minX = Math.min(containerPos.getX(), otherPos.getX());
                    final int minY = Math.min(containerPos.getY(), otherPos.getY());
                    final int minZ = Math.min(containerPos.getZ(), otherPos.getZ());
                    return new BlockPos(minX, minY, minZ);
                }
            }
            return containerPos;
        }

        void process(final int budget) {
            if (finished) {
                return;
            }
            if (world == null) {
                finished = true;
                return;
            }
            int remaining = Math.max(1, budget);
            while (remaining > 0 && iterator.hasNext()) {
                remaining--;
                final BlockPos pos = iterator.next();
                if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    continue;
                }
                hasLoadedChunks = true;
                final BlockEntity blockEntity = world.getBlockEntity(pos);
                if (!(blockEntity instanceof net.minecraft.block.entity.SignBlockEntity sign)) {
                    continue;
                }
                final java.util.List<String> names = readSignNames(sign);
                names.removeIf(name -> !knownPlacementNames.contains(name));
                if (names.isEmpty()) {
                    continue;
                }
                final BlockPos containerPos = resolveContainerPosForSign(world, pos);
                if (containerPos == null) {
                    continue;
                }
                final BlockPos canonicalPos = getCanonicalContainerPos(containerPos).toImmutable();
                
                // Check if all projects have already scanned this container
                boolean needsScan = false;
                for (final String projectName : names) {
                    if (!scannedContainers.computeIfAbsent(projectName, key -> new HashSet<>()).contains(canonicalPos)) {
                        needsScan = true;
                        break;
                    }
                }
                if (!needsScan) {
                    continue;
                }
                
                final Inventory inventory = getInventoryAt(world, containerPos);
                if (inventory == null) {
                    continue;
                }
                for (final String projectName : names) {
                    final Set<BlockPos> scanned = scannedContainers.computeIfAbsent(projectName, key -> new HashSet<>());
                    if (scanned.contains(canonicalPos)) {
                        continue;
                    }
                    scanned.add(canonicalPos);
                    final Map<MaterialKey, Integer> projectTotals = totals.computeIfAbsent(projectName, key -> new HashMap<>());
                    scanInventory(inventory, projectTotals);
                }
            }
            if (!iterator.hasNext()) {
                finished = true;
            }
        }

        boolean isFinished() {
            return finished;
        }

        Map<String, Map<MaterialKey, Integer>> getTotals() {
            return totals;
        }

        boolean hasLoadedChunks() {
            return hasLoadedChunks;
        }
    }

    private void scanInventory(final Inventory inventory, final Map<MaterialKey, Integer> totals) {
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
