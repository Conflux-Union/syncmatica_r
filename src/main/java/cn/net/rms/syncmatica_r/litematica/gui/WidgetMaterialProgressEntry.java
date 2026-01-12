package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//$$ import net.minecraft.text.Text;
//#endif
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12001
//$$ import net.minecraft.registry.Registries;
//#else
import net.minecraft.util.registry.Registry;
//#endif

public class WidgetMaterialProgressEntry extends WidgetListEntryBase<SyncmaticaMaterialEntry> {

    public static final int NAME_COLUMN_LEFT_OFFSET = 24;
    public static final int REQUIRED_COLUMN_RIGHT_OFFSET = 194;
    public static final int STOCK_COLUMN_RIGHT_OFFSET = 244;
    public static final int MISSING_COLUMN_RIGHT_OFFSET = 294;

    private final ServerPlacement placement;

    public WidgetMaterialProgressEntry(final int x, final int y, final int width, final int height,
                                       final SyncmaticaMaterialEntry entry, final int listIndex,
                                       final ServerPlacement placement) {
        super(x, y, width, height, entry, listIndex);
        this.placement = placement;
    }

//#if MC >= 12111
//$$     @Override
//$$     public void render(final GuiContext guiContext, final int mouseX, final int mouseY, final boolean selected) {
//$$         final DrawContext drawContext = guiContext;
//#elseif MC >= 12110
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

        RenderUtils.drawRect(x, y, width, height, resolveBaseBackgroundColor());

        final SyncmaticaMaterialEntry material = resolveCurrentEntry();

        if (material != null && material.getClaimers() != null && !material.getClaimers().isEmpty()) {
            final net.minecraft.client.MinecraftClient cli = net.minecraft.client.MinecraftClient.getInstance();
            final String self = cli != null && cli.player != null ? SyncmaticaUtil.getProfileName(cli.player.getGameProfile()) : "";
            final boolean selfClaimed = material.getClaimers().contains(self);
            final int overlay = selfClaimed ? 0x3040FF40 : 0x30FFFF80;
            RenderUtils.drawRect(x, y, width, height, overlay);
        }
        final int textColor = 0xFFFFFFFF;
        final int secondaryColor = 0xC0FFFFFF;

        final int baseX = x + 6;
        final int requiredColumnRight = baseX + REQUIRED_COLUMN_RIGHT_OFFSET;

        final int missingColumnRight = x + width - 8;

        final int stockColumnRight = missingColumnRight - 100;

        final ItemStack stack = resolveDisplayStack(material == null ? null : material.getKey());
        if (!stack.isEmpty()) {
            renderItemStack(stack, baseX, y + 2,
//#if MC >= 12001
//$$                     drawContext
//#else
                    matrixStack
//#endif
            );
        }

        final String displayName = resolveDisplayName(material, stack);
        final int nameX = baseX + NAME_COLUMN_LEFT_OFFSET;
//#if MC >= 12111
//$$         drawString(guiContext, nameX, y + 6, textColor, displayName);
//#elseif MC >= 12110
//$$         drawString(drawContext, nameX, y + 6, textColor, displayName);
//#elseif MC >= 12001
//$$         drawString(nameX, y + 6, textColor, displayName, drawContext);
//#else
        drawString(nameX, y + 6, textColor, displayName, matrixStack);
//#endif

        final String requiredText = String.valueOf(material.getAmountRequired());
//#if MC >= 12111
//$$         drawString(guiContext, requiredColumnRight - getStringWidth(requiredText), y + 6, secondaryColor, requiredText);
//#elseif MC >= 12110
//$$         drawString(drawContext, requiredColumnRight - getStringWidth(requiredText), y + 6, secondaryColor, requiredText);
//#elseif MC >= 12001
//$$         drawString(requiredColumnRight - getStringWidth(requiredText), y + 6, secondaryColor, requiredText, drawContext);
//#else
        drawString(requiredColumnRight - getStringWidth(requiredText), y + 6, secondaryColor, requiredText, matrixStack);
//#endif

        final String stockText = String.valueOf(material.getStockingSupplied());
//#if MC >= 12111
//$$         drawString(guiContext, stockColumnRight - getStringWidth(stockText), y + 6, secondaryColor, stockText);
//#elseif MC >= 12110
//$$         drawString(drawContext, stockColumnRight - getStringWidth(stockText), y + 6, secondaryColor, stockText);
//#elseif MC >= 12001
//$$         drawString(stockColumnRight - getStringWidth(stockText), y + 6, secondaryColor, stockText, drawContext);
//#else
        drawString(stockColumnRight - getStringWidth(stockText), y + 6, secondaryColor, stockText, matrixStack);
//#endif

        final String missingText = formatMissingShortText(material);
        final int missingColor = material.isFinished() ? 0x80FF80 : 0xFFFF80;
        final int missingTextX = missingColumnRight - getStringWidth(missingText);
//#if MC >= 12111
//$$         drawString(guiContext, missingTextX, y + 6, missingColor, missingText);
//#elseif MC >= 12110
//$$         drawString(drawContext, missingTextX, y + 6, missingColor, missingText);
//#elseif MC >= 12001
//$$         drawString(missingTextX, y + 6, missingColor, missingText, drawContext);
//#else
        drawString(missingTextX, y + 6, missingColor, missingText, matrixStack);
//#endif

        final int missingLeftBound = stockColumnRight + 4;
        final int rowTop = y;
        final int rowBottom = y + height;
        if (mouseX >= missingLeftBound && mouseX <= missingColumnRight && mouseY >= rowTop && mouseY <= rowBottom) {
            final net.minecraft.client.gui.screen.Screen screen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
            if (screen != null) {
                final java.util.List<net.minecraft.text.Text> lines = java.util.Collections.singletonList(
                        literal(formatMissingVerboseText(material))
                );
//#if MC >= 12001
//$$                 final MinecraftClient client = MinecraftClient.getInstance();
//$$                 final TextRenderer renderer = client.textRenderer;
//$$                 drawContext.drawTooltip(renderer, lines, mouseX, mouseY);
//#else
                screen.renderTooltip(matrixStack, lines, mouseX, mouseY);
//#endif
            }
        }

        final int blankLeft = nameX + getStringWidth(displayName) + 6;
        final int blankRight = requiredColumnRight - 6;
        if (shouldRenderClaimTooltip(material) && mouseX >= blankLeft && mouseX <= blankRight
                && mouseY >= rowTop && mouseY <= rowBottom) {
            final net.minecraft.client.gui.screen.Screen screen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
            if (screen != null) {
                final java.util.List<net.minecraft.text.Text> lines = new java.util.ArrayList<>();
                final java.util.List<String> claimers = material == null ? java.util.Collections.emptyList() : material.getClaimers();
                if (claimers.isEmpty()) {
                    lines.add(literal(fi.dy.masa.malilib.util.StringUtils.translate("syncmatica_r.gui.tooltip.material.claim.none")));
                } else {
                    final String joined = String.join(", ", claimers);
                    lines.add(literal(
                            fi.dy.masa.malilib.util.StringUtils.translate("syncmatica_r.gui.tooltip.material.claimers", joined))
                    );
                }
                final net.minecraft.client.MinecraftClient cli = net.minecraft.client.MinecraftClient.getInstance();
                final String self = cli != null && cli.player != null ? SyncmaticaUtil.getProfileName(cli.player.getGameProfile()) : "";
                final boolean canToggle = claimers.isEmpty() || claimers.contains(self);
                final String key = canToggle
                        ? "syncmatica_r.gui.tooltip.material.claim.action"
                        : "syncmatica_r.gui.tooltip.material.claim.locked";
                lines.add(literal(fi.dy.masa.malilib.util.StringUtils.translate(key)));
//#if MC >= 12001
//$$                 final MinecraftClient client2 = MinecraftClient.getInstance();
//$$                 final TextRenderer textRenderer = client2.textRenderer;
//$$                 drawContext.drawTooltip(textRenderer, lines, mouseX, mouseY);
//#else
                screen.renderTooltip(matrixStack, lines, mouseX, mouseY);
//#endif
            }
        }
    }

    protected boolean shouldRenderClaimTooltip(final SyncmaticaMaterialEntry material) {
        return true;
    }

    public boolean mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton != 0 || !isMouseOver(mouseX, mouseY) || placement == null) {
            return false;
        }
        if (!fi.dy.masa.malilib.gui.GuiBase.isShiftDown()) {
            return false;
        }
        final SyncmaticaMaterialEntry material = getEntry();
        final int baseX = x + 6;
        final int requiredColumnRight = baseX + REQUIRED_COLUMN_RIGHT_OFFSET;

        final int blankLeft = baseX + NAME_COLUMN_LEFT_OFFSET;
        final int blankRight = requiredColumnRight - 6;
        if (mouseX < blankLeft || mouseX > blankRight || mouseY < y || mouseY > y + height) {
            return false;
        }

        final java.util.List<String> claimers = material == null ? java.util.Collections.emptyList() : material.getClaimers();
        final net.minecraft.client.MinecraftClient cli = net.minecraft.client.MinecraftClient.getInstance();
        final String self = cli != null && cli.player != null ? SyncmaticaUtil.getProfileName(cli.player.getGameProfile()) : "";
        if (!claimers.isEmpty() && !claimers.contains(self)) {
            return true;
        }
        sendToggleClaim(material);
        return true;
    }

    public boolean onMouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        return mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void sendToggleClaim(final SyncmaticaMaterialEntry material) {
        if (material == null || material.getKey() == null) {
            return;
        }
        final Context con = LitematicManager.getInstance().getActiveContext();
        if (!(con.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return;
        }
        final ExchangeTarget server = ((ClientCommunicationManager) con.getCommunicationManager()).getServer();
        if (server == null) {
            return;
        }
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(placement.getId());
        buf.writeString(material.getKey().itemId().toString());
        buf.writeString(material.getKey().variant());
        server.sendPacket(PacketType.MATERIAL_CLAIM_TOGGLE.toIdentifier(server.getProtocolFlavor()), buf, con);
    }

    private SyncmaticaMaterialEntry resolveCurrentEntry() {
        final SyncmaticaMaterialEntry original = getEntry();
        if (placement == null || original == null || original.getKey() == null) {
            return original;
        }
        for (final SyncmaticaMaterialEntry e : placement.getMaterialList().getEntries()) {
            if (e.getKey() != null && e.getKey().equals(original.getKey())) {
                return e;
            }
        }
        return original;
    }

    private ItemStack resolveDisplayStack(final MaterialKey key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        //#if MC >= 12001
        //$$ final Item item = Registries.ITEM.get(key.itemId());
        //#else
        final Item item = Registry.ITEM.get(key.itemId());
        //#endif
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private void renderItemStack(final ItemStack stack, final int iconX, final int iconY,
//#if MC >= 12001
//$$             final DrawContext drawContext
//#else
            final MatrixStack matrixStack
//#endif
    ) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ItemRenderer itemRenderer = client.getItemRenderer();
        final TextRenderer textRenderer = client.textRenderer;

//#if MC >= 12001
//$$         drawContext.drawItem(stack, iconX, iconY);
//$$         drawContext.drawItemInSlot(textRenderer, stack, iconX, iconY);
//#else
        RenderSystem.enableDepthTest();
        itemRenderer.renderInGui(stack, iconX, iconY);
        itemRenderer.renderGuiItemOverlay(textRenderer, stack, iconX, iconY);
        RenderSystem.disableDepthTest();
//#endif
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

        return fi.dy.masa.malilib.util.StringUtils.translate(
                "syncmatica_r.gui.label.material.missing.format",
                boxes, stacks, singles, totalMissing
        );
    }

    private String formatMissingShortText(final SyncmaticaMaterialEntry material) {
        if (material == null) {
            return "0";
        }
        final int totalMissing = Math.max(0, material.getAmountMissing());
        return fi.dy.masa.malilib.util.StringUtils.translate(
                "syncmatica_r.gui.label.material.missing.short.total", totalMissing
        );
    }

    protected int resolveBaseBackgroundColor() {
        return listIndex % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF;
    }

    private net.minecraft.text.Text literal(final String text) {
//#if MC >= 12001
//$$         return Text.literal(text);
//#else
        return new net.minecraft.text.LiteralText(text);
//#endif
    }
}
