package cn.net.rms.syncmatica_r.mixin;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
//#if MC >= 12005
//$$ import cn.net.rms.syncmatica_r.communication.SyncmaticaPayload;
//$$ import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
//#else
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.util.Identifier;
//#endif
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinServerPlayNetworkHandler {

    @Shadow
    public ServerPlayerEntity player;
    @Unique
    private ExchangeTarget exTarget = null;
    @Unique
    private ServerCommunicationManager comManager = null;

    @Inject(method = "<init>", at = @At("TAIL"))
//#if MC >= 12101
//$$     public void onConnect(final MinecraftServer server, final ClientConnection connection, final ServerPlayerEntity player, final net.minecraft.server.network.ConnectedClientData clientData, final CallbackInfo ci) {
//#else
    public void onConnect(final MinecraftServer server, final ClientConnection connection, final ServerPlayerEntity player, final CallbackInfo ci) {
//#endif
        operateComms(sm -> sm.onPlayerJoin(getExchangeTarget(), player));
    }

    public void onDisconnected(final Text reason, final CallbackInfo ci) {
        operateComms(sm -> sm.onPlayerLeave(getExchangeTarget()));
    }

    @Inject(method = "onCustomPayload", at = @At("HEAD"))
    public void onCustomPayload(final CustomPayloadC2SPacket packet, final CallbackInfo ci) {
//#if MC >= 12005
//$$         NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler) (Object) this, player.getServerWorld());
//$$         if (packet.payload() instanceof SyncmaticaPayload syncPayload) {
//$$             operateComms(sm -> sm.onPacket(getExchangeTarget(), syncPayload.id(), syncPayload.data()));
//$$         }
//#else
        NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler) (Object) this, player.getServerWorld());
        final Identifier id = ((MixinCustomPayloadC2SPacket) packet).getChannel();
        final PacketByteBuf packetBuf = ((MixinCustomPayloadC2SPacket) packet).getData();
        operateComms(sm -> sm.onPacket(getExchangeTarget(), id, packetBuf));
//#endif
    }

    private ExchangeTarget getExchangeTarget() {
        if (exTarget == null) {
            exTarget = new ExchangeTarget((ServerPlayNetworkHandler) (Object) this);
        }
        return exTarget;
    }

    private void operateComms(final Consumer<ServerCommunicationManager> operation) {
        if (comManager == null) {
            final Context con = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
            if (con != null) {
                comManager = (ServerCommunicationManager) con.getCommunicationManager();
            }
        }
        if (comManager != null) {
            operation.accept(comManager);
        }
    }
}
