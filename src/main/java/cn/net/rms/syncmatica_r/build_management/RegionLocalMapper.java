package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/**
 * The world-to-schematic coordinate map of one placed sub-region, solved once.
 *
 * <p>{@link RegionBoundsResolver#toLocalPosition} answers the same question, but
 * it re-derives the placement corner and walks the rotation and mirror cases for
 * every position it is handed. A completion scan asks millions of times per
 * pass, so the answer is worth solving once.
 *
 * <p>Everything that map does is affine: two reverse transforms, each a pure
 * signed axis swap, then a per-axis flip for regions that grow the negative way.
 * Composed, that is
 *
 * <pre>
 * localX = a*worldX + b*worldZ + c
 * localZ = d*worldX + e*worldZ + f
 * localY = g*worldY + h
 * </pre>
 *
 * <p>with every coefficient in {-1, 0, 1}. Rotation turns about the Y axis and
 * mirroring is horizontal, so Y never mixes with the other two. The coefficients
 * are read off by pushing the unit vectors through the same reverse transforms
 * the exact path uses — no case analysis is duplicated here, and nothing has to
 * be kept in step with it.
 */
public final class RegionLocalMapper {

    private final int localXPerWorldX;
    private final int localXPerWorldZ;
    private final int localXOffset;
    private final int localZPerWorldX;
    private final int localZPerWorldZ;
    private final int localZOffset;
    private final int localYPerWorldY;
    private final int localYOffset;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private RegionLocalMapper(final int localXPerWorldX, final int localXPerWorldZ, final int localXOffset,
                              final int localZPerWorldX, final int localZPerWorldZ, final int localZOffset,
                              final int localYPerWorldY, final int localYOffset,
                              final int sizeX, final int sizeY, final int sizeZ) {
        this.localXPerWorldX = localXPerWorldX;
        this.localXPerWorldZ = localXPerWorldZ;
        this.localXOffset = localXOffset;
        this.localZPerWorldX = localZPerWorldX;
        this.localZPerWorldZ = localZPerWorldZ;
        this.localZOffset = localZOffset;
        this.localYPerWorldY = localYPerWorldY;
        this.localYOffset = localYOffset;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    /**
     * @param modification the player's override for this sub-region, or null when
     *                     the region still sits where the schematic put it
     * @return the map, or null when the region has no usable pose
     */
    public static RegionLocalMapper of(final RegionGeometry geometry,
                                       final BlockPos origin,
                                       final BlockRotation placementRotation,
                                       final BlockMirror placementMirror,
                                       final SubRegionPlacementModification modification) {
        if (geometry == null || origin == null) {
            return null;
        }
        final BlockRotation rotation = placementRotation == null ? BlockRotation.NONE : placementRotation;
        final BlockMirror mirror = placementMirror == null ? BlockMirror.NONE : placementMirror;
        final BlockPos regionPos = modification == null ? geometry.getPosition() : modification.position;
        final BlockRotation subRotation = modification == null ? BlockRotation.NONE : modification.rotation;
        final BlockMirror subMirror = modification == null ? BlockMirror.NONE : modification.mirror;
        if (regionPos == null || geometry.getSize() == null) {
            return null;
        }

        final BlockPos placed = RegionBoundsResolver.transform(regionPos, mirror, rotation);
        final int cornerX = placed.getX() + origin.getX();
        final int cornerY = placed.getY() + origin.getY();
        final int cornerZ = placed.getZ() + origin.getZ();

        final BlockPos alongX = reverse(1, 0, subMirror, subRotation, mirror, rotation);
        final BlockPos alongZ = reverse(0, 1, subMirror, subRotation, mirror, rotation);

        final BlockPos size = geometry.getSize();
        final int flipX = size.getX() >= 0 ? 1 : -1;
        final int flipY = size.getY() >= 0 ? 1 : -1;
        final int flipZ = size.getZ() >= 0 ? 1 : -1;

        final int xPerX = flipX * alongX.getX();
        final int xPerZ = flipX * alongZ.getX();
        final int zPerX = flipZ * alongX.getZ();
        final int zPerZ = flipZ * alongZ.getZ();

        return new RegionLocalMapper(
                xPerX, xPerZ, -(xPerX * cornerX + xPerZ * cornerZ),
                zPerX, zPerZ, -(zPerX * cornerX + zPerZ * cornerZ),
                flipY, -flipY * cornerY,
                Math.abs(size.getX()), Math.abs(size.getY()), Math.abs(size.getZ()));
    }

    private static BlockPos reverse(final int x, final int z,
                                    final BlockMirror subMirror, final BlockRotation subRotation,
                                    final BlockMirror mirror, final BlockRotation rotation) {
        return RegionBoundsResolver.reverseTransform(
                RegionBoundsResolver.reverseTransform(new BlockPos(x, 0, z), subMirror, subRotation),
                mirror, rotation);
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

    public int localX(final int worldX, final int worldZ) {
        return localXPerWorldX * worldX + localXPerWorldZ * worldZ + localXOffset;
    }

    public int localY(final int worldY) {
        return localYPerWorldY * worldY + localYOffset;
    }

    public int localZ(final int worldX, final int worldZ) {
        return localZPerWorldX * worldX + localZPerWorldZ * worldZ + localZOffset;
    }

    /** The Y map is its own inverse up to sign, so this needs no division. */
    public int worldY(final int localY) {
        return localYPerWorldY * (localY - localYOffset);
    }

    public boolean containsLocal(final int localX, final int localY, final int localZ) {
        return localX >= 0 && localY >= 0 && localZ >= 0
                && localX < sizeX && localY < sizeY && localZ < sizeZ;
    }

    /**
     * @return the lowest local X any position in that world X/Z box maps to.
     *         The box need not lie inside the region; the answer is then simply
     *         out of range.
     */
    public int lowestLocalX(final int minWorldX, final int minWorldZ, final int maxWorldX, final int maxWorldZ) {
        return extreme(localXPerWorldX, minWorldX, maxWorldX, true)
                + extreme(localXPerWorldZ, minWorldZ, maxWorldZ, true) + localXOffset;
    }

    public int highestLocalX(final int minWorldX, final int minWorldZ, final int maxWorldX, final int maxWorldZ) {
        return extreme(localXPerWorldX, minWorldX, maxWorldX, false)
                + extreme(localXPerWorldZ, minWorldZ, maxWorldZ, false) + localXOffset;
    }

    public int lowestLocalZ(final int minWorldX, final int minWorldZ, final int maxWorldX, final int maxWorldZ) {
        return extreme(localZPerWorldX, minWorldX, maxWorldX, true)
                + extreme(localZPerWorldZ, minWorldZ, maxWorldZ, true) + localZOffset;
    }

    public int highestLocalZ(final int minWorldX, final int minWorldZ, final int maxWorldX, final int maxWorldZ) {
        return extreme(localZPerWorldX, minWorldX, maxWorldX, false)
                + extreme(localZPerWorldZ, minWorldZ, maxWorldZ, false) + localZOffset;
    }

    /** Each term is monotonic in its own axis, so the extreme sits on an edge. */
    private static int extreme(final int coefficient, final int low, final int high, final boolean lowest) {
        if (coefficient == 0) {
            return 0;
        }
        return coefficient * ((coefficient > 0) == lowest ? low : high);
    }
}
