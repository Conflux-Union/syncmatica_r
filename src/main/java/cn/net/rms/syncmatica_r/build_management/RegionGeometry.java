package cn.net.rms.syncmatica_r.build_management;

import net.minecraft.util.math.BlockPos;

/**
 * The pose-independent shape of one sub-region as stored in the litematic file:
 * its origin relative to the schematic origin, and its size.
 *
 * <p>Both are kept signed. A negative size means the region extends in the
 * negative direction from its origin, and dropping the sign would place the box
 * on the wrong side.
 */
public final class RegionGeometry {
    private final BlockPos position;
    private final BlockPos size;

    public RegionGeometry(final BlockPos position, final BlockPos size) {
        this.position = position;
        this.size = size;
    }

    public BlockPos getPosition() {
        return position;
    }

    public BlockPos getSize() {
        return size;
    }

    /** @return block count of this region, sign-independent. */
    public long getVolume() {
        final long x = Math.abs((long) size.getX());
        final long y = Math.abs((long) size.getY());
        final long z = Math.abs((long) size.getZ());
        return x * y * z;
    }

    @Override
    public String toString() {
        return "RegionGeometry[pos=" + position.getX() + "," + position.getY() + "," + position.getZ()
                + " size=" + size.getX() + "," + size.getY() + "," + size.getZ() + "]";
    }
}
