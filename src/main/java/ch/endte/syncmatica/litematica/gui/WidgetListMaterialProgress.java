package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Backed by the placement material snapshot.
 */
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

    @Override
    public void drawContents(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
        final int baseX = posX + 6;
        final int textColor = 0xFFFFFFFF;
        // Header row labels stay fixed while entries scroll beneath.
        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.material"), baseX, posY + 6, textColor);
        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.required"), baseX + 140, posY + 6, textColor);
        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.player"), baseX + 180, posY + 6, textColor);
        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.stock"), baseX + 220, posY + 6, textColor);
        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.missing"), baseX + 260, posY + 6, textColor);
        drawString(matrixStack, StringUtils.translate("syncmatica.gui.label.material.column.claimed"), baseX + 300, posY + 6, textColor);
        super.drawContents(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    protected Collection<SyncmaticaMaterialEntry> getAllEntries() {
        final List<SyncmaticaMaterialEntry> snapshot = new ArrayList<>(placement.getMaterialList().getEntries());
        snapshot.sort((left, right) -> {
            final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
            final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
            return leftKey.compareTo(rightKey);
        });
        return snapshot;
    }

    @Override
    protected WidgetMaterialProgressEntry createListEntryWidget(final int x, final int y, final int listIndex, final boolean isOdd, final SyncmaticaMaterialEntry entry) {
        return new WidgetMaterialProgressEntry(x, y, browserEntryWidth, browserEntryHeight, entry, listIndex);
    }

    @Override
    protected List<String> getEntryStringsForFilter(final SyncmaticaMaterialEntry entry) {
        final List<String> filter = new ArrayList<>(2);
        if (entry.getKey() != null) {
            filter.add(entry.getKey().toString().toLowerCase());
        }
        filter.add(String.valueOf(entry.getClaimedBy()));
        return filter;
    }
}
