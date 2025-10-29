package ch.endte.syncmatica.service;

import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.ServerPosition;
import ch.endte.syncmatica.extended_core.PlayerIdentifier;
import ch.endte.syncmatica.material.MaterialKey;
import ch.endte.syncmatica.material.MaterialProgressEntry;
import ch.endte.syncmatica.material.MaterialProgressState;
import ch.endte.syncmatica.material.StockingAreaDefinition;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;

/**
 * Aggregates material requirements, manual contributions, and stocking scans per placement.
 */
public class MaterialService extends AbstractService {
    public static final boolean ENABLED_DEFAULT = false;
    public static final int SCAN_INTERVAL_DEFAULT = 200;

    // Tracks every placement currently managed on the server.
    private final Map<UUID, ServerPlacement> placements = new HashMap<>();
    // Required material counts derived from schematics.
    private final Map<UUID, Map<MaterialKey, Integer>> requiredTotals = new HashMap<>();
    // Player declared contributions keyed by material.
    private final Map<UUID, Map<MaterialKey, Integer>> playerSuppliedTotals = new HashMap<>();
    // Stocking area scans folded into material counts.
    private final Map<UUID, Map<MaterialKey, Integer>> stockingTotals = new HashMap<>();
    // Claim assignments stored per material entry.
    private final Map<UUID, Map<MaterialKey, PlayerIdentifier>> claimants = new HashMap<>();
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
        rebuildSnapshot(placement, false);
    }

    public void detachPlacement(final ServerPlacement placement) {
        placements.remove(placement.getId());
        requiredTotals.remove(placement.getId());
        playerSuppliedTotals.remove(placement.getId());
        stockingTotals.remove(placement.getId());
        claimants.remove(placement.getId());
        stockingAreas.remove(placement.getId());
    }

    public void replaceRequirements(final UUID placementId, final Map<MaterialKey, Integer> required) {
        requiredTotals.put(placementId, new HashMap<>(required));
        final ServerPlacement placement = placements.get(placementId);
        if (placement != null) {
            rebuildSnapshot(placement, true);
        }
    }

    public void setManualContribution(final UUID placementId, final MaterialKey key, final int amount) {
        playerSuppliedTotals.computeIfAbsent(placementId, unused -> new HashMap<>()).put(key, Math.max(0, amount));
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

    public void setClaimant(final UUID placementId, final MaterialKey key, final PlayerIdentifier identifier) {
        claimants.computeIfAbsent(placementId, unused -> new HashMap<>()).put(key, identifier);
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
        playerSuppliedTotals.clear();
        stockingTotals.clear();
        claimants.clear();
        stockingAreas.clear();
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
        final Map<MaterialKey, Integer> manual = playerSuppliedTotals.getOrDefault(placementId, Collections.emptyMap());
        final Map<MaterialKey, Integer> stock = stockingTotals.getOrDefault(placementId, Collections.emptyMap());
        final Map<MaterialKey, PlayerIdentifier> claims = claimants.getOrDefault(placementId, Collections.emptyMap());

        final Set<MaterialKey> keys = new HashSet<>();
        keys.addAll(required.keySet());
        keys.addAll(manual.keySet());
        keys.addAll(stock.keySet());

        final MaterialProgressState snapshot = placement.getMaterialProgress();
        snapshot.clear();
        for (final MaterialKey key : keys) {
            final int requiredAmount = required.getOrDefault(key, 0);
            final MaterialProgressEntry entry = snapshot.getOrCreate(key, requiredAmount);
            entry.setPlayerSupplied(manual.getOrDefault(key, 0));
            entry.setStockingSupplied(stock.getOrDefault(key, 0));
            entry.setClaimedBy(claims.getOrDefault(key, null));
        }
        if (notify) {
            context.getSyncmaticManager().updateServerPlacement(placement);
        }
    }

    private void seedFromExistingSnapshot(final ServerPlacement placement) {
        placement.getMaterialList().updateFrom(placement.getMaterialProgress());
        final UUID placementId = placement.getId();
        final Map<MaterialKey, Integer> required = requiredTotals.computeIfAbsent(placementId, unused -> new HashMap<>());
        final Map<MaterialKey, Integer> manual = playerSuppliedTotals.computeIfAbsent(placementId, unused -> new HashMap<>());
        final Map<MaterialKey, Integer> stock = stockingTotals.computeIfAbsent(placementId, unused -> new HashMap<>());
        final Map<MaterialKey, PlayerIdentifier> claims = claimants.computeIfAbsent(placementId, unused -> new HashMap<>());
        required.clear();
        manual.clear();
        stock.clear();
        claims.clear();
        placement.getMaterialProgress().getEntries().forEach(entry -> {
            required.put(entry.getKey(), entry.getRequiredAmount());
            manual.put(entry.getKey(), entry.getPlayerSupplied());
            stock.put(entry.getKey(), entry.getStockingSupplied());
            if (entry.getClaimedBy() != null) {
                claims.put(entry.getKey(), entry.getClaimedBy());
            }
        });
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
            final BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            final Identifier blockId = Registry.BLOCK.getId(state.getBlock());
            final MaterialKey key = new MaterialKey(blockId, "");
            totals.merge(key, 1, Integer::sum);
        }
        setStockingContributions(placement.getId(), totals);
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
        final RegistryKey<World> key = RegistryKey.of(Registry.DIMENSION, new Identifier(dimensionId));
        return server.getWorld(key);
    }
}
