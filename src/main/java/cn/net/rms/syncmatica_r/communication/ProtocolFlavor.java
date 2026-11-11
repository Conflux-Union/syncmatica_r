package cn.net.rms.syncmatica_r.communication;

public enum ProtocolFlavor {
    NEW,
    LEGACY;

    public static final String NEW_NAMESPACE = "syncmatica_r";
    public static final String LEGACY_NAMESPACE = "syncmatica";

    /**
     * Unknown namespaces resolve to NEW: upstream drops unknown transports
     * silently, but a legacy peer crashes on packets it cannot type.
     */
    public static ProtocolFlavor fromNamespace(final String namespace) {
        return LEGACY_NAMESPACE.equals(namespace) ? LEGACY : NEW;
    }
}
