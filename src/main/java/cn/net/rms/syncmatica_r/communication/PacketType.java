package cn.net.rms.syncmatica_r.communication;

import net.minecraft.util.Identifier;

public enum PacketType {

    REGISTER_METADATA("syncmatica_r:register_metadata"),

    CANCEL_SHARE("syncmatica_r:cancel_share"),

    REQUEST_LITEMATIC("syncmatica_r:request_download"),

    SEND_LITEMATIC("syncmatica_r:send_litematic"),

    RECEIVED_LITEMATIC("syncmatica_r:received_litematic"),

    FINISHED_LITEMATIC("syncmatica_r:finished_litematic"),

    CANCEL_LITEMATIC("syncmatica_r:cancel_litematic"),

    REMOVE_SYNCMATIC("syncmatica_r:remove_syncmatic"),

    REGISTER_VERSION("syncmatica_r:register_version"),

    CONFIRM_USER("syncmatica_r:confirm_user"),

    FEATURE_REQUEST("syncmatica_r:feature_request"),

    FEATURE("syncmatica_r:feature"),

    MODIFY("syncmatica_r:modify"),

    MODIFY_REQUEST("syncmatica_r:modify_request"),

    MODIFY_REQUEST_DENY("syncmatica_r:modify_request_deny"),
    MODIFY_REQUEST_ACCEPT("syncmatica_r:modify_request_accept"),

    MODIFY_FINISH("syncmatica_r:modify_finish"),

    MESSAGE("syncmatica_r:mesage"),

    MATERIAL_CLAIM_TOGGLE("syncmatica_r:material_claim_toggle");

    public final Identifier identifier;

    PacketType(final String id) {
        identifier = new Identifier(id);
    }

    public static boolean containsIdentifier(final Identifier id) {
        for (final PacketType p : PacketType.values()) {
            if (id.equals(p.identifier)) {
                return true;
            }
        }
        return false;
    }
}
