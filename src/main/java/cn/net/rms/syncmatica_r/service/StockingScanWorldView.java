package cn.net.rms.syncmatica_r.service;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * The world access a stocking-area scan needs, narrowed so the chunk
 * enumeration can also run against synthetic worlds in tests. All methods
 * are called on the server thread only.
 */
interface StockingScanWorldView {
    boolean isChunkLoaded(int chunkX, int chunkZ);

    /**
     * Returns the live block-entity map of the chunk at the given chunk
     * coordinates, or null when the chunk is not loaded at full status.
     */
    Map<BlockPos, BlockEntity> getBlockEntities(int chunkX, int chunkZ);

    BlockEntity getBlockEntity(BlockPos pos);

    BlockState getBlockState(BlockPos pos);
}
