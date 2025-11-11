package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiStockingAreaMaterialTotals extends GuiListBase<
        SyncmaticaMaterialEntry,
        WidgetMaterialProgressEntry,
        WidgetListStockingAreaMaterialTotals> {

    private final WidgetStockingAreaMaterialEntry.StockingAreaSummary summary;

    public GuiStockingAreaMaterialTotals(final WidgetStockingAreaMaterialEntry.StockingAreaSummary summary) {
        super(12, 20);
        this.summary = summary;
        final String areaName = summary == null ? "" : summary.getDisplayName();
        title = StringUtils.translate("syncmatica_r.gui.title.stocking_area_material_totals", areaName);
        ScreenHelper.ifPresent(helper -> helper.setCurrentGui(this));
    }

    @Override
    public void initGui() {
        super.initGui();
        final String closeLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int buttonWidth = getStringWidth(closeLabel) + 20;
        final ButtonGeneric closeButton = new ButtonGeneric(
                width - buttonWidth - 10, height - 26, buttonWidth, 20, closeLabel);
        addButton(closeButton, (button, mouse) -> closeGui(true));
    }

    @Override
    protected WidgetListStockingAreaMaterialTotals createListWidget(final int listX, final int listY) {
        return new WidgetListStockingAreaMaterialTotals(listX, listY, getBrowserWidth(), getBrowserHeight(), summary);
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
