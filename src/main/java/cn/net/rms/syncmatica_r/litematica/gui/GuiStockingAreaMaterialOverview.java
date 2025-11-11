package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiStockingAreaMaterialOverview extends GuiListBase<
        WidgetStockingAreaMaterialEntry.RowData,
        WidgetStockingAreaMaterialEntry,
        WidgetListStockingAreaMaterial> {

    private final ServerPlacement focusPlacement;

    public GuiStockingAreaMaterialOverview(final ServerPlacement focusPlacement) {
        super(12, 24);
        this.focusPlacement = focusPlacement;
        title = StringUtils.translate("syncmatica_r.gui.title.stocking_area_overview");
        ScreenHelper.ifPresent(helper -> helper.setCurrentGui(this));
    }

    @Override
    public void initGui() {
        super.initGui();
        final String closeLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int buttonWidth = getStringWidth(closeLabel) + 20;
        // Consistent close affordance keeps the UX aligned with the existing material view.
        final ButtonGeneric closeButton = new ButtonGeneric(
                width - buttonWidth - 10, height - 26, buttonWidth, 20, closeLabel);
        addButton(closeButton, (button, mouse) -> closeGui(true));
    }

    @Override
    protected WidgetListStockingAreaMaterial createListWidget(final int listX, final int listY) {
        return new WidgetListStockingAreaMaterial(listX, listY, getBrowserWidth(), getBrowserHeight(), focusPlacement);
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
