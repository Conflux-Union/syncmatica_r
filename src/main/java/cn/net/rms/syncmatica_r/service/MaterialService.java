package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.ServerPosition;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.material.*;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import cn.net.rms.syncmatica_r.util.NbtHelper;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
//#if MC >= 12001
//$$ import net.minecraft.registry.Registries;
//#else
import net.minecraft.util.registry.Registry;
//#endif
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
//#if MC >= 12001
//$$ import net.minecraft.registry.RegistryKeys;
//$$ import net.minecraft.block.entity.SignText;
//#endif
//#if MC >= 12110
//$$ import net.minecraft.component.DataComponentTypes;
//$$ import net.minecraft.component.type.ContainerComponent;
//#elseif MC >= 12005
//$$ import net.minecraft.component.DataComponentTypes;
//$$ import net.minecraft.component.type.NbtComponent;
//#endif
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class MaterialService extends AbstractService {
    public static final boolean ENABLED_DEFAULT = true;
    public static final int SCAN_INTERVAL_DEFAULT = 200;
    public static final boolean INCLUDE_CONTAINER_CONTENTS_DEFAULT = false;
    public static final int SCAN_BLOCKS_PER_TICK_DEFAULT = 2048;
    public static final int MAX_SCHEMATIC_MEGABYTES_DEFAULT = 64;
    public static final int MAX_SCHEMATIC_BLOCKS_DEFAULT = 8_000_000;
    private static final int MAX_SHULKER_NESTING_DEPTH = 10;
    private static final Logger LOGGER = LogManager.getLogger(MaterialService.class);
    private final Map<UUID, ServerPlacement> placements = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> requiredTotals = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> stockingTotals = new HashMap<>();

    private final Map<UUID, StockingAreaDefinition> stockingAreas = new HashMap<>();

    private StockingAreaDefinition defaultStockingArea;

    private boolean enabled = ENABLED_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private boolean includeContainerContents = INCLUDE_CONTAINER_CONTENTS_DEFAULT;
    private int scanBlocksPerTick = SCAN_BLOCKS_PER_TICK_DEFAULT;
    private int maxSchematicMegabytes = MAX_SCHEMATIC_MEGABYTES_DEFAULT;
    private int maxSchematicBlocks = MAX_SCHEMATIC_BLOCKS_DEFAULT;
    private int tickCounter = 0;

    private final Map<UUID, PlacementScanState> activePlacementScans = new HashMap<>();
    private final ArrayDeque<UUID> placementScanQueue = new ArrayDeque<>();
    private DefaultStockingScanState defaultScanState;

    public boolean isEnabled() {
        return enabled;
    }

    public void attachPlacement(final ServerPlacement placement) {
        placements.put(placement.getId(), placement);
        stockingAreas.put(placement.getId(), placement.getStockingArea());
        cancelPlacementScan(placement.getId());
        seedFromExistingSnapshot(placement);
        if (enabled && placement.getMaterialProgress().isEmpty()) {
            final Map<MaterialKey, Integer> required = loadRequirementsFromSchematic(placement);
            if (!required.isEmpty()) {
                requiredTotals.put(placement.getId(), required);
                rebuildSnapshot(placement, true);
                return;
            }
        }
        ensureRequirementsLoaded(placement);
        rebuildSnapshot(placement, false);
    }

    public void detachPlacement(final ServerPlacement placement) {
        placements.remove(placement.getId());
        requiredTotals.remove(placement.getId());
        stockingTotals.remove(placement.getId());
        stockingAreas.remove(placement.getId());
        cancelPlacementScan(placement.getId());
    }

    public void replaceRequirements(final UUID placementId, final Map<MaterialKey, Integer> required) {
        requiredTotals.put(placementId, new HashMap<>(required));
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            rebuildSnapshot(placement, true);
        }
    }

    public void setStockingContributions(final UUID placementId, final Map<MaterialKey, Integer> totals) {
        stockingTotals.put(placementId, new HashMap<>(totals));
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            rebuildSnapshot(placement, true);
        }
    }

    public void setStockingArea(final ServerPlacement placement, final StockingAreaDefinition area) {
        stockingAreas.put(placement.getId(), area);
        placement.setStockingArea(area);
        cancelPlacementScan(placement.getId());
        rebuildSnapshot(placement, true);
        placement.touchModified(System.currentTimeMillis());
        if (context != null) {
            context.getSyncmaticManager().updateServerPlacement(placement);
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
        defaultStockingArea = area;
        defaultScanState = null;
        if (notifyManager && context != null && context.isServer()) {
            context.getSyncmaticManager().markDefaultStockingAreaDirty();
        }
    }

    public void tick(final MinecraftServer server) {
        if (!enabled) {
            return;
        }
        processPlacementScans();
        processDefaultScan();
        tickCounter++;
        if (tickCounter < scanInterval) {
            return;
        }
        tickCounter = 0;
        schedulePlacementScans(server);
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
            runPlacementScanNow(server, placement, area);
            return;
        }
        if (defaultStockingArea != null) {
            defaultScanState = null;
            final Map<String, Map<MaterialKey, Integer>> map = runDefaultScanNow(server);
            final Map<MaterialKey, Integer> totals = map.getOrDefault(placement.getName(), Collections.emptyMap());
            setStockingContributions(placement.getId(), totals);
        }
    }

    public void scanDefaultNow(final MinecraftServer server) {
        if (!enabled || defaultStockingArea == null) {
            return;
        }
        defaultScanState = null;
        final Map<String, Map<MaterialKey, Integer>> map = runDefaultScanNow(server);
        for (final ServerPlacement placement : placements.values()) {
            if (stockingAreas.get(placement.getId()) == null) {
                final Map<MaterialKey, Integer> totals = map.getOrDefault(placement.getName(), Collections.emptyMap());
                setStockingContributions(placement.getId(), totals);
            }
        }
    }

    private void queuePlacementScan(final MinecraftServer server, final ServerPlacement placement,
                                    final StockingAreaDefinition area) {
        cancelPlacementScan(placement.getId());
        final ServerWorld world = resolveWorld(server, area.getDimensionId());
        final PlacementScanState state = new PlacementScanState(world, area);
        if (state.isFinished()) {
            finalizePlacementScan(placement.getId(), state);
            return;
        }
        activePlacementScans.put(placement.getId(), state);
        placementScanQueue.addLast(placement.getId());
    }

    private void runPlacementScanNow(final MinecraftServer server, final ServerPlacement placement,
                                     final StockingAreaDefinition area) {
        final ServerWorld world = resolveWorld(server, area.getDimensionId());
        final PlacementScanState state = new PlacementScanState(world, area);
        while (!state.isFinished()) {
            state.process(Math.max(1, scanBlocksPerTick));
        }
        if (state.hasLoadedChunks()) {
            finalizePlacementScan(placement.getId(), state);
        }
    }

    private Map<String, Map<MaterialKey, Integer>> runDefaultScanNow(final MinecraftServer server) {
        if (defaultStockingArea == null) {
            return Collections.emptyMap();
        }
        final ServerWorld world = resolveWorld(server, defaultStockingArea.getDimensionId());
        final DefaultStockingScanState state = new DefaultStockingScanState(world, defaultStockingArea);
        while (!state.isFinished()) {
            state.process(Math.max(1, scanBlocksPerTick));
        }
        return state.getTotals();
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
        configuration.saveBoolean("enabled", ENABLED_DEFAULT);
        configuration.saveInteger("scan_interval", SCAN_INTERVAL_DEFAULT);
        configuration.saveBoolean("include_container_contents", INCLUDE_CONTAINER_CONTENTS_DEFAULT);
        configuration.saveInteger("scan_blocks_per_tick", SCAN_BLOCKS_PER_TICK_DEFAULT);
        configuration.saveInteger("max_schematic_megabytes", MAX_SCHEMATIC_MEGABYTES_DEFAULT);
        configuration.saveInteger("max_schematic_blocks", MAX_SCHEMATIC_BLOCKS_DEFAULT);
    }

    @Override
    public String getConfigKey() {
        return "materials";
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", value -> enabled = value);
        configuration.loadInteger("scan_interval", value -> scanInterval = Math.max(20, value));
        configuration.loadBoolean("include_container_contents", value -> includeContainerContents = value);
        configuration.loadInteger("scan_blocks_per_tick", value -> scanBlocksPerTick = Math.max(64, value));
        configuration.loadInteger("max_schematic_megabytes", value -> maxSchematicMegabytes = Math.max(1, value));
        configuration.loadInteger("max_schematic_blocks", value -> maxSchematicBlocks = Math.max(1_000_000, value));
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
    }

    private void rebuildSnapshot(final ServerPlacement placement, final boolean notify) {
        if (!enabled) {
            if (notify) {
                context.getSyncmaticManager().updateServerPlacement(placement);
            }
            return;
        }
        ensureRequirementsLoaded(placement);
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

    private Map<MaterialKey, Integer> loadRequirementsFromSchematic(final ServerPlacement placement) {
        final File file = context.getFileStorage().getLocalLitematic(placement);
        if (file == null) {
            LOGGER.warn("Cannot load material requirements for placement '{}' (hash: {}): file not found",
                    placement.getName(), placement.getHash());
            return Collections.emptyMap();
        }
        final long byteLimit = getMaxSchematicBytes();
        if (file.length() > byteLimit) {
            LOGGER.warn("Skipping material extraction for '{}' ({} bytes exceeds limit {} bytes)",
                    placement.getName(), file.length(), byteLimit);
            return Collections.emptyMap();
        }
        LOGGER.debug("Loading material requirements from: {} (exists={})", file.getAbsolutePath(), file.exists());
        final Map<MaterialKey, Integer> result = MaterialRequirementExtractor.extract(
                file,
                includeContainerContents,
                Math.max(1, maxSchematicBlocks)
        );
        LOGGER.debug("Extracted {} material types from placement '{}'", result.size(), placement.getName());
        return result;
    }

    private void ensureRequirementsLoaded(final ServerPlacement placement) {
        final Map<MaterialKey, Integer> required = requiredTotals.computeIfAbsent(placement.getId(), unused -> new HashMap<>());
        if (!required.isEmpty()) {
            return;
        }
        final Map<MaterialKey, Integer> extracted = loadRequirementsFromSchematic(placement);
        if (extracted.isEmpty()) {
            return;
        }
        required.clear();
        required.putAll(extracted);
    }

    public void refreshPlacement(final ServerPlacement placement) {
        if (!enabled) {
            return;
        }
        final Map<MaterialKey, Integer> extracted = loadRequirementsFromSchematic(placement);
        if (extracted.isEmpty()) {
            return;
        }
        requiredTotals.put(placement.getId(), new HashMap<>(extracted));
        final Map<MaterialKey, Integer> stock = stockingTotals.get(placement.getId());
        if (stock != null) {
            stock.keySet().retainAll(extracted.keySet());
        }
        rebuildSnapshot(placement, true);
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
        final java.util.List<String> names = new java.util.ArrayList<>(4);
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
                    names.add(trimmed);
                }
            } catch (final Throwable ignored) {

            }
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
            if (world == null || area == null) {
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
        private boolean finished;
        private boolean hasLoadedChunks;

        DefaultStockingScanState(final ServerWorld world, final StockingAreaDefinition area) {
            this.world = world;
            if (world == null || area == null) {
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
            if (stack == null || stack.isEmpty()) {
                continue;
            }

//#if MC >= 12001
//$$             final Identifier itemId = Registries.ITEM.getId(stack.getItem());
//#else
            final Identifier itemId = Registry.ITEM.getId(stack.getItem());
//#endif
            boolean hasShulkerContents = false;

            if (stack.getItem() instanceof BlockItem) {
//#if MC >= 12110
//$$                 final ContainerComponent container = stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT);
//$$                 if (!container.equals(ContainerComponent.DEFAULT)) {
//$$                     hasShulkerContents = true;
//$$                     for (final ItemStack item : container.iterateNonEmpty()) {
//$$                         final Identifier nestedItemId = Registries.ITEM.getId(item.getItem());
//$$                         final MaterialKey nestedKey = new MaterialKey(nestedItemId, "");
//$$                         totals.merge(nestedKey, item.getCount(), Integer::sum);
//$$                     }
//$$                 }
//#elseif MC >= 12005
//$$                 final NbtComponent blockEntityData = stack.getComponents().getOrDefault(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.DEFAULT);
//$$                 if (!blockEntityData.isEmpty()) {
//$$                     final NbtCompound blockEntityTag = blockEntityData.copyNbt();
//$$                     final NbtList items = NbtHelper.getList(blockEntityTag, "Items");
//$$                     if (items != null && items.size() > 0) {
//$$                         hasShulkerContents = true;
//$$                         scanShulkerBoxContents(items, totals, 0);
//$$                     }
//$$                 }
//#else
                final NbtCompound nbt = stack.getNbt();
                if (nbt != null && nbt.contains("BlockEntityTag")) {
                    final NbtCompound blockEntityTag = nbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("Items")) {
                        final NbtList items = blockEntityTag.getList("Items", 10);
                        if (items != null && items.size() > 0) {
                            hasShulkerContents = true;
                            scanShulkerBoxContents(items, totals, 0);
                        }
                    }
                }
//#endif
            }

            if (!hasShulkerContents) {
                final MaterialKey key = new MaterialKey(itemId, "");
                totals.merge(key, stack.getCount(), Integer::sum);
            }
        }
    }

    private void scanShulkerBoxContents(final NbtList itemsNbt, final Map<MaterialKey, Integer> totals, final int depth) {
        if (depth > MAX_SHULKER_NESTING_DEPTH) {
            LOGGER.warn("Shulker box nesting depth exceeded limit ({}), skipping further scanning", depth);
            return;
        }

        for (int i = 0; i < itemsNbt.size(); i++) {
            final NbtCompound itemNbt = NbtHelper.getCompound(itemsNbt, i);
            if (itemNbt == null) {
                continue;
            }
            final String rawId = NbtHelper.getString(itemNbt, "id");
            if (rawId.isEmpty()) {
                continue;
            }
            final Optional<Identifier> itemId = IdentifierUtil.tryParse(rawId);
            if (!itemId.isPresent()) {
                continue;
            }
            final int count = NbtHelper.getByte(itemNbt, "Count") & 0xFF;
            if (count <= 0) {
                continue;
            }

            final NbtCompound tag = NbtHelper.getCompound(itemNbt, "tag");
            boolean hasNestedContents = false;
            if (tag != null) {
                final NbtCompound blockEntityTag = NbtHelper.getCompound(tag, "BlockEntityTag");
                if (blockEntityTag != null) {
                    final NbtList nestedItems = NbtHelper.getList(blockEntityTag, "Items");
                    if (nestedItems != null && nestedItems.size() > 0) {
                        hasNestedContents = true;
                        scanShulkerBoxContents(nestedItems, totals, depth + 1);
                    }
                }
            }

            if (!hasNestedContents) {
                final MaterialKey itemKey = new MaterialKey(itemId.get(), "");
                totals.merge(itemKey, count, Integer::sum);
            }
        }
    }

    private long getMaxSchematicBytes() {
        return Math.max(1L, maxSchematicMegabytes) * 1024L * 1024L;
    }

    private ServerWorld resolveWorld(final MinecraftServer server, final String dimensionId) {
        if (ServerPosition.OVERWORLD_DIMENSION_ID.equals(dimensionId)) {
            return server.getOverworld();
        }
        if (ServerPosition.NETHER_DIMENSION_ID.equals(dimensionId)) {
            return server.getWorld(World.NETHER);
        }
        if ("minecraft:the_end".equals(dimensionId)) {
            return server.getWorld(World.END);
        }
        final RegistryKey<World> key = RegistryKey.of(
//#if MC >= 12001
//$$                 RegistryKeys.WORLD,
//#else
                Registry.WORLD_KEY,
//#endif
                IdentifierUtil.require(dimensionId));
        return server.getWorld(key);
    }
}
