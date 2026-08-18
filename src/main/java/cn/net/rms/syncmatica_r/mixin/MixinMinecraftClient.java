package cn.net.rms.syncmatica_r.mixin;

import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.client.hud.MaterialHudOverlay;
import cn.net.rms.syncmatica_r.litematica.ClaimedRegionVisibility;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.mixin_actor.ActorClientPlayNetworkHandler;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    //#if MC >= 12106
    //$$ @Inject(method = "onDisconnected", at = @At("HEAD"))
    //#else
    @Inject(method = "disconnect()V", at = @At("HEAD"))
    //#endif
    private void shutdownSyncmatica(final CallbackInfo ci) {
        ScreenHelper.close();
        MaterialHudOverlay.getInstance().reset();
        ClaimedRegionVisibility.getInstance().reset();
        Syncmatica.shutdown();
        LitematicManager.clear();
        ActorClientPlayNetworkHandler.getInstance().reset();
    }
}
