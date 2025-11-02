package ch.endte.syncmatica.communication;

import net.minecraft.util.Identifier;

public enum PacketType {

    REGISTER_METADATA("syncmatica:register_metadata"),
    
    
    

    CANCEL_SHARE("syncmatica:cancel_share"),
    // send to a client when a share failed
    // the client can cancel the upload or upon finishing send a removal packet

    REQUEST_LITEMATIC("syncmatica:request_download"),
    
    // litematic starting with a download request

    SEND_LITEMATIC("syncmatica:send_litematic"),
    

    RECEIVED_LITEMATIC("syncmatica:received_litematic"),
    
    // by waiting until a response is sent I hope we can ensure
    // that we do not overwhelm the clients' connection to the server

    FINISHED_LITEMATIC("syncmatica:finished_litematic"),
    
    // transmission

    CANCEL_LITEMATIC("syncmatica:cancel_litematic"),
    
    // will be sent in several cases - upon errors mostly

    REMOVE_SYNCMATIC("syncmatica:remove_syncmatic"),
    // a packet that will be sent to clients when a syncmatic got removed
    

    REGISTER_VERSION("syncmatica:register_version"),
    // this packet will be sent to the client when it joins the server
    // upon receiving this packet the client will check the server version
    // initializes syncmatica on the clients end
    
    

    CONFIRM_USER("syncmatica:confirm_user"),
    // the confirm-user packet
    // send after a successful version exchange
    // fully starts up syncmatica on the clients end
    // sends all server placements along to the client

    FEATURE_REQUEST("syncmatica:feature_request"),
    // requests the partner to send a list of its features
    // does not require a fully finished handshake

    FEATURE("syncmatica:feature"),
    // sends feature set to the partner
    
    
    // afterwards the feature set is used to communicate to the partner

    MODIFY("syncmatica:modify"),
    // sends updated placement data to the client or vice versa

    MODIFY_REQUEST("syncmatica:modify_request"),
    // send from client to server to request the editing of placement values
    // used to ensure that only one person can edit at a time thus preventing all kinds of stuff

    MODIFY_REQUEST_DENY("syncmatica:modify_request_deny"),
    MODIFY_REQUEST_ACCEPT("syncmatica:modify_request_accept"),

    MODIFY_FINISH("syncmatica:modify_finish"),
    // send from client to server to mark that the editing of placement values has concluded
    // sends along the final data of the placement

    MESSAGE("syncmatica:mesage");
    
    // can't fix the typo here lol

    public final Identifier identifier;

    PacketType(final String id) {
        identifier = new Identifier(id);
    }

    public static boolean containsIdentifier(final Identifier id) {
        for (final PacketType p : PacketType.values()) {
            if (id.equals(p.identifier)) { // this took I kid you not 4-5 hours to find
                return true;
            }
        }
        return false;
    }
}
