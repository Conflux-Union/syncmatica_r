package cn.net.rms.syncmatica_r.material;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
