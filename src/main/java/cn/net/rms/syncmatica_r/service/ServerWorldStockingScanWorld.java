package cn.net.rms.syncmatica_r.service;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;

final class ServerWorldStockingScanWorld implements StockingScanWorldView {
    private final ServerWorld world;

    ServerWorldStockingScanWorld(final ServerWorld world) {
        this.world = world;
    }

    @Override
    public boolean isChunkLoaded(final int chunkX, final int chunkZ) {
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public Map<BlockPos, BlockEntity> getBlockEntities(final int chunkX, final int chunkZ) {
        // create=false: never loads or generates, returns null for unloaded chunks.
        final Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (!(chunk instanceof WorldChunk)) {
            return null;
        }
        return ((WorldChunk) chunk).getBlockEntities();
    }

    @Override
    public BlockEntity getBlockEntity(final BlockPos pos) {
        return world.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(final BlockPos pos) {
        return world.getBlockState(pos);
    }
}
