package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Renders a single material progress row showing stocking totals.
 */
public class WidgetMaterialProgressEntry extends WidgetListEntryBase<SyncmaticaMaterialEntry> {
    public WidgetMaterialProgressEntry(final int x, final int y, final int width, final int height,
                                       final SyncmaticaMaterialEntry entry, final int listIndex) {
        super(x, y, width, height, entry, listIndex);
    }

    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected, final MatrixStack matrixStack) {
        RenderUtils.drawRect(x, y, width, height, listIndex % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF);
        final SyncmaticaMaterialEntry material = getEntry();
        final int textColor = 0xFFFFFFFF;
        final int secondaryColor = 0xC0FFFFFF;

        final int baseX = x + 6;
        final int requiredColumnRight = baseX + 170;
        final int stockColumnRight = baseX + 220;
        final int missingColumnRight = baseX + 270;
        
        drawString(baseX, y + 6, textColor, material.getKey() != null ? material.getKey().toString() : "unknown", matrixStack);
        
        final String requiredText = String.valueOf(material.getAmountRequired());
        drawString(requiredColumnRight - getStringWidth(requiredText), y + 6, secondaryColor, requiredText, matrixStack);
        
        final String stockText = String.valueOf(material.getStockingSupplied());
        drawString(stockColumnRight - getStringWidth(stockText), y + 6, secondaryColor, stockText, matrixStack);
        
        final String missingText = String.valueOf(material.getAmountMissing());
        drawString(missingColumnRight - getStringWidth(missingText), y + 6, material.isFinished() ? 0x80FF80 : 0xFFFF80, missingText, matrixStack);
    }
}
