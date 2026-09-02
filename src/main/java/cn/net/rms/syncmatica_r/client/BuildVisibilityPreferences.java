package cn.net.rms.syncmatica_r.client;

/**
 * Whether this player wants Litematica's sub-region visibility to follow the
 * regions they claimed.
 *
 * <p>Off by default: turning it on lets the mod write to a Litematica setting
 * the player also edits by hand, and that is a trade only the player can decide
 * to make. Like the foreign-build warning, the choice is the player's rather
 * than the server operator's, so it lives on the client.
 */
public final class BuildVisibilityPreferences {

    private BuildVisibilityPreferences() {
    }

    public static void load() {
        ClientConfigs.INSTANCE.load();
    }

    public static void save() {
        ClientConfigs.INSTANCE.save();
    }

    public static boolean isFollowClaimsEnabled() {
        return ClientConfigs.General.FOLLOW_CLAIMS.getBooleanValue();
    }

    public static void setFollowClaimsEnabled(final boolean value) {
        ClientConfigs.General.FOLLOW_CLAIMS.setBooleanValue(value);
    }
}
