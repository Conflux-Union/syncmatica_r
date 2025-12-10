package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import cn.net.rms.syncmatica_r.util.MaterialClaimHelper;
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
        final String unclaimAllLabel = StringUtils.translate("syncmatica_r.gui.button.material.unclaim_all");
        final int closeWidth = getStringWidth(closeLabel) + 20;
        final int unclaimAllWidth = getStringWidth(unclaimAllLabel) + 20;
        final int spacing = 6;
        final int totalWidth = closeWidth + unclaimAllWidth + spacing;
        final int baseX = width - totalWidth - 10;
        final int y = height - 26;

        final ButtonGeneric unclaimAllButton = new ButtonGeneric(baseX, y, unclaimAllWidth, 20, unclaimAllLabel);
        addButton(unclaimAllButton, (button, mouseButton) -> unclaimAllMaterials());

        final ButtonGeneric closeButton = new ButtonGeneric(baseX + unclaimAllWidth + spacing, y, closeWidth, 20, closeLabel);
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

    private void unclaimAllMaterials() {
        if (summary == null || summary.getPlacements().isEmpty()) {
            return;
        }
        final int unclaimedCount = MaterialClaimHelper.unclaimAllMaterials(summary.getPlacements());
        if (unclaimedCount > 0) {
            getListWidget().refreshEntries();
        }
    }
}
