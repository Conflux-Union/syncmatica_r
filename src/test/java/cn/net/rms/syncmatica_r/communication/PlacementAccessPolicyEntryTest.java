package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlacementAccessPolicyEntryTest {
    @Test
    void areaOwnershipDecidesEditRights() {
        final UUID creator = UUID.randomUUID();
        assertTrue(PlacementAccessPolicy.canManageStockingAreaEntry(creator, creator, false));
        assertFalse(PlacementAccessPolicy.canManageStockingAreaEntry(UUID.randomUUID(), creator, false));
        assertTrue(PlacementAccessPolicy.canManageStockingAreaEntry(UUID.randomUUID(), creator, true));
        // Server-owned areas (null owner) are elevated-only.
        assertFalse(PlacementAccessPolicy.canManageStockingAreaEntry(creator, null, false));
        assertTrue(PlacementAccessPolicy.canManageStockingAreaEntry(creator, null, true));
    }
}
