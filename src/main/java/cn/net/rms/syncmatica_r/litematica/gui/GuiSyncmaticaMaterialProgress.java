package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSyncmaticaMaterialProgress extends GuiListBase<SyncmaticaMaterialEntry, WidgetMaterialProgressEntry, WidgetListMaterialProgress> {

    private final ServerPlacement placement;

    public GuiSyncmaticaMaterialProgress(final ServerPlacement placement) {
        super(12, 20);
        this.placement = placement;
        title = StringUtils.translate("syncmatica_r.gui.title.material_progress") + ": " + placement.getName();
        ScreenHelper.ifPresent(helper -> helper.setCurrentGui(this));
    }

    @Override
    public void initGui() {
        super.initGui();
        final String closeLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final String exportLabel = StringUtils.translate("syncmatica_r.gui.button.material_export");
        final int closeWidth = getStringWidth(closeLabel) + 20;
        final int exportWidth = getStringWidth(exportLabel) + 20;
        final int spacing = 6;
        final int totalWidth = closeWidth + exportWidth + spacing;
        final int baseX = width - totalWidth - 10;
        final int y = height - 26;

        final ButtonGeneric exportButton = new ButtonGeneric(baseX, y, exportWidth, 20, exportLabel);
        addButton(exportButton, (button, mouseButton) -> {
            final GuiMaterialExportOptions gui = new GuiMaterialExportOptions(placement);
            gui.setParent(this);
            GuiBase.openGui(gui);
        });

        final ButtonGeneric closeButton = new ButtonGeneric(baseX + exportWidth + spacing, y, closeWidth, 20, closeLabel);
        addButton(closeButton, (b, i) -> closeGui(true));
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
