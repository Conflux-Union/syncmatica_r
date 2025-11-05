package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WidgetListMaterialProgress extends WidgetListBase<SyncmaticaMaterialEntry, WidgetMaterialProgressEntry> {

    private final ServerPlacement placement;

    public WidgetListMaterialProgress(final int x, final int y, final int width, final int height, final ServerPlacement placement) {
        super(x, y, width, height, null);
        browserEntryHeight = 20;
        browserEntryWidth = width - 8;
        browserWidth = width;
        browserHeight = height;
        this.placement = placement;
        browserEntriesOffsetY = 18;
    }

//#if MC < 12001
    @Override
    public void drawContents(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
        final int baseX = posX + 6;
        final int textColor = 0xFFFFFFFF;

        final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;

        final int missingColumnRight = posX + browserEntryWidth - 8;

        final int stockColumnRight = missingColumnRight - 100;

        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.material"),
                baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);

        final String requiredLabel = StringUtils.translate("syncmatica.gui.label.material.column.required");
        drawString(matrixStack, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);

        final String stockLabel = StringUtils.translate("syncmatica.gui.label.material.column.stock");
        drawString(matrixStack, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);

        final String missingLabel = StringUtils.translate("syncmatica.gui.label.material.column.missing");
        drawString(matrixStack, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);

        super.drawContents(matrixStack, mouseX, mouseY, partialTicks);
    }
//#else
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$ 
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$ 
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$ 
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$ 
//$$         drawString(drawContext, StringUtils.translate("syncmatica.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$ 
//$$         final String requiredLabel = StringUtils.translate("syncmatica.gui.label.material.column.required");
//$$         drawString(drawContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$ 
//$$         final String stockLabel = StringUtils.translate("syncmatica.gui.label.material.column.stock");
//$$         drawString(drawContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$ 
//$$         final String missingLabel = StringUtils.translate("syncmatica.gui.label.material.column.missing");
//$$         drawString(drawContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$ 
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$     }
//#endif

    @Override
    protected Collection<SyncmaticaMaterialEntry> getAllEntries() {

        final List<SyncmaticaMaterialEntry> snapshot = new ArrayList<>(placement.getMaterialList().getEntries());
        snapshot.sort((left, right) -> {
            final int lm = left.getAmountMissing();
            final int rm = right.getAmountMissing();
            if (lm != rm) {
                return Integer.compare(rm, lm);
            }
            final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
            final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
            return leftKey.compareTo(rightKey);
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
}
