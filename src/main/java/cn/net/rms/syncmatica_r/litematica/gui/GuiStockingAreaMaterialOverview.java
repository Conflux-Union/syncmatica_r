package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.util.StockingAreaSelectionHelper;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message;
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
        final String defaultAreaLabel =
                StringUtils.translate("syncmatica_r.gui.button.stocking_area.set_default_from_selection");
        final String closeLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int defaultAreaWidth = getStringWidth(defaultAreaLabel) + 20;
        final int closeWidth = getStringWidth(closeLabel) + 20;
        final int spacing = 6;
        final int y = height - 26;
        int x = width - defaultAreaWidth - closeWidth - spacing - 10;

        final ButtonGeneric defaultAreaButton = new ButtonGeneric(x, y, defaultAreaWidth, 20, defaultAreaLabel);
        addButton(defaultAreaButton, (button, mouse) -> applySelectionAsDefaultStockingArea());
        x += defaultAreaWidth + spacing;

        // Consistent close affordance keeps the UX aligned with the existing material view.
        final ButtonGeneric closeButton = new ButtonGeneric(x, y, closeWidth, 20, closeLabel);
        addButton(closeButton, (button, mouse) -> closeGui(true));
    }

    /**
     * The server answers with its own message on success, so only report the
     * cases where the packet never left the client.
     */
    private void applySelectionAsDefaultStockingArea() {
        final StockingAreaSelectionHelper.Result result = StockingAreaSelectionHelper.sendAsDefault();
        if (result != StockingAreaSelectionHelper.Result.SENT) {
            addMessage(Message.MessageType.ERROR, StockingAreaSelectionHelper.getFailureMessageKey(result));
        }
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
