package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The expected values in {@link #transformMatchesLitematica()} come from
 * Litematica's own {@code PositionUtils.getTransformedBlockPos}, so they are
 * independent of this implementation. Everything else is checked for internal
 * consistency against those.
 */
final class RegionBoundsResolverTest {

    private static final BlockPos SAMPLE = new BlockPos(3, 5, 7);

    @Test
    void transformMatchesLitematica() {
        assertEquals(SAMPLE, RegionBoundsResolver.transform(SAMPLE, BlockMirror.NONE, BlockRotation.NONE));
        assertEquals(new BlockPos(-7, 5, 3),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.NONE, BlockRotation.CLOCKWISE_90));
        assertEquals(new BlockPos(-3, 5, -7),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.NONE, BlockRotation.CLOCKWISE_180));
        assertEquals(new BlockPos(7, 5, -3),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.NONE, BlockRotation.COUNTERCLOCKWISE_90));

        // LEFT_RIGHT negates z, FRONT_BACK negates x, both before the rotation.
        assertEquals(new BlockPos(3, 5, -7),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.LEFT_RIGHT, BlockRotation.NONE));
        assertEquals(new BlockPos(-3, 5, 7),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.FRONT_BACK, BlockRotation.NONE));
        assertEquals(new BlockPos(7, 5, 3),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.LEFT_RIGHT, BlockRotation.CLOCKWISE_90));
        assertEquals(new BlockPos(-7, 5, -3),
                RegionBoundsResolver.transform(SAMPLE, BlockMirror.FRONT_BACK, BlockRotation.CLOCKWISE_90));
    }

    @Test
    void reverseTransformUndoesTransform() {
        for (final BlockRotation rotation : BlockRotation.values()) {
            for (final BlockMirror mirror : BlockMirror.values()) {
                for (final BlockPos pos : new BlockPos[]{
                        BlockPos.ORIGIN, SAMPLE, new BlockPos(-4, 0, 9), new BlockPos(11, -6, -2)}) {
                    final BlockPos there = RegionBoundsResolver.transform(pos, mirror, rotation);
                    final BlockPos back = RegionBoundsResolver.reverseTransform(there, mirror, rotation);
                    assertEquals(pos, back, "round trip failed for " + rotation + "/" + mirror + " at " + pos);
                }
            }
        }
    }

    @Test
    void relativeEndShrinksTowardsZero() {
        assertEquals(new BlockPos(3, 2, 1), RegionBoundsResolver.relativeEndFromSize(new BlockPos(4, 3, 2)));
        assertEquals(new BlockPos(-3, -2, -1), RegionBoundsResolver.relativeEndFromSize(new BlockPos(-4, -3, -2)));
        assertEquals(BlockPos.ORIGIN, RegionBoundsResolver.relativeEndFromSize(new BlockPos(1, 1, 1)));
        assertEquals(BlockPos.ORIGIN, RegionBoundsResolver.relativeEndFromSize(new BlockPos(-1, -1, -1)));
    }

    @Test
    void unrotatedRegionSitsAtTheOrigin() {
        final RegionBounds bounds = resolve(new BlockPos(0, 0, 0), new BlockPos(4, 3, 2),
                BlockRotation.NONE, BlockMirror.NONE, null);

        assertEquals(new BlockPos(100, 64, 200), bounds.getMin());
        assertEquals(new BlockPos(103, 66, 201), bounds.getMax());
    }

    @Test
    void quarterTurnSwapsTheHorizontalExtents() {
        final RegionBounds bounds = resolve(new BlockPos(0, 0, 0), new BlockPos(4, 3, 2),
                BlockRotation.CLOCKWISE_90, BlockMirror.NONE, null);

        // A 4x3x2 region turned a quarter turn covers 2 along x and 4 along z.
        assertEquals(new BlockPos(99, 64, 200), bounds.getMin());
        assertEquals(new BlockPos(100, 66, 203), bounds.getMax());
        assertEquals(2L, bounds.getMax().getX() - bounds.getMin().getX() + 1L);
        assertEquals(4L, bounds.getMax().getZ() - bounds.getMin().getZ() + 1L);
    }

    @Test
    void offsetRegionsRotateAroundThePlacementOrigin() {
        final RegionBounds bounds = resolve(new BlockPos(5, 0, 7), new BlockPos(4, 3, 2),
                BlockRotation.CLOCKWISE_90, BlockMirror.NONE, null);

        assertEquals(new BlockPos(92, 64, 205), bounds.getMin());
        assertEquals(new BlockPos(93, 66, 208), bounds.getMax());
    }

    @Test
    void negativeSizesExtendBackwardsFromTheRegionOrigin() {
        final RegionBounds bounds = resolve(new BlockPos(0, 0, 0), new BlockPos(-4, 3, 2),
                BlockRotation.NONE, BlockMirror.NONE, null);

        assertEquals(new BlockPos(97, 64, 200), bounds.getMin());
        assertEquals(new BlockPos(100, 66, 201), bounds.getMax());
        assertEquals(24L, bounds.getVolume());
    }

    @Test
    void aSubRegionOverrideReplacesPositionAndPose() {
        final SubRegionPlacementModification override = new SubRegionPlacementModification(
                "roof", new BlockPos(10, 0, 0), BlockRotation.CLOCKWISE_180, BlockMirror.NONE);
        final RegionBounds bounds = resolve(new BlockPos(0, 0, 0), new BlockPos(4, 3, 2),
                BlockRotation.NONE, BlockMirror.NONE, override);

        // pos1 follows the override position (110, 64, 200); the sub-region
        // rotation only turns the region's own extent, so the box grows back
        // towards -x/-z from there.
        assertEquals(new BlockPos(107, 64, 199), bounds.getMin());
        assertEquals(new BlockPos(110, 66, 200), bounds.getMax());
        assertEquals(24L, bounds.getVolume());
    }

    /**
     * The strongest check available without Litematica: whatever the pose, walking
     * every world position inside the computed box must hit every local coordinate
     * of the region exactly once. A box that disagrees with the per-block mapping
     * fails here.
     */
    @Test
    void everyPoseMapsTheBoxOntoTheRegionExactlyOnce() {
        final BlockPos[] sizes = {
                new BlockPos(4, 3, 2),
                new BlockPos(1, 1, 1),
                new BlockPos(-4, 3, 2),
                new BlockPos(4, 3, -2),
                new BlockPos(-3, -2, -5),
        };
        final BlockPos[] regionPositions = {BlockPos.ORIGIN, new BlockPos(5, 2, 7), new BlockPos(-6, -1, 3)};

        for (final BlockPos size : sizes) {
            final long expected = Math.abs((long) size.getX()) * Math.abs((long) size.getY())
                    * Math.abs((long) size.getZ());
            for (final BlockPos regionPos : regionPositions) {
                for (final BlockRotation rotation : BlockRotation.values()) {
                    for (final BlockMirror mirror : BlockMirror.values()) {
                        assertBijection(regionPos, size, rotation, mirror, expected);
                    }
                }
            }
        }
    }

    @Test
    void positionsOutsideTheRegionHaveNoLocalCoordinate() {
        final RegionGeometry geometry = new RegionGeometry(BlockPos.ORIGIN, new BlockPos(4, 3, 2));
        final BlockPos origin = new BlockPos(100, 64, 200);

        assertNotNull(RegionBoundsResolver.toLocalPosition(new BlockPos(100, 64, 200), geometry, origin,
                BlockRotation.NONE, BlockMirror.NONE, null));
        assertNull(RegionBoundsResolver.toLocalPosition(new BlockPos(104, 64, 200), geometry, origin,
                BlockRotation.NONE, BlockMirror.NONE, null));
        assertNull(RegionBoundsResolver.toLocalPosition(new BlockPos(99, 64, 200), geometry, origin,
                BlockRotation.NONE, BlockMirror.NONE, null));
    }

    @Test
    void missingInputsResolveToNothing() {
        final RegionGeometry geometry = new RegionGeometry(BlockPos.ORIGIN, new BlockPos(4, 3, 2));
        assertNull(RegionBoundsResolver.resolve(null, BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE, null));
        assertNull(RegionBoundsResolver.resolve(geometry, null, BlockRotation.NONE, BlockMirror.NONE, null));
        assertNull(RegionBoundsResolver.toLocalPosition(null, geometry, BlockPos.ORIGIN,
                BlockRotation.NONE, BlockMirror.NONE, null));
    }

    private void assertBijection(final BlockPos regionPos, final BlockPos size, final BlockRotation rotation,
                                 final BlockMirror mirror, final long expected) {
        final RegionGeometry geometry = new RegionGeometry(regionPos, size);
        final BlockPos origin = new BlockPos(100, 64, 200);
        final RegionBounds bounds = RegionBoundsResolver.resolve(geometry, origin, rotation, mirror, null);
        assertNotNull(bounds);

        final String label = size + " " + regionPos + " " + rotation + "/" + mirror;
        assertEquals(expected, bounds.getVolume(), "box volume must match the region volume for " + label);

        final Set<BlockPos> seen = new HashSet<>();
        for (int x = bounds.getMin().getX(); x <= bounds.getMax().getX(); x++) {
            for (int y = bounds.getMin().getY(); y <= bounds.getMax().getY(); y++) {
                for (int z = bounds.getMin().getZ(); z <= bounds.getMax().getZ(); z++) {
                    final BlockPos local = RegionBoundsResolver.toLocalPosition(
                            new BlockPos(x, y, z), geometry, origin, rotation, mirror, null);
                    assertNotNull(local, "position inside the box has no local coordinate for " + label);
                    assertTrue(seen.add(local), "local coordinate " + local + " hit twice for " + label);
                }
            }
        }
        assertEquals(expected, seen.size(), "the box must cover the whole region for " + label);
    }

    private RegionBounds resolve(final BlockPos regionPos, final BlockPos size, final BlockRotation rotation,
                                 final BlockMirror mirror, final SubRegionPlacementModification override) {
        final RegionBounds bounds = RegionBoundsResolver.resolve(
                new RegionGeometry(regionPos, size), new BlockPos(100, 64, 200), rotation, mirror, override);
        assertNotNull(bounds);
        return bounds;
    }
}
