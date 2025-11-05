package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.litematica.ScreenHelper;
import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSyncmaticaMaterialProgress extends GuiListBase<SyncmaticaMaterialEntry, WidgetMaterialProgressEntry, WidgetListMaterialProgress> {

    private final ServerPlacement placement;

    public GuiSyncmaticaMaterialProgress(final ServerPlacement placement) {
        super(12, 20);
        this.placement = placement;
        title = StringUtils.translate("syncmatica.gui.title.material_progress") + ": " + placement.getName();
        ScreenHelper.ifPresent(helper -> helper.setCurrentGui(this));
    }

    @Override
    public void initGui() {
        super.initGui();
        final String closeLabel = StringUtils.translate("syncmatica.gui.button.back");
        final int buttonWidth = getStringWidth(closeLabel) + 20;

        final ButtonGeneric button = new ButtonGeneric(width - buttonWidth - 10, height - 26, buttonWidth, 20, closeLabel);
        addButton(button, (b, i) -> closeGui(true));
    }

    @Override
    protected WidgetListMaterialProgress createListWidget(final int listX, final int listY) {
        return new WidgetListMaterialProgress(listX, listY, getBrowserWidth(), getBrowserHeight(), placement);
    }

    @Override
    protected int getBrowserHeight() {
        return height - 50;
    }

    @Override
    protected int getBrowserWidth() {
        return width - 20;
    }
}
