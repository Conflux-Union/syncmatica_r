package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Renders a single material progress row.
 */
public class WidgetMaterialProgressEntry extends WidgetListEntryBase<SyncmaticaMaterialEntry> {

    public WidgetMaterialProgressEntry(final int x, final int y, final int width, final int height, final SyncmaticaMaterialEntry entry, final int listIndex) {
        super(x, y, width, height, entry, listIndex);
    }

    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected, final MatrixStack matrixStack) {
        RenderUtils.drawRect(x, y, width, height, listIndex % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF);
        final SyncmaticaMaterialEntry material = getEntry();
        final int textColor = 0xFFFFFFFF;
        final int secondaryColor = 0xC0FFFFFF;
        int posX = x + 6;

        // Columns: material id, required, player supply, stocked supply, missing total, claimed owner.

        drawString(posX, y + 6, textColor, material.getKey() != null ? material.getKey().toString() : "unknown", matrixStack);
        posX += 140;
        drawString(posX, y + 6, secondaryColor, String.valueOf(material.getAmountRequired()), matrixStack);
        posX += 40;
        drawString(posX, y + 6, secondaryColor, String.valueOf(material.getPlayerSupplied()), matrixStack);
        posX += 40;
        drawString(posX, y + 6, secondaryColor, String.valueOf(material.getStockingSupplied()), matrixStack);
        posX += 40;
        drawString(posX, y + 6, material.isFinished() ? 0x80FF80 : 0xFFFF80, String.valueOf(material.getAmountMissing()), matrixStack);
        posX += 40;
        final String claimed = material.isClaimed() ? material.getClaimedBy() : "-";
        drawString(posX, y + 6, secondaryColor, claimed, matrixStack);
    }
}
