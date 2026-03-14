package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.client.MaterialListPreferences;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
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
//#if MC >= 12110
//$$ import net.minecraft.client.gui.Click;
//#endif

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WidgetListMaterialProgress extends WidgetListBase<SyncmaticaMaterialEntry, WidgetMaterialProgressEntry> {

    private static final int TOTALS_SECTION_HEIGHT = 18;
    private static final int TOTALS_PADDING_X = 6;

    private final ServerPlacement placement;
    // Preserve the overall widget height so the totals bar can sit outside the scroll region.
    private int widgetHeight;


    public WidgetListMaterialProgress(final int x, final int y, final int width, final int height, final ServerPlacement placement) {
        super(x, y, width, height, null);
        browserEntryHeight = 20;
        this.placement = placement;
        configureBrowserBounds(width, height);
    }

    @Override
    public void setSize(final int width, final int height) {
        super.setSize(width, height);
        configureBrowserBounds(width, height);
    }

    private void configureBrowserBounds(final int width, final int height) {
        widgetHeight = height;
        browserEntryWidth = width - 8;
        browserWidth = width;
        browserEntriesOffsetY = 18;
        browserHeight = Math.max(browserEntriesOffsetY + 1, height - TOTALS_SECTION_HEIGHT);
    }
//#if MC >= 12111
//$$     @Override
//$$     public void drawContents(final GuiContext guiContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(guiContext, posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$         // Header mirrors the per-entry columns for readability.
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$         drawString(guiContext, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$         final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
//$$         drawString(guiContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$         final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
//$$         drawString(guiContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$         final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
//$$         drawString(guiContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$         super.drawContents(guiContext, mouseX, mouseY, partialTicks);
//$$         drawTotalsSection(guiContext);
//$$     }
//#elseif MC >= 12106
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(drawContext, posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$         // Header mirrors the per-entry columns for readability.
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$         drawString(drawContext, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$         final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
//$$         drawString(drawContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$         final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
//$$         drawString(drawContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$         final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
//$$         drawString(drawContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$         drawTotalsSection(drawContext);
//$$     }
//#elseif MC >= 12001
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$         // Header mirrors the per-entry columns for readability.
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$         drawString(drawContext, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$         final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
//$$         drawString(drawContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$         final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
//$$         drawString(drawContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$         final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
//$$         drawString(drawContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$         drawTotalsSection(drawContext);
//$$     }
//#else
    @Override
    public void drawContents(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
        final int baseX = posX + 6;
        final int textColor = 0xFFFFFFFF;
        // Header mirrors the per-entry columns for readability.
        final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
        final int missingColumnRight = posX + browserEntryWidth - 8;
        final int stockColumnRight = missingColumnRight - 100;
        drawString(matrixStack, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
                baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
        final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
        drawString(matrixStack, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
        final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
        drawString(matrixStack, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
        final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
        drawString(matrixStack, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
        super.drawContents(matrixStack, mouseX, mouseY, partialTicks);
        drawTotalsSection(matrixStack);
    }
//#endif

    @Override
    protected Collection<SyncmaticaMaterialEntry> getAllEntries() {
        final List<SyncmaticaMaterialEntry> snapshot = new ArrayList<>(placement.getMaterialList().getEntries());
        // Apply filter: hide finished materials if enabled
        if (MaterialListPreferences.isHideFinished()) {
            snapshot.removeIf(SyncmaticaMaterialEntry.UNFINISHED.negate());
        }
        // Apply sort mode
        final MaterialListPreferences.SortMode sortMode = MaterialListPreferences.getSortMode();
        snapshot.sort((left, right) -> {
            if (sortMode == MaterialListPreferences.SortMode.NAME_ASC) {
                // Sort by name ascending
                final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
                final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
                return leftKey.compareTo(rightKey);
            } else {
                // Sort by missing count descending, then by name for ties
                final int lm = left.getAmountMissing();
                final int rm = right.getAmountMissing();
                if (lm != rm) {
                    return Integer.compare(rm, lm);
                }
                final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
                final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
                return leftKey.compareTo(rightKey);
            }
        });
        return snapshot;
    }

    @Override
    protected WidgetMaterialProgressEntry createListEntryWidget(final int x, final int y, final int listIndex, final boolean isOdd, final SyncmaticaMaterialEntry entry) {
        return new WidgetMaterialProgressEntry(x, y, browserEntryWidth, browserEntryHeight, entry, listIndex, placement);
    }

    @Override
    protected List<String> getEntryStringsForFilter(final SyncmaticaMaterialEntry entry) {
        final List<String> filter = new ArrayList<>(2);
        if (entry.getKey() != null) {
            filter.add(entry.getKey().toString().toLowerCase());
        }
        return filter;
    }

//#if MC >= 12110
//$$     @Override
//$$     public boolean onMouseClicked(final Click click, final boolean isLeftClick) {
//$$         if (isInTotalsArea((int) click.x(), (int) click.y())) {
//$$             return false;
//$$         }
//$$         return super.onMouseClicked(click, isLeftClick);
//$$     }
//#else
    @Override
    public boolean onMouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (isInTotalsArea(mouseX, mouseY)) {
            return false;
        }
        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }
//#endif

//#if MC >= 12111
//$$     private void drawTotalsSection(final GuiContext guiContext) {
//$$         final int footerTop = getTotalsSectionTop();
//$$         RenderUtils.drawRect(guiContext, posX, footerTop - 1, browserWidth, 1, 0x60000000);
//$$         RenderUtils.drawRect(guiContext, posX, footerTop, browserWidth, TOTALS_SECTION_HEIGHT, 0x20000000);
//$$         drawTotalsRow(new TextDrawer() {
//$$             @Override
//$$             public void drawString(final String text, final int x, final int y, final int color) {
//$$                 WidgetListMaterialProgress.this.drawString(guiContext, text, x, y, color);
//$$             }
//$$         });
//$$     }
//#elseif MC >= 12106
//$$     private void drawTotalsSection(final DrawContext drawContext) {
//$$         final int footerTop = getTotalsSectionTop();
//$$         RenderUtils.drawRect(drawContext, posX, footerTop - 1, browserWidth, 1, 0x60000000);
//$$         RenderUtils.drawRect(drawContext, posX, footerTop, browserWidth, TOTALS_SECTION_HEIGHT, 0x20000000);
//$$         drawTotalsRow(new TextDrawer() {
//$$             @Override
//$$             public void drawString(final String text, final int x, final int y, final int color) {
//$$                 WidgetListMaterialProgress.this.drawString(drawContext, text, x, y, color);
//$$             }
//$$         });
//$$     }
//#elseif MC >= 12001
//$$     private void drawTotalsSection(final DrawContext drawContext) {
//$$         drawTotalsRow(new TextDrawer() {
//$$             @Override
//$$             public void drawString(final String text, final int x, final int y, final int color) {
//$$                 WidgetListMaterialProgress.this.drawString(drawContext, text, x, y, color);
//$$             }
//$$         });
//$$     }
//#else
    private void drawTotalsSection(final MatrixStack matrixStack) {
        drawTotalsRow(new TextDrawer() {
            @Override
            public void drawString(final String text, final int x, final int y, final int color) {
                WidgetListMaterialProgress.this.drawString(matrixStack, text, x, y, color);
            }
        });
    }
//#endif

    private void drawTotalsRow(final TextDrawer drawer) {
        // Footer math uses the same offsets as the header to avoid drift.
        final Totals totals = calculateTotals();
        final int footerTop = getTotalsSectionTop();
        final int textColor = 0xFFFFFFFF;
        final int baseX = posX + TOTALS_PADDING_X;
        final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
        final int missingColumnRight = posX + browserEntryWidth - 8;
        final int stockColumnRight = missingColumnRight - 100;
//#if MC < 12106
        RenderUtils.drawRect(posX, footerTop - 1, browserWidth, 1, 0x60000000);
        RenderUtils.drawRect(posX, footerTop, browserWidth, TOTALS_SECTION_HEIGHT, 0x20000000);
//#endif
        final String totalLabel = StringUtils.translate("syncmatica_r.gui.label.material.total");
        final String requiredValue = formatNumber(totals.required);
        final String stockValue = formatNumber(totals.stock);
        final String missingValue = formatNumber(totals.missing);
        drawer.drawString(totalLabel,
                baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, footerTop + 4, textColor);
        drawer.drawString(requiredValue,
                requiredColumnRight - getStringWidth(requiredValue), footerTop + 4, textColor);
        drawer.drawString(stockValue,
                stockColumnRight - getStringWidth(stockValue), footerTop + 4, textColor);
        drawer.drawString(missingValue,
                missingColumnRight - getStringWidth(missingValue), footerTop + 4, textColor);
    }

    // Totals are computed from the live placement snapshot to stay accurate.
    private Totals calculateTotals() {
        final Totals totals = new Totals();
        for (final SyncmaticaMaterialEntry entry : placement.getMaterialList().getEntries()) {
            totals.required += Math.max(0, entry.getAmountRequired());
            totals.stock += Math.max(0, entry.getAmountPresent());
            totals.missing += Math.max(0, entry.getAmountMissing());
        }
        return totals;
    }

    // Clamp footer so short panes still show the summary cleanly.
    private int getTotalsSectionTop() {
        final int headerBottom = posY + browserEntriesOffsetY;
        final int rawTop = posY + widgetHeight - TOTALS_SECTION_HEIGHT;
        return Math.max(rawTop, headerBottom + 1);
    }


    // Shield the footer from clicks so hidden rows stay unselectable.
    private boolean isInTotalsArea(final int mouseX, final int mouseY) {
        final int footerTop = getTotalsSectionTop();
        final int footerBottom = footerTop + TOTALS_SECTION_HEIGHT;
        return mouseX >= posX && mouseX <= posX + browserWidth
                && mouseY >= footerTop && mouseY <= footerBottom;
    }

    private String formatNumber(final int value) {
        return String.valueOf(Math.max(0, value));
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }

    private static final class Totals {
        private int required;
        private int stock;
        private int missing;
    }
}
