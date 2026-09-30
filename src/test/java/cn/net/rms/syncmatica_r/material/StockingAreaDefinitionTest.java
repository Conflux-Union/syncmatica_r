package cn.net.rms.syncmatica_r.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class StockingAreaDefinitionTest {
    @Test
    void calculatesInclusiveVolumeWithoutDependingOnCornerOrder() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld",
                new BlockPos(2, 3, 4),
                new BlockPos(0, 0, 0)
        );

        assertEquals(60L, area.getVolume());
    }

    @Test
    void saturatesOverflowingVolume() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld",
                new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        );

        assertEquals(Long.MAX_VALUE, area.getVolume());
    }

    @Test
    void derivesChunkRangeIncludingNegativeCoordinates() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld",
                new BlockPos(-1, 0, -17),
                new BlockPos(17, 8, 0)
        );

        assertEquals(-1, area.getMinChunkX());
        assertEquals(1, area.getMaxChunkX());
        assertEquals(-2, area.getMinChunkZ());
        assertEquals(0, area.getMaxChunkZ());
    }

    @Test
    void containsChecksInclusiveBoundsOnEveryAxis() {
        final StockingAreaDefinition area = new StockingAreaDefinition(
                "minecraft:overworld",
                new BlockPos(-2, 3, -4),
                new BlockPos(5, 7, 6)
        );

        assertTrue(area.contains(new BlockPos(-2, 3, -4)));
        assertTrue(area.contains(new BlockPos(5, 7, 6)));
        assertTrue(area.contains(new BlockPos(0, 5, 0)));
        assertFalse(area.contains(new BlockPos(-3, 5, 0)));
        assertFalse(area.contains(new BlockPos(0, 8, 0)));
        assertFalse(area.contains(new BlockPos(0, 5, 7)));
    }
}
