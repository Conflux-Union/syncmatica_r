package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
//#if MC >= 12110
//$$ import net.minecraft.client.gui.Click;
//#endif

/**
 * One shared schematic in the build management overview. Clicking the row opens
 * its regions.
 */
public class WidgetBuildPlacementEntry extends WidgetListEntryBase<ServerPlacement> {

    static final int CLAIMED_COLUMN_WIDTH = 70;

    /**
     * Handed down rather than looked up from the client: which screen is current
     * is asked for differently across versions, and the list already knows who
     * owns it.
     */
    private final GuiBuildManagement parent;

    public WidgetBuildPlacementEntry(final int x, final int y, final int width, final int height,
                                     final ServerPlacement placement, final int listIndex,
                                     final GuiBuildManagement parent) {
        super(x, y, width, height, placement, listIndex);
        this.parent = parent;
    }

//#if MC >= 12111
//$$     @Override
//$$     public void render(final GuiContext guiContext, final int mouseX, final int mouseY, final boolean selected) {
//#elseif MC >= 12106
//$$     @Override
//$$     public void render(final DrawContext drawContext, final int mouseX, final int mouseY, final boolean selected) {
//#else
    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected,
//#if MC >= 12001
//$$             final DrawContext drawContext
//#else
            final MatrixStack matrixStack
//#endif
    ) {
//#endif
        // Bundling the version-specific draw calls into two local adapters keeps
        // the rest of this method free of preprocessor blocks.
        final RectDrawer rects = (rx, ry, rw, rh, color) -> {
//#if MC >= 12111
//$$             RenderUtils.drawRect(guiContext, rx, ry, rw, rh, color);
//#elseif MC >= 12106
//$$             RenderUtils.drawRect(drawContext, rx, ry, rw, rh, color);
//#else
            RenderUtils.drawRect(rx, ry, rw, rh, color);
//#endif
        };
        final TextDrawer texts = (text, tx, ty, color) -> {
//#if MC >= 12111
//$$             drawString(guiContext, tx, ty, color, text);
//#elseif MC >= 12106
//$$             drawString(drawContext, tx, ty, color, text);
//#elseif MC >= 12001
//$$             drawString(tx, ty, color, text, drawContext);
//#else
            drawString(tx, ty, color, text, matrixStack);
//#endif
        };

        final ServerPlacement placement = getEntry();
        final boolean hovered = isMouseOver(mouseX, mouseY);
        rects.drawRect(x, y, width, height,
                hovered ? 0x40FFFFFF : (listIndex % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF));
        if (placement == null) {
            return;
        }

        final int claimedColumnRight = x + width - 8;
        final int regionColumnRight = claimedColumnRight - CLAIMED_COLUMN_WIDTH;
        final int textY = y + 6;

        texts.drawString(placement.getName(), x + 6, textY, 0xFFFFFFFF);

        int regionCount = 0;
        int claimedCount = 0;
        for (final BuildRegion region : placement.getBuildRegions().getRegions()) {
            regionCount++;
            if (region.isClaimed()) {
                claimedCount++;
            }
        }

        final String regionText = Integer.toString(regionCount);
        texts.drawString(regionText, regionColumnRight - getStringWidth(regionText), textY,
                regionCount == 0 ? 0x80FFFFFF : 0xFFFFFFFF);

        final String claimedText = claimedCount + "/" + regionCount;
        texts.drawString(claimedText, claimedColumnRight - getStringWidth(claimedText), textY,
                claimedColor(regionCount, claimedCount));
    }

    private static int claimedColor(final int regionCount, final int claimedCount) {
        if (regionCount == 0) {
            return 0x80FFFFFF;
        }
        return claimedCount == regionCount ? 0xFF80FF80 : 0xFFFFFF80;
    }

//#if MC >= 12110
//$$     @Override
//$$     protected boolean onMouseClickedImpl(final Click click, final boolean isLeftClick) {
//$$         return mouseClickedImpl((int) click.x(), (int) click.y(), click.button());
//$$     }
//$$
//$$     public boolean mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
//$$         return mouseClickedImpl(mouseX, mouseY, mouseButton);
//$$     }
//#else
    public boolean mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        return mouseClickedImpl(mouseX, mouseY, mouseButton);
    }

    public boolean onMouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        return mouseClicked(mouseX, mouseY, mouseButton);
    }
//#endif

    protected boolean mouseClickedImpl(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton != 0 || !isMouseOver(mouseX, mouseY) || getEntry() == null) {
            return false;
        }
        final GuiBuildRegions gui = new GuiBuildRegions(getEntry());
        gui.setParent(parent);
        GuiBase.openGui(gui);
        return true;
    }

    private interface RectDrawer {
        void drawRect(int x, int y, int width, int height, int color);
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }
}
