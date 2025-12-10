package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import cn.net.rms.syncmatica_r.util.MaterialClaimHelper;
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
        final String unclaimAllLabel = StringUtils.translate("syncmatica_r.gui.button.material.unclaim_all");
        final int closeWidth = getStringWidth(closeLabel) + 20;
        final int exportWidth = getStringWidth(exportLabel) + 20;
        final int unclaimAllWidth = getStringWidth(unclaimAllLabel) + 20;
        final int spacing = 6;
        final int totalWidth = closeWidth + exportWidth + unclaimAllWidth + spacing * 2;
        final int baseX = width - totalWidth - 10;
        final int y = height - 26;

        final ButtonGeneric unclaimAllButton = new ButtonGeneric(baseX, y, unclaimAllWidth, 20, unclaimAllLabel);
        addButton(unclaimAllButton, (button, mouseButton) -> unclaimAllMaterials());

        final ButtonGeneric exportButton = new ButtonGeneric(baseX + unclaimAllWidth + spacing, y, exportWidth, 20, exportLabel);
        addButton(exportButton, (button, mouseButton) -> {
            final GuiMaterialExportOptions gui = new GuiMaterialExportOptions(placement);
            gui.setParent(this);
            GuiBase.openGui(gui);
        });

        final ButtonGeneric closeButton = new ButtonGeneric(baseX + unclaimAllWidth + exportWidth + spacing * 2, y, closeWidth, 20, closeLabel);
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

    private void unclaimAllMaterials() {
        final int unclaimedCount = MaterialClaimHelper.unclaimAllMaterials(placement);
        if (unclaimedCount > 0) {
            getListWidget().refreshEntries();
        }
    }
}
