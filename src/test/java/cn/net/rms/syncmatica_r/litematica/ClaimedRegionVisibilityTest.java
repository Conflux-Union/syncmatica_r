package cn.net.rms.syncmatica_r.litematica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ClaimedRegionVisibilityTest {

    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    @Test
    void onlyTheRegionsThisPlayerHoldsAreCollected() {
        final BuildRegionState regions = new BuildRegionState();
        regions.getOrCreate("roof", 100L).addClaimer(new PlayerIdentifier(SELF, "Alice"));
        regions.getOrCreate("walls", 100L).addClaimer(new PlayerIdentifier(OTHER, "Bob"));
        regions.getOrCreate("floor", 100L);

        assertEquals(Collections.singleton("roof"), ClaimedRegionVisibility.collectOwnClaims(regions, SELF));
    }

    /**
     * The server allows one claimant per region today, but the storage and the
     * wire format both carry a set, so sharing a region must not hide it.
     */
    @Test
    void aRegionSharedWithSomebodyElseStillCounts() {
        final BuildRegionState regions = new BuildRegionState();
        final BuildRegion shared = regions.getOrCreate("roof", 100L);
        shared.addClaimer(new PlayerIdentifier(OTHER, "Bob"));
        shared.addClaimer(new PlayerIdentifier(SELF, "Alice"));

        assertEquals(Collections.singleton("roof"), ClaimedRegionVisibility.collectOwnClaims(regions, SELF));
    }

    @Test
    void claimingARegionSwitchesThatRegionOnAndNothingElse() {
        final ClaimedRegionVisibility.ClaimChange change = ClaimedRegionVisibility.changeBetween(
                Set.of("roof"), Set.of("roof", "walls"));

        assertEquals(Collections.singleton("walls"), change.toEnable);
        assertTrue(change.toDisable.isEmpty(), "a region already held must not be touched again");
    }

    @Test
    void droppingARegionSwitchesItOff() {
        final ClaimedRegionVisibility.ClaimChange change = ClaimedRegionVisibility.changeBetween(
                Set.of("roof", "walls"), Set.of("roof"));

        assertEquals(Collections.singleton("walls"), change.toDisable);
        assertTrue(change.toEnable.isEmpty());
    }

    @Test
    void anUnchangedClaimSetTouchesNothing() {
        final ClaimedRegionVisibility.ClaimChange change = ClaimedRegionVisibility.changeBetween(
                Set.of("roof", "walls"), Set.of("walls", "roof"));

        assertTrue(change.toEnable.isEmpty());
        assertTrue(change.toDisable.isEmpty());
        // Regions nobody claimed, or somebody else claimed, are absent from both
        // sides and so keep whatever the player set them to.
    }

    @Test
    void theFirstUpdateSwitchesOnWhatIsHeldWithoutSwitchingAnythingOff() {
        final ClaimedRegionVisibility.ClaimChange change = ClaimedRegionVisibility.changeBetween(
                Collections.emptySet(), Set.of("roof"));

        assertEquals(Collections.singleton("roof"), change.toEnable);
        assertTrue(change.toDisable.isEmpty());
    }
}
