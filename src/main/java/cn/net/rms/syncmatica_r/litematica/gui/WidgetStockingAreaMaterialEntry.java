package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ClientCommunicationManager;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif

public class WidgetStockingAreaMaterialEntry extends WidgetListEntryBase<WidgetStockingAreaMaterialEntry.RowData> {

    private final ButtonGeneric viewButton;

    public WidgetStockingAreaMaterialEntry(final int x, final int y, final int width, final int height,
                                           final RowData data, final int listIndex) {
        super(x, y, width, height, data, listIndex);
        if (data.getType() == RowType.PLACEMENT) {
            // Per-placement action mirrors the original materials view.
            final String buttonLabel = StringUtils.translate("syncmatica_r.gui.button.material_gathering_placement");
            final int buttonWidth = Math.max(90, measure(buttonLabel) + 12);
            viewButton = new ButtonGeneric(x + width - buttonWidth - 6, y + 2, buttonWidth, height - 4, buttonLabel);
            viewButton.setEnabled(isMaterialFeatureEnabled());
            addButton(viewButton, (button, mouseButton) -> openMaterialGui(data.getPlacement()));
        } else {
            viewButton = null;
        }
    }

    @Override
    public void render(final int mouseX, final int mouseY, final boolean selected,
//#if MC >= 12001
//$$             final DrawContext drawContext
//#else
            final MatrixStack matrixStack
//#endif
    ) {
        final RowData data = getEntry();
        if (data == null) {
            return;
        }
        // Row rendering depends on whether this is a header, placement, or placeholder row.
        switch (data.getType()) {
            case HEADER:
                RenderUtils.drawRect(x, y, width, height, 0x40000000);
                drawHeader(mouseX, mouseY,
//#if MC >= 12001
//$$                         drawContext,
//#else
                        matrixStack,
//#endif
                        data.getHeaderText());
                break;
            case PLACEMENT:
                drawPlacementRow(mouseX, mouseY, data,
//#if MC >= 12001
//$$                         drawContext
//#else
                        matrixStack
//#endif
                );
                break;
            case EMPTY:
                RenderUtils.drawRect(x, y, width, height, 0x20000000);
                drawCenteredText(data.getHeaderText(),
//#if MC >= 12001
//$$                         drawContext
//#else
                        matrixStack
//#endif
                );
                break;
            default:
                break;
        }
    }

    private void drawHeader(final int mouseX, final int mouseY,
//#if MC >= 12001
//$$                           final DrawContext drawContext,
//#else
                            final MatrixStack matrixStack,
//#endif
                            final String text) {
        final int textColor = 0xFFFFFFFF;
//#if MC >= 12001
//$$         drawString(x + 8, y + 6, textColor, text, drawContext);
//#else
        drawString(x + 8, y + 6, textColor, text, matrixStack);
//#endif
    }

    private void drawCenteredText(final String text,
//#if MC >= 12001
//$$                                  final DrawContext drawContext
//#else
                                   final MatrixStack matrixStack
//#endif
    ) {
        final int textColor = 0x80FFFFFF;
        final int textWidth = measure(text);
        final int textX = x + (width - textWidth) / 2;
//#if MC >= 12001
//$$         drawString(textX, y + 6, textColor, text, drawContext);
//#else
        drawString(textX, y + 6, textColor, text, matrixStack);
//#endif
    }

    private void drawPlacementRow(final int mouseX, final int mouseY, final RowData data,
//#if MC >= 12001
//$$                                   final DrawContext drawContext
//#else
                                   final MatrixStack matrixStack
//#endif
    ) {
        final boolean hovered = isMouseOver(mouseX, mouseY);
        final int baseColor = data.isHighlighted() ? 0x3050D050 : 0x20000000;
        final int hoverColor = 0x40FFFFFF;
        final int background = hovered ? hoverColor : baseColor;
        RenderUtils.drawRect(x, y, width, height, background);
        final String name = data.getPlacement().getName();
//#if MC >= 12001
//$$         drawString(x + 10, y + 6, 0xFFFFFFFF, name, drawContext);
//$$         drawSubWidgets(mouseX, mouseY, drawContext);
//#else
        drawString(x + 10, y + 6, 0xFFFFFFFF, name, matrixStack);
        drawSubWidgets(mouseX, mouseY, matrixStack);
//#endif
    }

    private boolean isMaterialFeatureEnabled() {
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || !(context.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return false;
        }
        final ClientCommunicationManager manager = (ClientCommunicationManager) context.getCommunicationManager();
        return manager.getServer() != null
                && manager.getServer().getFeatureSet().hasFeature(Feature.MATERIAL_PROGRESS);
    }

    private void openMaterialGui(final ServerPlacement placement) {
        if (placement == null) {
            return;
        }
        GuiBase.openGui(new GuiSyncmaticaMaterialProgress(placement));
    }

    public enum RowType {
        HEADER,
        PLACEMENT,
        EMPTY
    }

    public static final class RowData {
        // Row metadata keeps rendering decisions data-driven and simplifies list rebuilding.
        private final RowType type;
        private final String headerText;
        private final ServerPlacement placement;
        private final boolean highlight;

        private RowData(final RowType type, final String headerText, final ServerPlacement placement, final boolean highlight) {
            this.type = type;
            this.headerText = headerText;
            this.placement = placement;
            this.highlight = highlight;
        }

        public static RowData header(final String text) {
            return new RowData(RowType.HEADER, text, null, false);
        }

        public static RowData placement(final ServerPlacement placement, final boolean highlight) {
            return new RowData(RowType.PLACEMENT, null, placement, highlight);
        }

        public static RowData empty(final String text) {
            return new RowData(RowType.EMPTY, text, null, false);
        }

        public RowType getType() {
            return type;
        }

        public String getHeaderText() {
            return headerText;
        }

        public ServerPlacement getPlacement() {
            return placement;
        }

        public boolean isHighlighted() {
            return highlight;
        }
    }

    // Text measurement needs to survive headless contexts, so keep a safe fallback.
    private int measure(final String text) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final TextRenderer renderer = client == null ? null : client.textRenderer;
        if (renderer == null) {
            return text.length() * 6;
        }
        return renderer.getWidth(text);
    }
}
