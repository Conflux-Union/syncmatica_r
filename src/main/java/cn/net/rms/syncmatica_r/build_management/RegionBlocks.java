package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.schematic.PackedBlockStateArray;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * The decoded block layout of one sub-region, kept as identifiers so decoding
 * stays free of the block registry and can run off the server thread.
 */
public final class RegionBlocks {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Identifier[] palette;
    private final PackedBlockStateArray states;

    public RegionBlocks(final BlockPos absoluteSize, final Identifier[] palette,
                        final PackedBlockStateArray states) {
        sizeX = absoluteSize.getX();
        sizeY = absoluteSize.getY();
        sizeZ = absoluteSize.getZ();
        this.palette = palette;
        this.states = states;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public Identifier[] getPalette() {
        return palette;
    }

    public long getVolume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    /**
     * @return index into {@link #getPalette()} for a schematic-local position, or
     *         -1 when the position is outside the region or the data is short
     */
    public int paletteIndexAt(final int x, final int y, final int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return -1;
        }
        if (palette.length == 1) {
            return 0;
        }
        final int index = states.get(PackedBlockStateArray.indexOf(x, y, z, sizeX, sizeZ));
        return index >= 0 && index < palette.length ? index : -1;
    }
}
