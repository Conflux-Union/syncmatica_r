package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlacementAccessPolicyTest {
    @Test
    void exposesDocumentedPermissionNodesAndOperatorFallback() {
        assertEquals("syncmatica_r.share", PlacementAccessPolicy.SHARE_PERMISSION);
        assertEquals("syncmatica_r.claim", PlacementAccessPolicy.CLAIM_PERMISSION);
        assertEquals("syncmatica_r.manage", PlacementAccessPolicy.MANAGE_PERMISSION);
        assertEquals(2, PlacementAccessPolicy.MANAGE_PERMISSION_LEVEL);
    }

    @Test
    void ownerCanManagePlacement() {
        final UUID owner = UUID.randomUUID();

        assertTrue(PlacementAccessPolicy.canManage(owner, owner, false));
    }

    @Test
    void elevatedUserCanManageAnotherPlayersPlacement() {
        assertTrue(PlacementAccessPolicy.canManage(UUID.randomUUID(), UUID.randomUUID(), true));
    }

    @Test
    void unrelatedPlayerCannotManagePlacement() {
        assertFalse(PlacementAccessPolicy.canManage(UUID.randomUUID(), UUID.randomUUID(), false));
        assertFalse(PlacementAccessPolicy.canManage(null, UUID.randomUUID(), false));
    }

    @Test
    void ownerCanManageStockingAreaWhenEnabled() {
        final UUID owner = UUID.randomUUID();

        assertTrue(PlacementAccessPolicy.canManageStockingArea(owner, owner, false, true));
    }

    @Test
    void ownerCannotManageStockingAreaWhenDisabled() {
        final UUID owner = UUID.randomUUID();

        assertFalse(PlacementAccessPolicy.canManageStockingArea(owner, owner, false, false));
    }

    @Test
    void unrelatedPlayerCannotManageStockingArea() {
        assertFalse(PlacementAccessPolicy.canManageStockingArea(
                UUID.randomUUID(), UUID.randomUUID(), false, true));
    }

    @Test
    void elevatedUserCanManageStockingAreaWhenOwnerManagementIsDisabled() {
        assertTrue(PlacementAccessPolicy.canManageStockingArea(
                UUID.randomUUID(), null, true, false));
    }
}
