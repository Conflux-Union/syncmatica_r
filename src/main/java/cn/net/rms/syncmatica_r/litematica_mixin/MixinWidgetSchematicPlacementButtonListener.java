package cn.net.rms.syncmatica_r.litematica_mixin;

import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes which of Litematica's own placement row buttons a listener belongs to, so we can find a
 * specific one without relying on its position in the widget list.
 */
@Mixin(WidgetSchematicPlacement.ButtonListener.class)
public interface MixinWidgetSchematicPlacementButtonListener {
    @Accessor(value = "type", remap = false)
    WidgetSchematicPlacement.ButtonListener.ButtonType getButtonType();
}
