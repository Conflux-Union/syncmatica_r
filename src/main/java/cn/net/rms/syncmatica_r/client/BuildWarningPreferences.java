package cn.net.rms.syncmatica_r.client;

/**
 * Whether this player wants to be told about building inside a sub-region
 * somebody else claimed.
 *
 * <p>The check runs entirely on the client, so the choice belongs to the player
 * rather than to the server operator.
 */
public final class BuildWarningPreferences {

    private BuildWarningPreferences() {
    }

    public static void load() {
        ClientConfigs.INSTANCE.load();
    }

    public static void save() {
        ClientConfigs.INSTANCE.save();
    }

    public static boolean isEnabled() {
        return ClientConfigs.General.WARN_ON_FOREIGN_PLACEMENT.getBooleanValue();
    }

    public static void setEnabled(final boolean value) {
        ClientConfigs.General.WARN_ON_FOREIGN_PLACEMENT.setBooleanValue(value);
    }
}
