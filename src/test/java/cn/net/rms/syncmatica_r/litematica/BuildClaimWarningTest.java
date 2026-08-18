package cn.net.rms.syncmatica_r.litematica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class BuildClaimWarningTest {

    @Test
    void aRegionCoversEveryBlockBetweenItsCornersInclusive() {
        final BlockPos first = new BlockPos(10, 64, -20);
        final BlockPos second = new BlockPos(13, 66, -19);

        assertTrue(BuildClaimWarning.containsBetween(first, second, first), "the near corner is inside");
        assertTrue(BuildClaimWarning.containsBetween(first, second, second), "the far corner is inside");
        assertTrue(BuildClaimWarning.containsBetween(first, second, new BlockPos(11, 65, -20)));

        assertFalse(BuildClaimWarning.containsBetween(first, second, new BlockPos(14, 65, -20)), "past x");
        assertFalse(BuildClaimWarning.containsBetween(first, second, new BlockPos(11, 67, -20)), "above y");
        assertFalse(BuildClaimWarning.containsBetween(first, second, new BlockPos(11, 65, -21)), "past z");
    }

    /** Litematica reports the corners in definition order, not sorted. */
    @Test
    void cornerOrderDoesNotChangeWhatIsInside() {
        final BlockPos low = new BlockPos(10, 64, -20);
        final BlockPos high = new BlockPos(13, 66, -19);
        final BlockPos inside = new BlockPos(12, 65, -19);

        assertTrue(BuildClaimWarning.containsBetween(low, high, inside));
        assertTrue(BuildClaimWarning.containsBetween(high, low, inside));
        assertTrue(BuildClaimWarning.containsBetween(
                new BlockPos(13, 64, -19), new BlockPos(10, 66, -20), inside),
                "corners mixed per axis still describe the same box");
    }

    @Test
    void aSingleBlockRegionStillHasOneBlockInIt() {
        final BlockPos only = new BlockPos(0, 0, 0);

        assertTrue(BuildClaimWarning.containsBetween(only, only, only));
        assertFalse(BuildClaimWarning.containsBetween(only, only, new BlockPos(0, 1, 0)));
        assertFalse(BuildClaimWarning.containsBetween(null, only, only), "an unresolved corner covers nothing");
    }
}
