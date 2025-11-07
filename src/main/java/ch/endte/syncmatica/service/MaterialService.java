package ch.endte.syncmatica.service;

import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.ServerPosition;
import ch.endte.syncmatica.communication.ServerCommunicationManager;
import ch.endte.syncmatica.material.*;
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
//#if MC >= 12001
//$$ import net.minecraft.registry.RegistryKeys;
//$$ import net.minecraft.block.entity.SignText;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class MaterialService extends AbstractService {
    public static final boolean ENABLED_DEFAULT = true;
    public static final int SCAN_INTERVAL_DEFAULT = 200;
    public static final boolean INCLUDE_CONTAINER_CONTENTS_DEFAULT = false;
    private static final Logger LOGGER = LogManager.getLogger(MaterialService.class);
    private final Map<UUID, ServerPlacement> placements = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> requiredTotals = new HashMap<>();

    private final Map<UUID, Map<MaterialKey, Integer>> stockingTotals = new HashMap<>();

    private final Map<UUID, StockingAreaDefinition> stockingAreas = new HashMap<>();

    private StockingAreaDefinition defaultStockingArea;

    private boolean enabled = ENABLED_DEFAULT;
    private int scanInterval = SCAN_INTERVAL_DEFAULT;
    private boolean includeContainerContents = INCLUDE_CONTAINER_CONTENTS_DEFAULT;
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

    public StockingAreaDefinition getDefaultStockingArea() {
        return defaultStockingArea;
    }

    public void setDefaultStockingArea(final StockingAreaDefinition area) {

        defaultStockingArea = area;
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

        final Map<String, Map<MaterialKey, Integer>> defaultTotals =
                (defaultStockingArea != null) ? scanDefaultArea(server, defaultStockingArea) : Collections.emptyMap();

        for (final ServerPlacement placement : placements.values()) {
            final StockingAreaDefinition area = stockingAreas.get(placement.getId());
            if (area != null) {
                scanPlacement(server, placement, area);
                continue;
            }
            if (defaultStockingArea != null) {
                final Map<MaterialKey, Integer> totals = defaultTotals.getOrDefault(placement.getName(), Collections.emptyMap());
                setStockingContributions(placement.getId(), totals);
            }
        }
    }

    public void scanNow(final MinecraftServer server, final ServerPlacement placement) {
        if (!enabled) {
            return;
        }
        final StockingAreaDefinition area = stockingAreas.get(placement.getId());
        if (area != null) {
            scanPlacement(server, placement, area);
            return;
        }
        if (defaultStockingArea != null) {
            final Map<String, Map<MaterialKey, Integer>> map = scanDefaultArea(server, defaultStockingArea);
            final Map<MaterialKey, Integer> totals = map.getOrDefault(placement.getName(), Collections.emptyMap());
            setStockingContributions(placement.getId(), totals);
        }
    }

    public void scanDefaultNow(final MinecraftServer server) {
        if (!enabled || defaultStockingArea == null) {
            return;
        }
        final Map<String, Map<MaterialKey, Integer>> map = scanDefaultArea(server, defaultStockingArea);
        for (final ServerPlacement placement : placements.values()) {
            if (stockingAreas.get(placement.getId()) == null) {
                final Map<MaterialKey, Integer> totals = map.getOrDefault(placement.getName(), Collections.emptyMap());
                setStockingContributions(placement.getId(), totals);
            }
        }
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        configuration.saveBoolean("enabled", ENABLED_DEFAULT);
        configuration.saveInteger("scan_interval", SCAN_INTERVAL_DEFAULT);
        configuration.saveBoolean("include_container_contents", INCLUDE_CONTAINER_CONTENTS_DEFAULT);
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

        final Set<MaterialKey> keys = new HashSet<>();
        keys.addAll(required.keySet());
        keys.addAll(stock.keySet());

        final MaterialProgressState snapshot = placement.getMaterialProgress();

        final java.util.Map<MaterialKey, java.util.Collection<ch.endte.syncmatica.extended_core.PlayerIdentifier>> previousClaimants = new java.util.HashMap<>();
        for (final MaterialProgressEntry e : snapshot.getEntries()) {
            previousClaimants.put(e.getKey(), new java.util.ArrayList<>(e.getClaimants()));
        }
        snapshot.clear();
        for (final MaterialKey key : keys) {
            final int requiredAmount = required.getOrDefault(key, 0);
            final MaterialProgressEntry entry = snapshot.getOrCreate(key, requiredAmount);
            entry.setStockingSupplied(stock.getOrDefault(key, 0));
            final java.util.Collection<ch.endte.syncmatica.extended_core.PlayerIdentifier> claim = previousClaimants.get(key);
            if (claim != null) {
                for (final ch.endte.syncmatica.extended_core.PlayerIdentifier p : claim) {
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
        LOGGER.debug("Loading material requirements from: {} (exists={})", file.getAbsolutePath(), file.exists());
        final Map<MaterialKey, Integer> result = MaterialRequirementExtractor.extract(file, includeContainerContents);
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

        for (final BlockPos pos : BlockPos.iterate(min, max)) {
            final BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity instanceof Inventory inventory) {
                scanInventory(inventory, totals);
            }
        }

        setStockingContributions(placement.getId(), totals);
    }

    private Map<String, Map<MaterialKey, Integer>> scanDefaultArea(final MinecraftServer server, final StockingAreaDefinition area) {
        final ServerWorld world = resolveWorld(server, area.getDimensionId());
        if (world == null) {
            return Collections.emptyMap();
        }
        final Map<String, Map<MaterialKey, Integer>> result = new HashMap<>();
        final BlockPos min = area.getMin();
        final BlockPos max = area.getMax();

        for (final BlockPos pos : BlockPos.iterate(min, max)) {
            final BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof net.minecraft.block.entity.SignBlockEntity sign)) {
                continue;
            }
            final java.util.List<String> names = new java.util.ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                try {
                    final net.minecraft.text.Text t = getSignLine(sign, i);
                    if (t != null) {
                        final String s = t.getString();
                        if (s != null) {
                            final String trimmed = s.trim();
                            if (!trimmed.isEmpty()) {
                                names.add(trimmed);
                            }
                        }
                    }
                } catch (final Throwable ignored) {

                }
            }
            if (names.isEmpty()) {
                continue;
            }
            final Inventory inv = resolveInventoryForSign(world, pos);
            if (inv == null) {
                continue;
            }
            for (final String projectName : names) {
                final Map<MaterialKey, Integer> totals = result.computeIfAbsent(projectName, k -> new HashMap<>());
                scanInventory(inv, totals);
            }
        }
        return result;
    }

    private net.minecraft.text.Text getSignLine(final net.minecraft.block.entity.SignBlockEntity sign, final int row) {
//#if MC < 12001
        return sign.getTextOnRow(row, false);
//#else
//$$         final SignText front = sign.getFrontText();
//$$         final net.minecraft.text.Text frontLine = front.getMessage(row, false);
//$$         if (!frontLine.getString().isEmpty()) {
//$$             return frontLine;
//$$         }
//$$         return sign.getBackText().getMessage(row, false);
//#endif
    }

    private Inventory resolveInventoryForSign(final ServerWorld world, final BlockPos signPos) {
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
        return getInventoryAt(world, candidate);
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

    private void scanInventory(final Inventory inventory, final Map<MaterialKey, Integer> totals) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            final ItemStack stack = inventory.getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            final Identifier itemId = Registry.ITEM.getId(stack.getItem());
            final MaterialKey key = new MaterialKey(itemId, "");
            totals.merge(key, stack.getCount(), Integer::sum);

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

    private void scanShulkerBoxContents(final NbtList itemsNbt, final Map<MaterialKey, Integer> totals) {
        for (int i = 0; i < itemsNbt.size(); i++) {
            final NbtCompound itemNbt = itemsNbt.getCompound(i);
            if (!itemNbt.contains("id") || !itemNbt.contains("Count")) {
                continue;
            }

            try {
                final Identifier itemId = new Identifier(itemNbt.getString("id"));
                final int count = itemNbt.getByte("Count");

                final MaterialKey key = new MaterialKey(itemId, "");
                totals.merge(key, count, Integer::sum);

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
        final RegistryKey<World> key = RegistryKey.of(
//#if MC < 12001
                Registry.WORLD_KEY,
//#else
//$$                 RegistryKeys.WORLD,
//#endif
                new Identifier(dimensionId));
        return server.getWorld(key);
    }
}
