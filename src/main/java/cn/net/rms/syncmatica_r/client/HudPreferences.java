package cn.net.rms.syncmatica_r.client;

public final class HudPreferences {

    private static final double MIN_SCALE = 0.6d;
    private static final double MAX_SCALE = 1.4d;

    private HudPreferences() {
    }

    public static void load() {
        ClientConfigs.INSTANCE.load();
    }

    public static void save() {
        ClientConfigs.INSTANCE.save();
    }

    public static void setHudScale(final double scale) {
        ClientConfigs.General.HUD_SCALE.setDoubleValue(clampScale(scale));
    }

    public static double clampScale(final double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static double getHudScale() {
        return ClientConfigs.General.HUD_SCALE.getDoubleValue();
    }

    public static boolean isHudEnabled() {
        return ClientConfigs.General.HUD_ENABLED.getBooleanValue();
    }

    public static void setHudEnabled(final boolean enabled) {
        ClientConfigs.General.HUD_ENABLED.setBooleanValue(enabled);
    }

    public static double getMinScale() {
        return MIN_SCALE;
    }

    public static double getMaxScale() {
        return MAX_SCALE;
    }

    public static double getRelativeScale() {
        return (getHudScale() - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
    }

    public static void setRelativeScale(final double relative) {
        final double clamped = Math.max(0d, Math.min(1d, relative));
        setHudScale(MIN_SCALE + clamped * (MAX_SCALE - MIN_SCALE));
    }

}
