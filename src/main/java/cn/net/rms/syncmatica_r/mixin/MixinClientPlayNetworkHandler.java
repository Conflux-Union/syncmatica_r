package cn.net.rms.syncmatica_r.mixin;

import cn.net.rms.syncmatica_r.mixin_actor.ActorClientPlayNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
//#if MC >= 12101
//$$ import net.minecraft.network.packet.CustomPayload;
//#elseif MC >= 12005
//$$ import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
//#else
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler {
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void handlePacket(
            //#if MC >= 12101
            //$$ final CustomPayload packet,
            //#else
            final CustomPayloadS2CPacket packet,
            //#endif
            final CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            return;
        }
        ActorClientPlayNetworkHandler.getInstance().packetEvent((ClientPlayNetworkHandler) (Object) this, packet, ci);
    }
}
