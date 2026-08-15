package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WidgetListBuildPlacements extends WidgetListBase<ServerPlacement, WidgetBuildPlacementEntry> {

    private static final int HEADER_HEIGHT = 18;

    private final GuiBuildManagement parent;

    public WidgetListBuildPlacements(final int x, final int y, final int width, final int height,
                                     final GuiBuildManagement parent) {
        super(x, y, width, height, null);
        browserEntryHeight = 20;
        browserEntryWidth = width - 8;
        browserEntriesOffsetY = HEADER_HEIGHT;
        this.parent = parent;
    }

    @Override
    public void setSize(final int width, final int height) {
        super.setSize(width, height);
        browserEntryWidth = width - 8;
    }

//#if MC >= 12111
//$$     @Override
//$$     public void drawContents(final GuiContext guiContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(guiContext, posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         drawHeaderRow((text, tx, ty, color) -> drawString(guiContext, text, tx, ty, color));
//$$         super.drawContents(guiContext, mouseX, mouseY, partialTicks);
//$$     }
//#elseif MC >= 12106
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(drawContext, posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         drawHeaderRow((text, tx, ty, color) -> drawString(drawContext, text, tx, ty, color));
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$     }
//#elseif MC >= 12001
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         drawHeaderRow((text, tx, ty, color) -> drawString(drawContext, text, tx, ty, color));
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$     }
//#else
    @Override
    public void drawContents(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
        drawHeaderRow((text, tx, ty, color) -> drawString(matrixStack, text, tx, ty, color));
        super.drawContents(matrixStack, mouseX, mouseY, partialTicks);
    }
//#endif

    /**
     * Column offsets mirror {@link WidgetBuildPlacementEntry} so the header stays
     * lined up with the rows underneath it.
     */
    private void drawHeaderRow(final TextDrawer drawer) {
        final int textColor = 0xFFFFFFFF;
        final int textY = posY + 6;
        final int progressColumnRight = posX + browserEntryWidth - 8;
        final int claimedColumnRight = progressColumnRight - WidgetBuildPlacementEntry.PROGRESS_COLUMN_WIDTH;
        final int regionColumnRight = claimedColumnRight - WidgetBuildPlacementEntry.CLAIMED_COLUMN_WIDTH;

        drawer.drawString(StringUtils.translate("syncmatica_r.gui.label.build.column.schematic"),
                posX + 6, textY, textColor);
        final String regionLabel = StringUtils.translate("syncmatica_r.gui.label.build.column.regions");
        drawer.drawString(regionLabel, regionColumnRight - getStringWidth(regionLabel), textY, textColor);
        final String claimedLabel = StringUtils.translate("syncmatica_r.gui.label.build.column.claimed");
        drawer.drawString(claimedLabel, claimedColumnRight - getStringWidth(claimedLabel), textY, textColor);
        final String progressLabel = StringUtils.translate("syncmatica_r.gui.label.build.column.progress");
        drawer.drawString(progressLabel, progressColumnRight - getStringWidth(progressLabel), textY, textColor);
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }

    @Override
    protected Collection<ServerPlacement> getAllEntries() {
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || context.getSyncmaticManager() == null) {
            return Collections.emptyList();
        }
        final List<ServerPlacement> snapshot = new ArrayList<>(context.getSyncmaticManager().getAll());
        snapshot.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return snapshot;
    }

    @Override
    protected WidgetBuildPlacementEntry createListEntryWidget(final int x, final int y, final int listIndex,
                                                              final boolean isOdd, final ServerPlacement entry) {
        return new WidgetBuildPlacementEntry(x, y, browserEntryWidth, browserEntryHeight, entry, listIndex, parent);
    }

    @Override
    protected List<String> getEntryStringsForFilter(final ServerPlacement entry) {
        final List<String> filter = new ArrayList<>(1);
        filter.add(entry.getName().toLowerCase());
        return filter;
    }
}
