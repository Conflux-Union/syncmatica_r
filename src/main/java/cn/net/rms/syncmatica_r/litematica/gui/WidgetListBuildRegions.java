package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
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
        final int claimerColumnRight = posX + browserEntryWidth - 8;

        drawer.drawString(StringUtils.translate("syncmatica_r.gui.label.build.column.region"),
                posX + 6, textY, textColor);
        final String playerLabel = StringUtils.translate("syncmatica_r.gui.label.build.column.player");
        drawer.drawString(playerLabel, claimerColumnRight - getStringWidth(playerLabel), textY, textColor);
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }

    @Override
    protected Collection<BuildRegion> getAllEntries() {
        final List<BuildRegion> snapshot = new ArrayList<>(placement.getBuildRegions().getRegions());
        // Unclaimed regions first so the next thing to pick up is at the top,
        // then by name for a stable order.
        snapshot.sort((left, right) -> {
            if (left.isClaimed() != right.isClaimed()) {
                return left.isClaimed() ? 1 : -1;
            }
            return left.getRegionName().compareToIgnoreCase(right.getRegionName());
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
