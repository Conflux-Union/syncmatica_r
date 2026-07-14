package cn.net.rms.syncmatica_r;

public enum Feature {
    CORE,

    FEATURE,
    MODIFY,
    MESSAGE,
    QUOTA,
    DEBUG,
    MATERIAL_PROGRESS,
    MATERIAL_CLAIMS,
    CORE_EX,
    TIMESTAMPS,
    VERSION,
    DISPLAY_NAME;

    public static Feature fromString(final String s) {
        for (final Feature f : Feature.values()) {
            if (f.toString().equals(s)) {
                return f;
            }
        }
        return null;
    }
}
