package cn.net.rms.syncmatica_r.service;

// This benchmark constructs real block entities and bootstraps vanilla
// registries, which was only validated on the 1.17.1 test classpath, so the
// whole body is stripped from every preprocessed version.
//#if MC <= 11701
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import cn.net.rms.syncmatica_r.util.InventoryScanner;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
//#endif

class StockingAreaScanBenchmarkTest {
//#if MC <= 11701

    private static final int BUDGET_PER_TICK = 2048;
    private static final int ROUNDS = 5;

    // Referenced lazily: building this table touches Items, which requires
    // the registry bootstrap from BeforeAll to have run already.
    private static Item[] itemPool() {
        return new Item[]{Items.STONE, Items.DIRT, Items.OAK_PLANKS, Items.GLASS};
    }

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void sparseWarehouseScanIsFasterAndEquivalent() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld", new BlockPos(0, 0, 0), new BlockPos(127, 31, 255));
        final SyntheticWorld world = new SyntheticWorld();
        loadAllChunks(world, area);
        placeChests(world, area, 2048, pos -> true);

        final ScanRun legacy = runLegacy(world, area);
        final ScanRun current = runCurrent(world, area);

        printComparison("sparse warehouse (128x32x256, 2048 chests)", legacy, current);

        assertEquals(legacy.totals, current.totals);
        assertTrue(legacy.worldOps >= 50L * current.worldOps,
                "expected >= 50x fewer world operations, legacy=" + legacy.worldOps
                        + " current=" + current.worldOps);
        assertTrue(current.ticks < legacy.ticks);
        assertTrue(legacy.medianNanos >= 5L * current.medianNanos,
                "expected >= 5x wall-clock speedup, legacy=" + legacy.medianNanos
                        + "ns current=" + current.medianNanos + "ns");
    }

    @Test
    void denseSmallWarehouseScanStaysEquivalent() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld", new BlockPos(0, 0, 0), new BlockPos(15, 7, 15));
        final SyntheticWorld world = new SyntheticWorld();
        loadAllChunks(world, area);
        placeChests(world, area, 512, pos -> true);

        final ScanRun legacy = runLegacy(world, area);
        final ScanRun current = runCurrent(world, area);

        printComparison("dense small warehouse (16x8x16, 512 chests)", legacy, current);

        assertEquals(legacy.totals, current.totals);
        assertTrue(legacy.worldOps >= 2L * current.worldOps,
                "expected >= 2x fewer world operations, legacy=" + legacy.worldOps
                        + " current=" + current.worldOps);
    }

    @Test
    void partiallyUnloadedAreaDoesNotBurnBudgetOnUnloadedChunks() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld", new BlockPos(0, 0, 0), new BlockPos(127, 31, 255));
        final SyntheticWorld world = new SyntheticWorld();
        // Every other chunk column stays unloaded, mimicking an idle storage
        // hall outside any player's view distance.
        loadAllChunks(world, area, (chunkX, chunkZ) -> (chunkX + chunkZ) % 2 == 0);
        placeChests(world, area, 8,
                pos -> (pos.getX() >> 4) % 2 == 0 && pos.getZ() == 0);

        final ScanRun legacy = runLegacy(world, area);
        final ScanRun current = runCurrent(world, area);

        printComparison("half-unloaded area (128x32x256, 8 chests)", legacy, current);

        assertEquals(legacy.totals, current.totals);
        assertTrue(legacy.worldOps >= 10L * current.worldOps,
                "expected >= 10x fewer world operations, legacy=" + legacy.worldOps
                        + " current=" + current.worldOps);
        assertTrue(current.ticks < legacy.ticks);
    }

    // ---- scan drivers ----

    private static final class ScanRun {
        final Map<MaterialKey, Integer> totals;
        final long worldOps;
        final int ticks;
        final long medianNanos;

        ScanRun(final Map<MaterialKey, Integer> totals, final long worldOps,
                final int ticks, final long medianNanos) {
            this.totals = totals;
            this.worldOps = worldOps;
            this.ticks = ticks;
            this.medianNanos = medianNanos;
        }
    }

    private static ScanRun runLegacy(final SyntheticWorld world, final StockingAreaDefinition area) {
        final List<Long> durations = new ArrayList<>();
        Map<MaterialKey, Integer> totals = null;
        int ticks = 0;
        for (int round = 0; round < ROUNDS; round++) {
            world.blockEntityLookups = 0;
            final LegacyBlockScan scan = new LegacyBlockScan(world, area);
            final long start = System.nanoTime();
            ticks = 0;
            while (!scan.isFinished() && ticks < 10_000) {
                scan.process(BUDGET_PER_TICK);
                ticks++;
            }
            durations.add(System.nanoTime() - start);
            totals = scan.totals;
        }
        return new ScanRun(totals, world.blockEntityLookups, ticks, median(durations));
    }

    private static ScanRun runCurrent(final SyntheticWorld world, final StockingAreaDefinition area) {
        final List<Long> durations = new ArrayList<>();
        Map<MaterialKey, Integer> totals = null;
        int ticks = 0;
        long worldOps = 0;
        for (int round = 0; round < ROUNDS; round++) {
            final MaterialService.PlacementScanState scan =
                    new MaterialService.PlacementScanState(world, area, true);
            final long start = System.nanoTime();
            ticks = 0;
            while (!scan.isFinished() && ticks < 10_000) {
                scan.process(BUDGET_PER_TICK);
                ticks++;
            }
            durations.add(System.nanoTime() - start);
            totals = scan.getTotals();
            worldOps = scan.getChunksFetched() + (long) scan.getBlockEntitiesInspected();
        }
        return new ScanRun(totals, worldOps, ticks, median(durations));
    }

    private static long median(final List<Long> values) {
        final List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        return sorted.get(sorted.size() / 2);
    }

    private static void printComparison(final String scenario, final ScanRun legacy, final ScanRun current) {
        System.out.printf("[benchmark] %s%n", scenario);
        System.out.printf("[benchmark]   legacy: ops=%,d  ticks=%d  median=%.2f ms%n",
                legacy.worldOps, legacy.ticks, legacy.medianNanos / 1_000_000.0);
        System.out.printf("[benchmark]   new:    ops=%,d  ticks=%d  median=%.2f ms%n",
                current.worldOps, current.ticks, current.medianNanos / 1_000_000.0);
        System.out.printf("[benchmark]   speedup: ops %.1fx, wall %.1fx; totals identical: %b%n",
                legacy.worldOps / (double) current.worldOps,
                legacy.medianNanos / (double) current.medianNanos,
                legacy.totals.equals(current.totals));
    }

    // ---- frozen legacy implementation (pre-rewrite block-by-block scan) ----

    /**
     * Frozen copy of the block-by-block placement scan this benchmark
     * compares against: iterates every BlockPos of the area and asks the
     * world for a block entity at each one. Do not "fix" this class; its
     * cost profile is the baseline being measured.
     */
    private static final class LegacyBlockScan {
        private final StockingScanWorldView view;
        private final Iterator<BlockPos> iterator;
        private final Map<MaterialKey, Integer> totals = new HashMap<>();
        private boolean finished;

        LegacyBlockScan(final StockingScanWorldView view, final StockingAreaDefinition area) {
            this.view = view;
            this.iterator = BlockPos.iterate(area.getMin(), area.getMax()).iterator();
        }

        void process(final int budget) {
            if (finished) {
                return;
            }
            int remaining = Math.max(1, budget);
            while (remaining > 0 && iterator.hasNext()) {
                remaining--;
                final BlockPos pos = iterator.next();
                if (!view.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    continue;
                }
                final BlockEntity blockEntity = view.getBlockEntity(pos);
                if (blockEntity instanceof Inventory inventory) {
                    for (int slot = 0; slot < inventory.size(); slot++) {
                        InventoryScanner.scanItemStack(inventory.getStack(slot), totals);
                    }
                }
            }
            if (!iterator.hasNext()) {
                finished = true;
            }
        }

        boolean isFinished() {
            return finished;
        }
    }

    // ---- synthetic world ----

    /**
     * In-memory stand-in for a server world: per-chunk block-entity maps plus
     * a loaded-chunk set, instrumented to count per-block lookups.
     */
    private static final class SyntheticWorld implements StockingScanWorldView {
        private final Map<Long, Map<BlockPos, BlockEntity>> chunks = new HashMap<>();
        private final Set<Long> loadedChunks = new HashSet<>();
        private long blockEntityLookups;

        void setChunkLoaded(final int chunkX, final int chunkZ, final boolean loaded) {
            final long key = chunkKey(chunkX, chunkZ);
            if (loaded) {
                loadedChunks.add(key);
                chunks.computeIfAbsent(key, unused -> new HashMap<>());
            } else {
                loadedChunks.remove(key);
            }
        }

        void addBlockEntity(final BlockPos pos, final BlockEntity blockEntity) {
            chunks.computeIfAbsent(chunkKey(pos.getX() >> 4, pos.getZ() >> 4),
                    unused -> new HashMap<>()).put(pos, blockEntity);
        }

        @Override
        public boolean isChunkLoaded(final int chunkX, final int chunkZ) {
            return loadedChunks.contains(chunkKey(chunkX, chunkZ));
        }

        @Override
        public Map<BlockPos, BlockEntity> getBlockEntities(final int chunkX, final int chunkZ) {
            if (!isChunkLoaded(chunkX, chunkZ)) {
                return null;
            }
            final Map<BlockPos, BlockEntity> map = chunks.get(chunkKey(chunkX, chunkZ));
            return map != null ? map : Collections.emptyMap();
        }

        @Override
        public BlockEntity getBlockEntity(final BlockPos pos) {
            blockEntityLookups++;
            final Map<BlockPos, BlockEntity> map = chunks.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            if (map == null || !isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                return null;
            }
            return map.get(pos);
        }

        @Override
        public BlockState getBlockState(final BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        private static long chunkKey(final int chunkX, final int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        }
    }

    // ---- scenario helpers ----

    private interface ChunkFilter {
        boolean accepts(int chunkX, int chunkZ);
    }

    private interface PlacementFilter {
        boolean accepts(BlockPos pos);
    }

    private static void loadAllChunks(final SyntheticWorld world, final StockingAreaDefinition area) {
        loadAllChunks(world, area, (chunkX, chunkZ) -> true);
    }

    private static void loadAllChunks(final SyntheticWorld world, final StockingAreaDefinition area,
                                      final ChunkFilter filter) {
        for (int chunkZ = area.getMinChunkZ(); chunkZ <= area.getMaxChunkZ(); chunkZ++) {
            for (int chunkX = area.getMinChunkX(); chunkX <= area.getMaxChunkX(); chunkX++) {
                world.setChunkLoaded(chunkX, chunkZ, filter.accepts(chunkX, chunkZ));
            }
        }
    }

    private static void placeChests(final SyntheticWorld world, final StockingAreaDefinition area,
                                    final int count, final PlacementFilter filter) {
        int placed = 0;
        for (int y = area.getMin().getY(); y <= area.getMax().getY() && placed < count; y++) {
            for (int x = area.getMin().getX(); x <= area.getMax().getX() && placed < count; x++) {
                for (int z = area.getMin().getZ(); z <= area.getMax().getZ() && placed < count; z++) {
                    final BlockPos pos = new BlockPos(x, y, z);
                    if (!filter.accepts(pos)) {
                        continue;
                    }
                    final ChestBlockEntity chest = new ChestBlockEntity(pos, Blocks.CHEST.getDefaultState());
                    final Item[] itemPool = itemPool();
                    for (int slot = 0; slot < chest.size(); slot++) {
                        chest.setStack(slot, new ItemStack(
                                itemPool[(slot + placed) % itemPool.length],
                                1 + (slot * 7 + placed) % 64));
                    }
                    world.addBlockEntity(pos, chest);
                    placed++;
                }
            }
        }
        if (placed < count) {
            throw new IllegalStateException("area too small for " + count + " chests, placed " + placed);
        }
    }
//#endif
}
