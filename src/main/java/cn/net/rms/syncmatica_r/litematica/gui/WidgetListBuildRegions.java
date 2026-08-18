package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.util.NaturalOrderComparator;
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
import java.util.List;

public class WidgetListBuildRegions extends WidgetListBase<BuildRegion, WidgetBuildRegionEntry> {

    private static final int HEADER_HEIGHT = 18;

    private final ServerPlacement placement;

    public WidgetListBuildRegions(final int x, final int y, final int width, final int height,
                                  final ServerPlacement placement) {
        super(x, y, width, height, null);
        browserEntryHeight = 20;
        browserEntryWidth = width - 8;
        browserEntriesOffsetY = HEADER_HEIGHT;
        this.placement = placement;
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
     * Column offsets mirror {@link WidgetBuildRegionEntry} so the header stays
     * lined up with the rows underneath it.
     */
    private void drawHeaderRow(final TextDrawer drawer) {
        final int textColor = 0xFFFFFFFF;
        final int textY = posY + 6;
        final int progressColumnRight = posX + browserEntryWidth - 8;
        final int claimerColumnRight = progressColumnRight - WidgetBuildRegionEntry.PROGRESS_COLUMN_WIDTH;

        drawer.drawString(StringUtils.translate("syncmatica_r.gui.label.build.column.region"),
                posX + 6, textY, textColor);
        final String playerLabel = StringUtils.translate("syncmatica_r.gui.label.build.column.player");
        drawer.drawString(playerLabel, claimerColumnRight - getStringWidth(playerLabel), textY, textColor);
        final String progressLabel = StringUtils.translate("syncmatica_r.gui.label.build.column.progress");
        drawer.drawString(progressLabel, progressColumnRight - getStringWidth(progressLabel), textY, textColor);
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }

    @Override
    protected Collection<BuildRegion> getAllEntries() {
        final List<BuildRegion> snapshot = new ArrayList<>(placement.getBuildRegions().getRegions());
        // Finished regions sink to the bottom, everything still to do keeps its
        // place in the numbering. Claiming a region deliberately does not move
        // it: a row that jumps to the end the moment it is taken is a row nobody
        // can find again to drop after a misclick. With completion tracking off
        // nothing is ever complete, so this degrades to plain name order.
        snapshot.sort((left, right) -> {
            if (left.isComplete() != right.isComplete()) {
                return left.isComplete() ? 1 : -1;
            }
            return NaturalOrderComparator.INSTANCE.compare(left.getRegionName(), right.getRegionName());
        });
        return snapshot;
    }

    @Override
    protected WidgetBuildRegionEntry createListEntryWidget(final int x, final int y, final int listIndex,
                                                           final boolean isOdd, final BuildRegion entry) {
        return new WidgetBuildRegionEntry(x, y, browserEntryWidth, browserEntryHeight, entry, listIndex, placement);
    }

    @Override
    protected List<String> getEntryStringsForFilter(final BuildRegion entry) {
        final List<String> filter = new ArrayList<>(1);
        filter.add(entry.getRegionName().toLowerCase());
        return filter;
    }
}
