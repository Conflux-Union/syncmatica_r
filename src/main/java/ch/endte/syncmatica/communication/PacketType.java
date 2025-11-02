package ch.endte.syncmatica.communication;

import net.minecraft.util.Identifier;

public enum PacketType {

    REGISTER_METADATA("syncmatica:register_metadata"),

    CANCEL_SHARE("syncmatica:cancel_share"),

    REQUEST_LITEMATIC("syncmatica:request_download"),

    SEND_LITEMATIC("syncmatica:send_litematic"),

    RECEIVED_LITEMATIC("syncmatica:received_litematic"),

    FINISHED_LITEMATIC("syncmatica:finished_litematic"),

    CANCEL_LITEMATIC("syncmatica:cancel_litematic"),

    REMOVE_SYNCMATIC("syncmatica:remove_syncmatic"),

    REGISTER_VERSION("syncmatica:register_version"),

    CONFIRM_USER("syncmatica:confirm_user"),

    FEATURE_REQUEST("syncmatica:feature_request"),

    FEATURE("syncmatica:feature"),

    MODIFY("syncmatica:modify"),

    MODIFY_REQUEST("syncmatica:modify_request"),

    MODIFY_REQUEST_DENY("syncmatica:modify_request_deny"),
    MODIFY_REQUEST_ACCEPT("syncmatica:modify_request_accept"),

    MODIFY_FINISH("syncmatica:modify_finish"),

    MESSAGE("syncmatica:mesage"),

    MATERIAL_CLAIM_TOGGLE("syncmatica:material_claim_toggle");

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
