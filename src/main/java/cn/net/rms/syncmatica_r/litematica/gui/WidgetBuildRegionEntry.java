package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12001
//$$ import net.minecraft.client.font.TextRenderer;
//$$ import net.minecraft.client.gui.DrawContext;
//$$ import net.minecraft.text.Text;
//#endif
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
//#if MC >= 12110
//$$ import net.minecraft.client.gui.Click;
//#endif

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One claimable sub-region row. Shift + left click toggles the claim, matching
 * how materials are claimed one screen over.
 */
public class WidgetBuildRegionEntry extends WidgetListEntryBase<BuildRegion> {

    private final ServerPlacement placement;

    public WidgetBuildRegionEntry(final int x, final int y, final int width, final int height,
                                  final BuildRegion region, final int listIndex,
                                  final ServerPlacement placement) {
        super(x, y, width, height, region, listIndex);
        this.placement = placement;
    }

//#if MC >= 12111
//$$     @Override
//$$     public void render(final GuiContext guiContext, final int mouseX, final int mouseY, final boolean selected) {
//$$         final DrawContext drawContext = guiContext;
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

        final BuildRegion region = resolveCurrentEntry();
        rects.drawRect(x, y, width, height, listIndex % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF);

        final List<String> claimers = collectClaimerNames(region);
        if (!claimers.isEmpty()) {
            rects.drawRect(x, y, width, height, claimers.contains(selfName()) ? 0x3040FF40 : 0x30FFFF80);
        }

        final int claimerColumnRight = x + width - 8;
        final int textY = y + 6;

        texts.drawString(region == null ? "" : region.getRegionName(), x + 6, textY, 0xFFFFFFFF);

        final String claimerText = claimers.isEmpty()
                ? StringUtils.translate("syncmatica_r.gui.label.build.unassigned")
                : String.join(", ", claimers);
        texts.drawString(claimerText, claimerColumnRight - getStringWidth(claimerText), textY,
                claimers.isEmpty() ? 0x80FFFFFF : 0xFFFFFF80);

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            drawClaimTooltip(region, claimers, mouseX, mouseY,
//#if MC >= 12111
//$$                     guiContext
//#elseif MC >= 12001
//$$                     drawContext
//#else
                    matrixStack
//#endif
            );
        }
    }

    private void drawClaimTooltip(final BuildRegion region, final List<String> claimers,
                                  final int mouseX, final int mouseY,
//#if MC >= 12111
//$$             final GuiContext guiContext
//#elseif MC >= 12001
//$$             final DrawContext drawContext
//#else
            final MatrixStack matrixStack
//#endif
    ) {
        final String actionKey = claimers.isEmpty() || claimers.contains(selfName())
                ? "syncmatica_r.gui.tooltip.build.claim.action"
                : "syncmatica_r.gui.tooltip.build.claim.locked";
        final String ownerLine = claimers.isEmpty()
                ? StringUtils.translate("syncmatica_r.gui.tooltip.build.claim.none")
                : StringUtils.translate("syncmatica_r.gui.tooltip.build.claimers", String.join(", ", claimers));
        final String blocksLine = region == null ? "" : StringUtils.translate(
                "syncmatica_r.gui.tooltip.build.blocks", region.getRequiredBlocks());
//#if MC >= 12111
//$$         final List<String> lines = new ArrayList<>();
//$$         lines.add(ownerLine);
//$$         if (!blocksLine.isEmpty()) {
//$$             lines.add(blocksLine);
//$$         }
//$$         lines.add(StringUtils.translate(actionKey));
//$$         RenderUtils.drawHoverText(guiContext, mouseX, mouseY, lines);
//#else
        final List<net.minecraft.text.Text> lines = new ArrayList<>();
        lines.add(literal(ownerLine));
        if (!blocksLine.isEmpty()) {
            lines.add(literal(blocksLine));
        }
        lines.add(literal(StringUtils.translate(actionKey)));
//#if MC >= 12001
//$$         final MinecraftClient client = MinecraftClient.getInstance();
//$$         final TextRenderer textRenderer = client.textRenderer;
//$$         drawContext.drawTooltip(textRenderer, lines, mouseX, mouseY);
//#else
        final net.minecraft.client.gui.screen.Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen != null) {
            screen.renderTooltip(matrixStack, lines, mouseX, mouseY);
        }
//#endif
//#endif
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
        if (mouseButton != 0 || !isMouseOver(mouseX, mouseY) || placement == null || !GuiBase.isShiftDown()) {
            return false;
        }
        final BuildRegion region = resolveCurrentEntry();
        if (region == null) {
            return false;
        }
        final List<String> claimers = collectClaimerNames(region);
        // Swallow the click rather than sending a request the server would refuse.
        if (!claimers.isEmpty() && !claimers.contains(selfName())) {
            return true;
        }
        sendToggleClaim(region);
        return true;
    }

    private void sendToggleClaim(final BuildRegion region) {
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || !(context.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return;
        }
        final ExchangeTarget server = ((ClientCommunicationManager) context.getCommunicationManager()).getServer();
        if (server == null) {
            return;
        }
        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(placement.getId());
        buf.writeString(region.getRegionName(), ProtocolLimits.MAX_SUBREGION_NAME_LENGTH);
        server.sendPacket(PacketType.BUILD_REGION_CLAIM.toIdentifier(server.getProtocolFlavor()), buf, context);
    }

    /**
     * The placement's region state is replaced wholesale on every server update,
     * so the row has to look the entry up again instead of trusting the snapshot
     * it was created with.
     */
    private BuildRegion resolveCurrentEntry() {
        final BuildRegion original = getEntry();
        if (placement == null || original == null) {
            return original;
        }
        final BuildRegion current = placement.getBuildRegions().get(original.getRegionName());
        return current == null ? original : current;
    }

    private List<String> collectClaimerNames(final BuildRegion region) {
        if (region == null || !region.isClaimed()) {
            return Collections.emptyList();
        }
        final List<String> names = new ArrayList<>();
        for (final PlayerIdentifier claimer : region.getClaimants()) {
            names.add(claimer.getName());
        }
        return names;
    }

    private String selfName() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "";
        }
        return SyncmaticaUtil.getProfileName(client.player.getGameProfile());
    }

    private net.minecraft.text.Text literal(final String text) {
//#if MC >= 12001
//$$         return Text.literal(text);
//#else
        return new net.minecraft.text.LiteralText(text);
//#endif
    }

    private interface RectDrawer {
        void drawRect(int x, int y, int width, int height, int color);
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }
}
