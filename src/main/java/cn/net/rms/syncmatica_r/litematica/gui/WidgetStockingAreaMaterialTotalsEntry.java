package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif

import java.util.Collections;
import java.util.List;

public class WidgetStockingAreaMaterialTotalsEntry extends WidgetMaterialProgressEntry {

    private final WidgetListStockingAreaMaterialTotals owner;
    private final WidgetListStockingAreaMaterialTotals.AggregateBucket bucket;

    public WidgetStockingAreaMaterialTotalsEntry(final int x, final int y, final int width, final int height,
                                                 final SyncmaticaMaterialEntry entry, final int listIndex,
                                                 final WidgetListStockingAreaMaterialTotals owner,
                                                 final WidgetListStockingAreaMaterialTotals.AggregateBucket bucket) {
        super(x, y, width, height, entry, listIndex, null);
        this.owner = owner;
        this.bucket = bucket;
    }

    @Override
    protected boolean mouseClickedImpl(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton != 0 || owner == null || bucket == null) {
            return super.mouseClickedImpl(mouseX, mouseY, mouseButton);
        }
        if (!isMouseOver(mouseX, mouseY) || !GuiBase.isShiftDown()) {
            return false;
        }
        if (!isClaimToggleZone(mouseX, mouseY)) {
            return false;
        }
        if (!owner.handleAggregateToggle(bucket)) {
            return false;
        }
        return true;
    }

    private boolean isClaimToggleZone(final int mouseX, final int mouseY) {
        final int baseX = x + 6;
        final int requiredColumnRight = baseX + REQUIRED_COLUMN_RIGHT_OFFSET;
        final int blankLeft = baseX + NAME_COLUMN_LEFT_OFFSET;
        final int blankRight = requiredColumnRight - 6;
        return mouseX >= blankLeft && mouseX <= blankRight && mouseY >= y && mouseY <= y + height;
    }

    @Override
    protected int resolveBaseBackgroundColor() {
        if (bucket != null && bucket.isPartiallyClaimed()) {
            return 0x30FF8080;
        }
        return super.resolveBaseBackgroundColor();
    }

    @Override
    protected boolean shouldRenderClaimTooltip(final SyncmaticaMaterialEntry material) {
        return bucket == null || !bucket.isPartiallyClaimed();
    }

//#if MC >= 12111
//$$     @Override
//$$     public void render(final GuiContext guiContext, final int mouseX, final int mouseY, final boolean selected) {
//$$         super.render(guiContext, mouseX, mouseY, selected);
//$$         final DrawContext drawContext = guiContext;
//$$         if (bucket != null && bucket.isPartiallyClaimed() && isClaimToggleZone(mouseX, mouseY)) {
//$$             drawPartialClaimTooltip(mouseX, mouseY, drawContext);
//$$         }
//$$     }
//#elseif MC >= 12106
//$$     @Override
//$$     public void render(final DrawContext drawContext, final int mouseX, final int mouseY, final boolean selected) {
//$$         super.render(drawContext, mouseX, mouseY, selected);
//$$         if (bucket != null && bucket.isPartiallyClaimed() && isClaimToggleZone(mouseX, mouseY)) {
//$$             drawPartialClaimTooltip(mouseX, mouseY, drawContext);
//$$         }
//$$     }
//#else
    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected,
//#if MC >= 12001
//$$                       final DrawContext drawContext
//#else
                      final MatrixStack matrixStack
//#endif
    ) {
        super.render(mouseX, mouseY, selected,
//#if MC >= 12001
//$$                 drawContext
//#else
                matrixStack
//#endif
        );
        if (bucket != null && bucket.isPartiallyClaimed() && isClaimToggleZone(mouseX, mouseY)) {
            drawPartialClaimTooltip(mouseX, mouseY,
//#if MC >= 12001
//$$                     drawContext
//#else
                    matrixStack
//#endif
            );
        }
    }
//#endif

    private void drawPartialClaimTooltip(final int mouseX, final int mouseY,
//#if MC >= 12001
//$$                                         final DrawContext drawContext
//#else
                                         final MatrixStack matrixStack
//#endif
    ) {
        final String text = StringUtils.translate("syncmatica_r.gui.tooltip.material.partial_claim");
        final List<net.minecraft.text.Text> lines = Collections.singletonList(literal(text));
//#if MC >= 12001
//$$         final MinecraftClient client = MinecraftClient.getInstance();
//$$         if (drawContext != null && client != null) {
//$$             final TextRenderer renderer = client.textRenderer;
//$$             if (renderer != null) {
//$$                 drawContext.drawTooltip(renderer, lines, mouseX, mouseY);
//$$             }
//$$         }
//#else
        final Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen != null) {
            screen.renderTooltip(matrixStack, lines, mouseX, mouseY);
        }
//#endif
    }

    private net.minecraft.text.Text literal(final String text) {
//#if MC >= 12001
//$$         return net.minecraft.text.Text.literal(text);
//#else
        return new net.minecraft.text.LiteralText(text);
//#endif
    }
}
