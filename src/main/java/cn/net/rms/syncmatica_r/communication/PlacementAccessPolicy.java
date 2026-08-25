package cn.net.rms.syncmatica_r.communication;

import java.util.UUID;

public final class PlacementAccessPolicy {
    public static final String SHARE_PERMISSION = "syncmatica_r.share";
    public static final String CLAIM_PERMISSION = "syncmatica_r.claim";
    /**
     * Separate from {@link #CLAIM_PERMISSION} on purpose: signing up to gather a
     * material and signing up to build part of the schematic are different jobs,
     * and a server may well want to hand them out to different people.
     */
    public static final String BUILD_CLAIM_PERMISSION = "syncmatica_r.build.claim";
    public static final String MANAGE_PERMISSION = "syncmatica_r.manage";
    public static final int MANAGE_PERMISSION_LEVEL = 2;

    private PlacementAccessPolicy() {
    }

    public static boolean canManage(final UUID playerId, final UUID ownerId, final boolean elevated) {
        return elevated || (playerId != null && playerId.equals(ownerId));
    }

    public static boolean canManageStockingArea(final UUID playerId,
                                                 final UUID ownerId,
                                                 final boolean elevated,
                                                 final boolean ownerManagementEnabled) {
        return elevated || (ownerManagementEnabled && playerId != null && playerId.equals(ownerId));
    }
}
