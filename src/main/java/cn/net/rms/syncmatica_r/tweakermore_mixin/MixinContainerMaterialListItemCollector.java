package cn.net.rms.syncmatica_r.tweakermore_mixin;

import cn.net.rms.syncmatica_r.compat.tweakermore.SyncmaticaMaterialListAdapter;
import cn.net.rms.syncmatica_r.compat.tweakermore.TweakerMoreSourceConfig;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.fallenbreath.tweakermore.impl.features.autoContainerProcess.processors.ContainerMaterialListItemCollector", remap = false)
public abstract class MixinContainerMaterialListItemCollector {
    @Redirect(
            method = "process",
            at = @At(
                    value = "INVOKE",
                    target = "Lfi/dy/masa/litematica/data/DataManager;getMaterialList()Lfi/dy/masa/litematica/materials/MaterialListBase;",
                    remap = true),
            remap = false)
    private MaterialListBase useSelectedMaterialSource() {
        if (!TweakerMoreSourceConfig.usesSyncmatica()) {
            return DataManager.getMaterialList();
        }

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getGameProfile() == null) {
            return SyncmaticaMaterialListAdapter.create(null);
        }
        return SyncmaticaMaterialListAdapter.create(
                SyncmaticaUtil.getProfileId(client.player.getGameProfile()));
    }
}
