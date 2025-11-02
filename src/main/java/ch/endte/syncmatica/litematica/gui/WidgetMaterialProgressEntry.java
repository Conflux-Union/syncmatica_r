package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.material.MaterialKey;
import ch.endte.syncmatica.material.SyncmaticaMaterialEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;

/**
 * Renders a single material progress row showing stocking totals.
 */
public class WidgetMaterialProgressEntry extends WidgetListEntryBase<SyncmaticaMaterialEntry> {
    
    public static final int NAME_COLUMN_LEFT_OFFSET = 24;
    public static final int REQUIRED_COLUMN_RIGHT_OFFSET = 194;
    public static final int STOCK_COLUMN_RIGHT_OFFSET = 244;
    public static final int MISSING_COLUMN_RIGHT_OFFSET = 294;

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
        final int requiredColumnRight = baseX + REQUIRED_COLUMN_RIGHT_OFFSET;
        // Anchor the missing column to the right edge to use available space.
        final int missingColumnRight = x + width - 8;
        // Place stock column between total(required) and missing by anchoring it near the missing column.
        final int stockColumnRight = missingColumnRight - 100;

        final ItemStack stack = resolveDisplayStack(material == null ? null : material.getKey());
        if (!stack.isEmpty()) {
            renderItemStack(stack, baseX, y + 2);
        }

        final String displayName = resolveDisplayName(material, stack);
        drawString(baseX + NAME_COLUMN_LEFT_OFFSET, y + 6, textColor, displayName, matrixStack);

        final String requiredText = String.valueOf(material.getAmountRequired());
        drawString(requiredColumnRight - getStringWidth(requiredText), y + 6, secondaryColor, requiredText, matrixStack);

        final String stockText = String.valueOf(material.getStockingSupplied());
        drawString(stockColumnRight - getStringWidth(stockText), y + 6, secondaryColor, stockText, matrixStack);

        final String missingText = formatMissingShortText(material);
        final int missingColor = material.isFinished() ? 0x80FF80 : 0xFFFF80;
        final int missingTextX = missingColumnRight - getStringWidth(missingText);
        drawString(missingTextX, y + 6, missingColor, missingText, matrixStack);

        // Tooltip on hover: show verbose format when mouse is over the missing column.
        final int missingLeftBound = stockColumnRight + 4;
        final int rowTop = y;
        final int rowBottom = y + height;
        if (mouseX >= missingLeftBound && mouseX <= missingColumnRight && mouseY >= rowTop && mouseY <= rowBottom) {
            final net.minecraft.client.gui.screen.Screen screen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
            if (screen != null) {
                final java.util.List<net.minecraft.text.Text> lines = java.util.Collections.singletonList(
                        new net.minecraft.text.LiteralText(formatMissingVerboseText(material))
                );
                screen.renderTooltip(matrixStack, lines, mouseX, mouseY);
            }
        }
    }

    // Build a simple display stack from the material identifier.
    private ItemStack resolveDisplayStack(final MaterialKey key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        final Item item = Registry.ITEM.get(key.getItemId());
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    // Render the stack icon using the vanilla item renderer.
    private void renderItemStack(final ItemStack stack, final int iconX, final int iconY) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ItemRenderer itemRenderer = client.getItemRenderer();
        final TextRenderer textRenderer = client.textRenderer;

        RenderSystem.enableDepthTest();
        itemRenderer.renderInGui(stack, iconX, iconY);
        itemRenderer.renderGuiItemOverlay(textRenderer, stack, iconX, iconY);
        RenderSystem.disableDepthTest();
    }

    
    private String resolveDisplayName(final SyncmaticaMaterialEntry material, final ItemStack stack) {
        if (!stack.isEmpty()) {
            return stack.getName().getString();
        }
        if (material != null && material.getKey() != null) {
            return material.getKey().toString();
        }
        return "unknown";
    }

    // Verbose format: <boxes> Shulkers <stacks> Stacks <singles> Items (<total> Items) via i18n.
    private String formatMissingVerboseText(final SyncmaticaMaterialEntry material) {
        if (material == null) {
            return "0";
        }
        final int totalMissing = Math.max(0, material.getAmountMissing());
        final ItemStack stack = resolveDisplayStack(material.getKey());

        
        final int stackSize = stack.isEmpty() ? 64 : Math.max(1, stack.getMaxCount());
        final int perShulker = 27 * stackSize;

        final int boxes = totalMissing / perShulker;
        final int remAfterBoxes = totalMissing - boxes * perShulker;
        final int stacks = remAfterBoxes / stackSize;
        final int singles = remAfterBoxes - stacks * stackSize;

        // Use translation key to avoid embedding non-English literals in code.
        // Key should be defined in language assets as a printf-style pattern.
        return fi.dy.masa.malilib.util.StringUtils.translate(
                "syncmatica.gui.label.material.missing.format",
                boxes, stacks, singles, totalMissing
        );
    }

    
    private String formatMissingShortText(final SyncmaticaMaterialEntry material) {
        if (material == null) {
            return "0";
        }
        final int totalMissing = Math.max(0, material.getAmountMissing());
        return fi.dy.masa.malilib.util.StringUtils.translate(
                "syncmatica.gui.label.material.missing.short.total", totalMissing
        );
    }
}
