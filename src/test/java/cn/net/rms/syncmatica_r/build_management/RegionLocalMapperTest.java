package cn.net.rms.syncmatica_r.build_management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.extended_core.SubRegionPlacementModification;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class RegionLocalMapperTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 64, 200);
    private static final BlockPos REGION_POS = new BlockPos(-3, 5, 7);

    /**
     * The mapper folds two reverse transforms and a per-axis flip into three
     * affine rows. This walks every pose the game can produce and checks the
     * folded answer against one worked out the long way, position by position.
     */
    @Test
    void theFoldedMapAgreesWithTheStepByStepOne() {
        for (final BlockPos size : new BlockPos[]{
                new BlockPos(4, 3, 5), new BlockPos(-4, 3, 5), new BlockPos(4, -3, 5),
                new BlockPos(4, 3, -5), new BlockPos(-4, -3, -5), new BlockPos(1, 1, 1)}) {
            for (final BlockRotation rotation : BlockRotation.values()) {
                for (final BlockMirror mirror : BlockMirror.values()) {
                    assertAgrees(size, rotation, mirror, null);
                    for (final BlockRotation subRotation : BlockRotation.values()) {
                        for (final BlockMirror subMirror : BlockMirror.values()) {
                            assertAgrees(size, rotation, mirror, new SubRegionPlacementModification(
                                    "sub", new BlockPos(2, -1, 4), subRotation, subMirror));
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyPositionInTheBoxMapsToADistinctCellOfTheRegion() {
        final BlockPos size = new BlockPos(4, 3, -5);
        final RegionGeometry geometry = new RegionGeometry(REGION_POS, size);
        final RegionBounds bounds =
                RegionBoundsResolver.resolve(geometry, ORIGIN, BlockRotation.CLOCKWISE_90, BlockMirror.LEFT_RIGHT, null);
        final RegionLocalMapper mapper =
                RegionLocalMapper.of(geometry, ORIGIN, BlockRotation.CLOCKWISE_90, BlockMirror.LEFT_RIGHT, null);
        assertNotNull(bounds);
        assertNotNull(mapper);

        final boolean[] seen = new boolean[4 * 3 * 5];
        for (int x = bounds.getMin().getX(); x <= bounds.getMax().getX(); x++) {
            for (int y = bounds.getMin().getY(); y <= bounds.getMax().getY(); y++) {
                for (int z = bounds.getMin().getZ(); z <= bounds.getMax().getZ(); z++) {
                    final int localX = mapper.localX(x, z);
                    final int localY = mapper.localY(y);
                    final int localZ = mapper.localZ(x, z);
                    assertTrue(mapper.containsLocal(localX, localY, localZ),
                            "position inside the box fell outside the region");
                    final int cell = (localX * 3 + localY) * 5 + localZ;
                    assertFalse(seen[cell], "two world positions mapped onto the same cell");
                    seen[cell] = true;
                }
            }
        }
    }

    @Test
    void theYMapIsItsOwnInverse() {
        for (final BlockPos size : new BlockPos[]{new BlockPos(4, 3, 5), new BlockPos(4, -3, 5)}) {
            final RegionLocalMapper mapper = RegionLocalMapper.of(
                    new RegionGeometry(REGION_POS, size), ORIGIN, BlockRotation.CLOCKWISE_180, BlockMirror.FRONT_BACK,
                    null);
            assertNotNull(mapper);
            for (int worldY = 50; worldY < 90; worldY++) {
                assertEquals(worldY, mapper.worldY(mapper.localY(worldY)));
            }
        }
    }

    /**
     * The column index is read for a whole chunk column at a time, so the range
     * has to cover every cell the column touches and no more.
     */
    @Test
    void theLocalRangeOfAWorldBoxCoversExactlyThatBox() {
        for (final BlockRotation rotation : BlockRotation.values()) {
            for (final BlockMirror mirror : BlockMirror.values()) {
                final RegionLocalMapper mapper = RegionLocalMapper.of(
                        new RegionGeometry(REGION_POS, new BlockPos(40, 3, -50)), ORIGIN, rotation, mirror, null);
                assertNotNull(mapper);
                final int minX = 96;
                final int minZ = 208;
                final int maxX = 111;
                final int maxZ = 223;

                final int lowX = mapper.lowestLocalX(minX, minZ, maxX, maxZ);
                final int highX = mapper.highestLocalX(minX, minZ, maxX, maxZ);
                final int lowZ = mapper.lowestLocalZ(minX, minZ, maxX, maxZ);
                final int highZ = mapper.highestLocalZ(minX, minZ, maxX, maxZ);
                assertTrue(lowX <= highX && lowZ <= highZ, "range came back inverted for " + rotation + "/" + mirror);

                int seenLowX = Integer.MAX_VALUE;
                int seenHighX = Integer.MIN_VALUE;
                int seenLowZ = Integer.MAX_VALUE;
                int seenHighZ = Integer.MIN_VALUE;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        seenLowX = Math.min(seenLowX, mapper.localX(x, z));
                        seenHighX = Math.max(seenHighX, mapper.localX(x, z));
                        seenLowZ = Math.min(seenLowZ, mapper.localZ(x, z));
                        seenHighZ = Math.max(seenHighZ, mapper.localZ(x, z));
                    }
                }
                final String label = " for " + rotation + "/" + mirror;
                assertEquals(seenLowX, lowX, "lowest local X" + label);
                assertEquals(seenHighX, highX, "highest local X" + label);
                assertEquals(seenLowZ, lowZ, "lowest local Z" + label);
                assertEquals(seenHighZ, highZ, "highest local Z" + label);
            }
        }
    }

    @Test
    void aRegionWithoutAPoseHasNoMap() {
        assertNull(RegionLocalMapper.of(null, ORIGIN, BlockRotation.NONE, BlockMirror.NONE, null));
        assertNull(RegionLocalMapper.of(new RegionGeometry(REGION_POS, new BlockPos(1, 1, 1)), null,
                BlockRotation.NONE, BlockMirror.NONE, null));
        assertNull(RegionLocalMapper.of(new RegionGeometry(null, new BlockPos(1, 1, 1)), ORIGIN,
                BlockRotation.NONE, BlockMirror.NONE, null));
    }

    private static void assertAgrees(final BlockPos size, final BlockRotation rotation, final BlockMirror mirror,
                                     final SubRegionPlacementModification modification) {
        final RegionGeometry geometry = new RegionGeometry(REGION_POS, size);
        final RegionLocalMapper mapper = RegionLocalMapper.of(geometry, ORIGIN, rotation, mirror, modification);
        assertNotNull(mapper);
        final String label = size + " " + rotation + "/" + mirror
                + (modification == null ? "" : " sub " + modification.rotation + "/" + modification.mirror);

        for (int x = 90; x <= 112; x += 3) {
            for (int y = 58; y <= 72; y += 2) {
                for (int z = 190; z <= 212; z += 3) {
                    final BlockPos expected = stepByStep(new BlockPos(x, y, z), geometry, rotation, mirror,
                            modification);
                    final int localX = mapper.localX(x, z);
                    final int localY = mapper.localY(y);
                    final int localZ = mapper.localZ(x, z);
                    if (expected == null) {
                        assertFalse(mapper.containsLocal(localX, localY, localZ),
                                "position outside the region was accepted for " + label);
                        continue;
                    }
                    assertTrue(mapper.containsLocal(localX, localY, localZ),
                            "position inside the region was rejected for " + label);
                    assertEquals(expected, new BlockPos(localX, localY, localZ), "wrong local position for " + label);
                }
            }
        }
    }

    /**
     * The map worked out one operation at a time, exactly as Litematica's
     * {@code PositionUtils} does it. Kept apart from the production path on
     * purpose: an oracle that shares the code it checks proves nothing.
     */
    private static BlockPos stepByStep(final BlockPos worldPos, final RegionGeometry geometry,
                                       final BlockRotation rotation, final BlockMirror mirror,
                                       final SubRegionPlacementModification modification) {
        final BlockPos regionPos = modification == null ? geometry.getPosition() : modification.position;
        final BlockRotation subRotation = modification == null ? BlockRotation.NONE : modification.rotation;
        final BlockMirror subMirror = modification == null ? BlockMirror.NONE : modification.mirror;

        final BlockPos placed = RegionBoundsResolver.transform(regionPos, mirror, rotation);
        final BlockPos corner = new BlockPos(
                placed.getX() + ORIGIN.getX(), placed.getY() + ORIGIN.getY(), placed.getZ() + ORIGIN.getZ());
        final BlockPos relative = new BlockPos(
                worldPos.getX() - corner.getX(), worldPos.getY() - corner.getY(), worldPos.getZ() - corner.getZ());
        BlockPos local = RegionBoundsResolver.reverseTransform(relative, subMirror, subRotation);
        local = RegionBoundsResolver.reverseTransform(local, mirror, rotation);

        final BlockPos size = geometry.getSize();
        final int x = size.getX() >= 0 ? local.getX() : -local.getX();
        final int y = size.getY() >= 0 ? local.getY() : -local.getY();
        final int z = size.getZ() >= 0 ? local.getZ() : -local.getZ();
        if (x < 0 || y < 0 || z < 0
                || x >= Math.abs(size.getX()) || y >= Math.abs(size.getY()) || z >= Math.abs(size.getZ())) {
            return null;
        }
        return new BlockPos(x, y, z);
    }
}
