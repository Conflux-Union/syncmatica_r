package cn.net.rms.syncmatica_r.mixin_actor;

import cn.net.rms.syncmatica_r.IFileStorage;
import cn.net.rms.syncmatica_r.RedirectFileStorage;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.client.hud.MaterialHudOverlay;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
//$$ import cn.net.rms.syncmatica_r.communication.SyncmaticaPayload;
//$$ import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
//#else
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
//#endif
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class ActorClientPlayNetworkHandler {

    private static ActorClientPlayNetworkHandler instance;
    private static ClientPlayNetworkHandler clientPlayNetworkHandler;
    private CommunicationManager clientCommunication;
    private ExchangeTarget exTarget;

    public static ActorClientPlayNetworkHandler getInstance() {
        if (instance == null) {

            instance = new ActorClientPlayNetworkHandler();
        }

        return instance;
    }

    private static void setClientPlayNetworkHandler(final ClientPlayNetworkHandler clientPlayNetworkHandler) {
        ActorClientPlayNetworkHandler.clientPlayNetworkHandler = clientPlayNetworkHandler;
    }

    public void startEvent(final ClientPlayNetworkHandler clientPlayNetworkHandler) {
        setClientPlayNetworkHandler(clientPlayNetworkHandler);
        startClient();
    }

    public void startClient() {
        if (clientPlayNetworkHandler == null) {
            throw new RuntimeException("Tried to start client before receiving a connection");
        }
        final IFileStorage data = new RedirectFileStorage();
        final SyncmaticManager man = new SyncmaticManager();
        exTarget = new ExchangeTarget(clientPlayNetworkHandler);
        final CommunicationManager comms = new ClientCommunicationManager(exTarget);
        Syncmatica.initClient(comms, data, man);
        clientCommunication = comms;
        ScreenHelper.init();
        LitematicManager.getInstance().setActiveContext(Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT));
        MaterialHudOverlay.getInstance().bindToClientContext(Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT));
        MaterialHudOverlay.getInstance().scheduleRefresh();
    }

    public void packetEvent(final ClientPlayNetworkHandler clientPlayNetworkHandler, final CustomPayloadS2CPacket packet, final CallbackInfo ci) {
//#if MC >= 12005
//$$         if (!(packet.payload() instanceof SyncmaticaPayload payload)) {
//$$             return;
//$$         }
//$$         final Identifier id = payload.id();
//$$         final PacketByteBuf buf = payload.data();
//#else
        final Identifier id = packet.getChannel();
        final PacketByteBuf buf = packet.getData();
//#endif
        if (clientCommunication == null) {

            ActorClientPlayNetworkHandler.getInstance().startEvent(clientPlayNetworkHandler);
        }
        if (packetEvent(id, buf)) {

            ci.cancel();
        }
    }

    public boolean packetEvent(final Identifier id, final PacketByteBuf buf) {
        if (clientCommunication.handlePacket(id)) {
            clientCommunication.onPacket(exTarget, id, buf);

            return true;
        }

        return false;
    }

    public void reset() {
        clientCommunication = null;
        exTarget = null;
        clientPlayNetworkHandler = null;
    }
}
