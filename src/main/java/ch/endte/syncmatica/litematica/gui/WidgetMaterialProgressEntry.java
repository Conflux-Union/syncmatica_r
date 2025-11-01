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
    // Shared layout offsets for both header and rows.
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
        final int stockColumnRight = baseX + STOCK_COLUMN_RIGHT_OFFSET;
        final int missingColumnRight = baseX + MISSING_COLUMN_RIGHT_OFFSET;

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

        final String missingText = String.valueOf(material.getAmountMissing());
        drawString(missingColumnRight - getStringWidth(missingText), y + 6, material.isFinished() ? 0x80FF80 : 0xFFFF80, missingText, matrixStack);
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

    // Fallback to the identifier string if no translation is available.
    private String resolveDisplayName(final SyncmaticaMaterialEntry material, final ItemStack stack) {
        if (!stack.isEmpty()) {
            return stack.getName().getString();
        }
        if (material != null && material.getKey() != null) {
            return material.getKey().toString();
        }
        return "unknown";
    }
}
