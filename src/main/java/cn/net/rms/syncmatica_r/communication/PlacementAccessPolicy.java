package cn.net.rms.syncmatica_r.communication;

import java.util.UUID;

public final class PlacementAccessPolicy {
    public static final String SHARE_PERMISSION = "syncmatica_r.share";
    public static final String CLAIM_PERMISSION = "syncmatica_r.claim";
    public static final String MANAGE_PERMISSION = "syncmatica_r.manage";
    public static final int MANAGE_PERMISSION_LEVEL = 2;

    private PlacementAccessPolicy() {
    }

    public static boolean canManage(final UUID playerId, final UUID ownerId, final boolean elevated) {
        return elevated || (playerId != null && playerId.equals(ownerId));
    }
}
