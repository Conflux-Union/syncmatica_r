package ch.endte.syncmatica.service;

import ch.endte.syncmatica.ServerPlacement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ch.endte.syncmatica.ServerPosition;
import ch.endte.syncmatica.communication.ServerCommunicationManager;
import ch.endte.syncmatica.material.MaterialKey;
import ch.endte.syncmatica.material.MaterialProgressEntry;
import ch.endte.syncmatica.material.MaterialProgressState;
import ch.endte.syncmatica.material.MaterialRequirementExtractor;
import ch.endte.syncmatica.material.StockingAreaDefinition;
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
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.io.File;
import java.util.*;

/**
 * Aggregates material requirements and stocking scans per placement.
 */
public class MaterialService extends AbstractService {
    private static final Logger LOGGER = LogManager.getLogger(MaterialService.class);
    
    public static final boolean ENABLED_DEFAULT = true;
    public static final int SCAN_INTERVAL_DEFAULT = 200;

    // Tracks every placement currently managed on the server.
    private final Map<UUID, ServerPlacement> placements = new HashMap<>();
    // Required material counts derived from schematics.
    private final Map<UUID, Map<MaterialKey, Integer>> requiredTotals = new HashMap<>();
    // Stocking area scans folded into material counts.
    private final Map<UUID, Map<MaterialKey, Integer>> stockingTotals = new HashMap<>();
    // Cached stocking area definitions for later rescans.
    private final Map<UUID, StockingAreaDefinition> stockingAreas = new HashMap<>();

    private boolean enabled = ENABLED_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private int tickCounter = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void attachPlacement(final ServerPlacement placement) {
        placements.put(placement.getId(), placement);
        stockingAreas.put(placement.getId(), placement.getStockingArea());
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
        rebuildSnapshot(placement, true);
    }

    public StockingAreaDefinition getStockingArea(final UUID placementId) {
        return stockingAreas.get(placementId);
    }

    public void tick(final MinecraftServer server) {
        if (!enabled) {
            return;
        }
        tickCounter++;
        if (tickCounter < scanInterval) {
            return;
        }
        tickCounter = 0;
        for (final ServerPlacement placement : placements.values()) {
            final StockingAreaDefinition area = stockingAreas.get(placement.getId());
            if (area == null) {
                continue;
            }
            scanPlacement(server, placement, area);
        }
    }

    public void scanNow(final MinecraftServer server, final ServerPlacement placement) {
        if (!enabled) {
            return;
        }
        final StockingAreaDefinition area = stockingAreas.get(placement.getId());
        if (area == null) {
            return;
        }
        scanPlacement(server, placement, area);
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        configuration.saveBoolean("enabled", ENABLED_DEFAULT);
        configuration.saveInteger("scan_interval", SCAN_INTERVAL_DEFAULT);
    }

    @Override
    public String getConfigKey() {
        return "materials";
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", value -> enabled = value);
        configuration.loadInteger("scan_interval", value -> scanInterval = Math.max(20, value));
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

        final Set<MaterialKey> keys = new HashSet<>();
        keys.addAll(required.keySet());
        keys.addAll(stock.keySet());

        final MaterialProgressState snapshot = placement.getMaterialProgress();
        snapshot.clear();
        for (final MaterialKey key : keys) {
            final int requiredAmount = required.getOrDefault(key, 0);
            final MaterialProgressEntry entry = snapshot.getOrCreate(key, requiredAmount);
            entry.setStockingSupplied(stock.getOrDefault(key, 0));
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
        LOGGER.debug("Loading material requirements from: {} (exists={})", file.getAbsolutePath(), file.exists());
        final Map<MaterialKey, Integer> result = MaterialRequirementExtractor.extract(file);
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

    private void scanPlacement(final MinecraftServer server, final ServerPlacement placement, final StockingAreaDefinition area) {
        final ServerWorld world = resolveWorld(server, area.getDimensionId());
        if (world == null) {
            return;
        }
        final Map<MaterialKey, Integer> totals = new HashMap<>();
        final BlockPos min = area.getMin();
        final BlockPos max = area.getMax();
        
        // 遍历指定区域内的所有方块位置
        for (final BlockPos pos : BlockPos.iterate(min, max)) {
            final BlockEntity blockEntity = world.getBlockEntity(pos);
            
            // 检查是否是容器（箱子、桶、潜影盒等）
            if (blockEntity instanceof Inventory) {
                final Inventory inventory = (Inventory) blockEntity;
                scanInventory(inventory, totals);
            }
        }
        
        setStockingContributions(placement.getId(), totals);
    }
    
    /**
     * 扫描容器内的所有物品，包括嵌套的潜影盒
     */
    private void scanInventory(final Inventory inventory, final Map<MaterialKey, Integer> totals) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            final ItemStack stack = inventory.getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            
            // 统计当前物品
            final Identifier itemId = Registry.ITEM.getId(stack.getItem());
            final MaterialKey key = new MaterialKey(itemId, "");
            totals.merge(key, stack.getCount(), Integer::sum);
            
            // 检查是否是潜影盒（可能包含嵌套物品）
            if (stack.getItem() instanceof BlockItem) {
                final NbtCompound nbt = stack.getNbt();
                if (nbt != null && nbt.contains("BlockEntityTag")) {
                    final NbtCompound blockEntityTag = nbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("Items")) {
                        scanShulkerBoxContents(blockEntityTag.getList("Items", 10), totals);
                    }
                }
            }
        }
    }
    
    /**
     * 扫描潜影盒NBT数据内的物品（递归处理嵌套潜影盒）
     */
    private void scanShulkerBoxContents(final NbtList itemsNbt, final Map<MaterialKey, Integer> totals) {
        for (int i = 0; i < itemsNbt.size(); i++) {
            final NbtCompound itemNbt = itemsNbt.getCompound(i);
            if (!itemNbt.contains("id") || !itemNbt.contains("Count")) {
                continue;
            }
            
            try {
                final Identifier itemId = new Identifier(itemNbt.getString("id"));
                final int count = itemNbt.getByte("Count");
                
                // 统计潜影盒内的物品
                final MaterialKey key = new MaterialKey(itemId, "");
                totals.merge(key, count, Integer::sum);
                
                // 递归处理嵌套的潜影盒
                if (itemNbt.contains("tag")) {
                    final NbtCompound tag = itemNbt.getCompound("tag");
                    if (tag.contains("BlockEntityTag")) {
                        final NbtCompound blockEntityTag = tag.getCompound("BlockEntityTag");
                        if (blockEntityTag.contains("Items")) {
                            scanShulkerBoxContents(blockEntityTag.getList("Items", 10), totals);
                        }
                    }
                }
            } catch (final Exception e) {
                // 忽略无效的物品ID
            }
        }
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
        final RegistryKey<World> key = RegistryKey.of(Registry.WORLD_KEY, new Identifier(dimensionId));
        return server.getWorld(key);
    }
}
