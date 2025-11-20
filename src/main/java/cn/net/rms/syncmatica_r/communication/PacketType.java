package cn.net.rms.syncmatica_r.communication;

import net.minecraft.util.Identifier;

public enum PacketType {

    REGISTER_METADATA("register_metadata"),

    CANCEL_SHARE("cancel_share"),

    REQUEST_LITEMATIC("request_download"),

    SEND_LITEMATIC("send_litematic"),

    RECEIVED_LITEMATIC("received_litematic"),

    FINISHED_LITEMATIC("finished_litematic"),

    CANCEL_LITEMATIC("cancel_litematic"),

    REMOVE_SYNCMATIC("remove_syncmatic"),

    REGISTER_VERSION("register_version"),

    // Reforged-only capability announce from client to server
    REVOLUTION("revolution"),

    CONFIRM_USER("confirm_user"),

    FEATURE_REQUEST("feature_request"),

    FEATURE("feature"),

    MODIFY("modify"),

    MODIFY_REQUEST("modify_request"),

    MODIFY_REQUEST_DENY("modify_request_deny"),
    MODIFY_REQUEST_ACCEPT("modify_request_accept"),

    MODIFY_FINISH("modify_finish"),

    MESSAGE("mesage"),

    MATERIAL_CLAIM_TOGGLE("material_claim_toggle");

    private static final String NEW_NAMESPACE = "syncmatica_r";
    private static final String LEGACY_NAMESPACE = "syncmatica";

    private final String path;

    PacketType(final String path) {
        this.path = path;
    }

    public Identifier toIdentifier(final ProtocolFlavor flavor) {
        final String namespace = flavor == ProtocolFlavor.LEGACY
                ? LEGACY_NAMESPACE
                : NEW_NAMESPACE;
//#if MC >= 12005
//$$         return Identifier.of(namespace, path);
//#else
        return new Identifier(namespace, path);
//#endif
    }

    public Identifier toIdentifier() {
        return toIdentifier(ProtocolFlavor.NEW);
    }

    public static boolean containsIdentifier(final Identifier id) {
        return fromIdentifier(id) != null;
    }

    public static PacketType fromIdentifier(final Identifier id) {
        final String namespace = id.getNamespace();
        if (!NEW_NAMESPACE.equals(namespace) && !LEGACY_NAMESPACE.equals(namespace)) {
            return null;
        }
        final String path = id.getPath();
        for (final PacketType type : PacketType.values()) {
            if (type.path.equals(path)) {
                return type;
            }
        }
        return null;
    }
}
