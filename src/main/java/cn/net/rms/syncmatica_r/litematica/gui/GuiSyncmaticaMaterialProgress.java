package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.client.MaterialListPreferences;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.material.MaterialAvailability;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import cn.net.rms.syncmatica_r.util.MaterialClaimHelper;
import cn.net.rms.syncmatica_r.util.StockingAreaSelectionHelper;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSyncmaticaMaterialProgress extends GuiListBase<SyncmaticaMaterialEntry, WidgetMaterialProgressEntry, WidgetListMaterialProgress> {

    private static final int TOP_BAR_HEIGHT = 34;

    private final ServerPlacement placement;
    private boolean availabilityReported;

    public GuiSyncmaticaMaterialProgress(final ServerPlacement placement) {
        super(12, TOP_BAR_HEIGHT);
        this.placement = placement;
        title = StringUtils.translate("syncmatica_r.gui.title.material_progress") + ": " + placement.getName();
        ScreenHelper.ifPresent(helper -> helper.setCurrentGui(this));
    }

    @Override
    public void initGui() {
        super.initGui();

        // Top control bar: sort mode and filter buttons
        final int topBarY = 10;
        int topBarX = 10;

        // Sort mode button
        final String sortLabel = buildSortModeLabel();
        final int sortWidth = getStringWidth(sortLabel) + 20;
        final ButtonGeneric sortButton = new ButtonGeneric(topBarX, topBarY, sortWidth, 20, sortLabel);
        addButton(sortButton, (button, mouseButton) -> {
            MaterialListPreferences.cycleSortMode();
            button.setDisplayString(buildSortModeLabel());
            getListWidget().refreshEntries();
        });
        topBarX += sortWidth + 6;

        // Hide finished toggle button
        final String hideLabel = buildHideFinishedLabel();
        final int hideWidth = getStringWidth(hideLabel) + 20;
        final ButtonGeneric hideButton = new ButtonGeneric(topBarX, topBarY, hideWidth, 20, hideLabel);
        addButton(hideButton, (button, mouseButton) -> {
            MaterialListPreferences.toggleHideFinished();
            button.setDisplayString(buildHideFinishedLabel());
            getListWidget().refreshEntries();
        });

        // Bottom button bar
        final String stockingLabel = StringUtils.translate("syncmatica_r.gui.button.stocking_area.set_from_selection");
        final String unclaimAllLabel = StringUtils.translate("syncmatica_r.gui.button.material.unclaim_all");
        final String exportLabel = StringUtils.translate("syncmatica_r.gui.button.material_export");
        final String closeLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int stockingWidth = getStringWidth(stockingLabel) + 20;
        final int unclaimAllWidth = getStringWidth(unclaimAllLabel) + 20;
        final int exportWidth = getStringWidth(exportLabel) + 20;
        final int closeWidth = getStringWidth(closeLabel) + 20;
        final int spacing = 6;
        final int totalWidth = stockingWidth + unclaimAllWidth + exportWidth + closeWidth + spacing * 3;
        final int y = height - 26;
        int x = width - totalWidth - 10;

        final ButtonGeneric stockingButton = new ButtonGeneric(x, y, stockingWidth, 20, stockingLabel);
        addButton(stockingButton, (button, mouseButton) -> applySelectionAsStockingArea());
        x += stockingWidth + spacing;

        final ButtonGeneric unclaimAllButton = new ButtonGeneric(x, y, unclaimAllWidth, 20, unclaimAllLabel);
        addButton(unclaimAllButton, (button, mouseButton) -> unclaimAllMaterials());
        x += unclaimAllWidth + spacing;

        final ButtonGeneric exportButton = new ButtonGeneric(x, y, exportWidth, 20, exportLabel);
        addButton(exportButton, (button, mouseButton) -> {
            final GuiMaterialExportOptions gui = new GuiMaterialExportOptions(placement);
            gui.setParent(this);
            GuiBase.openGui(gui);
        });
        x += exportWidth + spacing;

        final ButtonGeneric closeButton = new ButtonGeneric(x, y, closeWidth, 20, closeLabel);
        addButton(closeButton, (b, i) -> closeGui(true));

        reportUnavailableMaterials();
    }

    /**
     * An empty list is otherwise indistinguishable from a schematic the server
     * refused to process, so state the reason once per opened screen.
     */
    private void reportUnavailableMaterials() {
        final MaterialAvailability availability = placement.getMaterialAvailability();
        if (availabilityReported || !availability.isBlocked()) {
            return;
        }
        availabilityReported = true;
        addMessage(Message.MessageType.ERROR, availability.getTranslationKey());
    }

    private String buildSortModeLabel() {
        final MaterialListPreferences.SortMode mode = MaterialListPreferences.getSortMode();
        final String modeKey = mode == MaterialListPreferences.SortMode.NAME_ASC
                ? "syncmatica_r.gui.label.material.sort.name"
                : "syncmatica_r.gui.label.material.sort.missing";
        return StringUtils.translate("syncmatica_r.gui.button.material.sort", StringUtils.translate(modeKey));
    }

    private String buildHideFinishedLabel() {
        final String stateKey = MaterialListPreferences.isHideFinished()
                ? "syncmatica_r.gui.label.toggle_on"
                : "syncmatica_r.gui.label.toggle_off";
        return StringUtils.translate("syncmatica_r.gui.button.material.hide_finished", StringUtils.translate(stateKey));
    }

    @Override
    protected WidgetListMaterialProgress createListWidget(final int listX, final int listY) {
        return new WidgetListMaterialProgress(listX, listY, getBrowserWidth(), getBrowserHeight(), placement);
    }

    @Override
    protected int getBrowserHeight() {
        return height - TOP_BAR_HEIGHT - 30;
    }

    @Override
    protected int getBrowserWidth() {
        return width - 20;
    }

    /**
     * The server answers with its own message on success, so only report the
     * cases where the packet never left the client.
     */
    private void applySelectionAsStockingArea() {
        final StockingAreaSelectionHelper.Result result = StockingAreaSelectionHelper.sendForPlacement(placement);
        if (result != StockingAreaSelectionHelper.Result.SENT) {
            addMessage(Message.MessageType.ERROR, StockingAreaSelectionHelper.getFailureMessageKey(result));
        }
    }

    private void unclaimAllMaterials() {
        final int unclaimedCount = MaterialClaimHelper.unclaimAllMaterials(placement);
        if (unclaimedCount > 0) {
            getListWidget().refreshEntries();
        }
    }
}
