package cn.net.rms.syncmatica_r.build_management;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** An inclusive world-space cuboid occupied by one sub-region of a placement. */
public final class RegionBounds {
    private final BlockPos min;
    private final BlockPos max;

    public RegionBounds(final BlockPos cornerA, final BlockPos cornerB) {
        min = new BlockPos(
                Math.min(cornerA.getX(), cornerB.getX()),
                Math.min(cornerA.getY(), cornerB.getY()),
                Math.min(cornerA.getZ(), cornerB.getZ())
        );
        max = new BlockPos(
                Math.max(cornerA.getX(), cornerB.getX()),
                Math.max(cornerA.getY(), cornerB.getY()),
                Math.max(cornerA.getZ(), cornerB.getZ())
        );
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }

    public boolean contains(final int x, final int y, final int z) {
        return x >= min.getX() && x <= max.getX()
                && y >= min.getY() && y <= max.getY()
                && z >= min.getZ() && z <= max.getZ();
    }

    public boolean contains(final BlockPos pos) {
        return pos != null && contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public long getVolume() {
        final long x = (long) max.getX() - min.getX() + 1L;
        final long y = (long) max.getY() - min.getY() + 1L;
        final long z = (long) max.getZ() - min.getZ() + 1L;
        return x * y * z;
    }

    /** @return the smallest box containing both, used to pre-filter a placement. */
    public RegionBounds union(final RegionBounds other) {
        if (other == null) {
            return this;
        }
        return new RegionBounds(
                new BlockPos(
                        Math.min(min.getX(), other.min.getX()),
                        Math.min(min.getY(), other.min.getY()),
                        Math.min(min.getZ(), other.min.getZ())),
                new BlockPos(
                        Math.max(max.getX(), other.max.getX()),
                        Math.max(max.getY(), other.max.getY()),
                        Math.max(max.getZ(), other.max.getZ()))
        );
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RegionBounds)) {
            return false;
        }
        final RegionBounds other = (RegionBounds) obj;
        return min.equals(other.min) && max.equals(other.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return "RegionBounds[" + format(min) + " -> " + format(max) + "]";
    }

    private static String format(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
