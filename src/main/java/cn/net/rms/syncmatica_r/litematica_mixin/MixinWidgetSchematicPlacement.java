package cn.net.rms.syncmatica_r.litematica_mixin;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.gui.ButtonListenerShare;
import cn.net.rms.syncmatica_r.litematica_mixin.MixinButtonBase;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicPlacements;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WidgetSchematicPlacement.class)
public abstract class MixinWidgetSchematicPlacement extends WidgetListEntryBase<SchematicPlacement> {
    @Shadow(remap = false)
    public int buttonsStartX;
    @Final
    @Shadow(remap = false)
    public SchematicPlacement placement;

    protected MixinWidgetSchematicPlacement(final int x, final int y, final int width, final int height, final SchematicPlacement entry, final int listIndex) {
        super(x, y, width, height, entry, listIndex);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    public void addUploadButton(final int x, final int y, final int width, final int height, final boolean isOdd,
                                final SchematicPlacement placement, final int listIndex, final WidgetListSchematicPlacements parent, final CallbackInfo ci) {
        if (LitematicManager.getInstance().isSyncmatic(placement)) {
            wrapRemoveButtonOfSyncmatic(placement);
        }

        final ButtonGeneric shareButton = new ButtonGeneric(buttonsStartX, y + 1, -1, true, "syncmatica_r.gui.button.share");
        final Context con = LitematicManager.getInstance().getActiveContext();
        final boolean buttonEnabled = con != null && con.isStarted() && !LitematicManager.getInstance().isSyncmatic(placement);
        shareButton.setEnabled(buttonEnabled);
        addButton(shareButton, new ButtonListenerShare(placement, parent.parent));
        buttonsStartX = shareButton.getX() - 1;
    }

    /**
     * A shared placement must not be removed outright, so shift-clicking Litematica's own Remove
     * button only drops it from the local render instead.
     * <p>
     * The button is matched by its listener type rather than by its index in {@code subWidgets}:
     * other Litematica addons inject into this same constructor and any button they add would
     * otherwise shift the position we look at.
     */
    private void wrapRemoveButtonOfSyncmatic(final SchematicPlacement placement) {
        for (final WidgetBase base : subWidgets) {
            if (!(base instanceof ButtonBase button)) {
                continue;
            }

            final IButtonActionListener listener = ((MixinButtonBase) button).getActionListener();
            if (!(listener instanceof WidgetSchematicPlacement.ButtonListener)
                    || ((MixinWidgetSchematicPlacementButtonListener) (Object) listener).getButtonType()
                    != WidgetSchematicPlacement.ButtonListener.ButtonType.REMOVE) {
                continue;
            }

            button.setActionListener((b, k) -> {
                if (GuiBase.isShiftDown()) {
                    LitematicManager.getInstance().unrenderSchematicPlacement(placement);
                    return;
                }
                listener.actionPerformedWithButton(b, k);
            });
            return;
        }
    }

    public SchematicPlacement getPlacement() {
        return placement;
    }

}
