package cn.net.rms.syncmatica_r.litematica_mixin;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.ModifyExchangeClient;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import ch.endte.syncmatica.litematica_mixin.MixinGuiBase;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiPlacementConfiguration.class)
public abstract class MixinGuiPlacementConfiguration extends GuiBase {

    @Final
    @Shadow(remap = false)
    public SchematicPlacement placement;

    @Inject(method = "initGui", at = @At("RETURN"), remap = false)
    public void initGui(final CallbackInfo ci) {
        if (!LitematicManager.getInstance().isSyncmatic(placement)) {
            return;
        }
        final List<ButtonBase> buttons = ((MixinGuiBase) (Object) this).getButtons();
        final ButtonBase button = buttons.get(6);
        button.setActionListener((b, k) -> {
            if (placement.isLocked()) {
                requestModification();
            } else {
                finishModification();
            }
        });
        ScreenHelper.ifPresent(s -> s.setCurrentGui(this));
    }

    private void requestModification() {
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ExchangeTarget server = ((ClientCommunicationManager) context.getCommunicationManager()).getServer();
        final ServerPlacement serverPlacement = LitematicManager.getInstance().syncmaticFromSchematic(placement);
        if (!server.getFeatureSet().hasFeature(Feature.CORE_EX) && placement.isRegionPlacementModified()) {
            addMessage(Message.MessageType.ERROR, "syncmatica_r.error.share_modified_subregions");
            return;
        }
        final ModifyExchangeClient modifyExchange = new ModifyExchangeClient(serverPlacement, server, context);
        context.getCommunicationManager().startExchange(modifyExchange);
    }

    private void finishModification() {
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ExchangeTarget server = ((ClientCommunicationManager) context.getCommunicationManager()).getServer();
        if (!server.getFeatureSet().hasFeature(Feature.CORE_EX) && placement.isRegionPlacementModified()) {
            addMessage(Message.MessageType.ERROR, "syncmatica_r.error.share_modified_subregions");
            return;
        }
        final ServerPlacement serverPlacement = LitematicManager.getInstance().syncmaticFromSchematic(placement);
        final ModifyExchangeClient modifyExchange = (ModifyExchangeClient) context.getCommunicationManager().getModifier(serverPlacement);
        if (modifyExchange != null) {
            modifyExchange.conclude();
        }
    }
}
