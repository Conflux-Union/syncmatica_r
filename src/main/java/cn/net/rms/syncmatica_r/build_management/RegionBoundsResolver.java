package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/**
 * Places a sub-region in the world without Litematica present.
 *
 * <p>The server has the litematic file and the placement pose but not
 * Litematica's placement objects, so the transform is reproduced here. It
 * mirrors {@code PositionUtils.getTransformedBlockPos} and the box construction
 * in {@code SchematicPlacement.getSubRegionBoxes}:
 *
 * <pre>
 * pos1   = transform(regionPos, placementMirror, placementRotation) + origin
 * relEnd = relativeEnd(size)
 * relEnd = transform(relEnd, placementMirror, placementRotation)
 * relEnd = transform(relEnd, subRegionMirror, subRegionRotation)
 * pos2   = relEnd + pos1
 * </pre>
 *
 * <p>Mirror is applied before rotation going forward, and rotation before mirror
 * going back, so the two directions are exact inverses of each other.
 */
public final class RegionBoundsResolver {

    private RegionBoundsResolver() {
    }

    /**
     * @param modification the player's override for this sub-region, or null when
     *                     the region still sits where the schematic put it
     */
    public static RegionBounds resolve(final RegionGeometry geometry,
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
        if (regionPos == null) {
            return null;
        }

        final BlockPos pos1 = add(transform(regionPos, mirror, rotation), origin);
        BlockPos relativeEnd = relativeEndFromSize(geometry.getSize());
        relativeEnd = transform(relativeEnd, mirror, rotation);
        relativeEnd = transform(relativeEnd, subMirror, subRotation);
        return new RegionBounds(pos1, add(relativeEnd, pos1));
    }

    /**
     * Maps a world position back to the schematic-local coordinate inside the
     * region, so a scan can walk world positions and ask what belongs there.
     *
     * <p>Solving the map costs more than applying it. A caller with more than a
     * handful of positions should hold a {@link RegionLocalMapper} instead of
     * calling this in a loop.
     *
     * @return local coordinates, or null when the position falls outside the region
     */
    public static BlockPos toLocalPosition(final BlockPos worldPos,
                                           final RegionGeometry geometry,
                                           final BlockPos origin,
                                           final BlockRotation placementRotation,
                                           final BlockMirror placementMirror,
                                           final SubRegionPlacementModification modification) {
        if (worldPos == null) {
            return null;
        }
        final RegionLocalMapper mapper =
                RegionLocalMapper.of(geometry, origin, placementRotation, placementMirror, modification);
        if (mapper == null) {
            return null;
        }
        final int x = mapper.localX(worldPos.getX(), worldPos.getZ());
        final int y = mapper.localY(worldPos.getY());
        final int z = mapper.localZ(worldPos.getX(), worldPos.getZ());
        return mapper.containsLocal(x, y, z) ? new BlockPos(x, y, z) : null;
    }

    /** Mirrors {@code PositionUtils.getRelativeEndPositionFromAreaSize}. */
    public static BlockPos relativeEndFromSize(final BlockPos size) {
        return new BlockPos(shrinkTowardsZero(size.getX()), shrinkTowardsZero(size.getY()),
                shrinkTowardsZero(size.getZ()));
    }

    private static int shrinkTowardsZero(final int value) {
        return value >= 0 ? value - 1 : value + 1;
    }

    /** Mirrors {@code PositionUtils.getTransformedBlockPos}: mirror first, then rotate. */
    public static BlockPos transform(final BlockPos pos, final BlockMirror mirror, final BlockRotation rotation) {
        int x = pos.getX();
        final int y = pos.getY();
        int z = pos.getZ();
        boolean mirrored = true;

        switch (mirror) {
            case LEFT_RIGHT:
                z = -z;
                break;
            case FRONT_BACK:
                x = -x;
                break;
            default:
                mirrored = false;
                break;
        }

        switch (rotation) {
            case CLOCKWISE_90:
                return new BlockPos(-z, y, x);
            case COUNTERCLOCKWISE_90:
                return new BlockPos(z, y, -x);
            case CLOCKWISE_180:
                return new BlockPos(-x, y, -z);
            default:
                return mirrored ? new BlockPos(x, y, z) : pos;
        }
    }

    /** Mirrors {@code PositionUtils.getReverseTransformedBlockPos}: rotate first, then mirror. */
    public static BlockPos reverseTransform(final BlockPos pos, final BlockMirror mirror,
                                            final BlockRotation rotation) {
        int x = pos.getX();
        final int y = pos.getY();
        int z = pos.getZ();
        final int originalX = x;
        boolean rotated = true;

        switch (rotation) {
            case CLOCKWISE_90:
                x = z;
                z = -originalX;
                break;
            case COUNTERCLOCKWISE_90:
                x = -z;
                z = originalX;
                break;
            case CLOCKWISE_180:
                x = -x;
                z = -z;
                break;
            default:
                rotated = false;
                break;
        }

        switch (mirror) {
            case LEFT_RIGHT:
                z = -z;
                break;
            case FRONT_BACK:
                x = -x;
                break;
            default:
                if (!rotated) {
                    return pos;
                }
                break;
        }
        return new BlockPos(x, y, z);
    }

    private static BlockPos add(final BlockPos left, final BlockPos right) {
        return new BlockPos(left.getX() + right.getX(), left.getY() + right.getY(), left.getZ() + right.getZ());
    }

}
