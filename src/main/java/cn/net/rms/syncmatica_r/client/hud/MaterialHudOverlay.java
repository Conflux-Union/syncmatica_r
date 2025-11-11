package cn.net.rms.syncmatica_r.client.hud;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.client.HudPreferences;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//$$ import net.minecraft.text.Text;
//#endif
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

// Lightweight HUD overlay that surfaces the most blocking stocking tasks.
public final class MaterialHudOverlay implements HudRenderCallback {

    private static final int MAX_ROWS = 6;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int HUD_MARGIN = 8;
    private static final MaterialHudOverlay INSTANCE = new MaterialHudOverlay();

    // Keeps the HUD in sync with placement changes without extra ticking.
    private final Consumer<ServerPlacement> placementListener = placement -> refreshSnapshot();

    private Context context;
    private List<Row> rows = Collections.emptyList();
    private String header = null;
    private boolean needsRefresh;
    private double hudScale = 1.0d;

    private MaterialHudOverlay() {
    }

    public static MaterialHudOverlay getInstance() {
        return INSTANCE;
    }

    public void register() {
        HudRenderCallback.EVENT.register(this);
    }

    public void setHudScale(final double scale) {
        final double clamped = HudPreferences.clampScale(scale);
        if (Double.compare(hudScale, clamped) != 0) {
            hudScale = clamped;
            needsRefresh = true;
        }
    }

    public double getHudScale() {
        return hudScale;
    }

    // Called from the network actor once the client context exists.
    public void bindToClientContext(final Context ctx) {
        if (ctx == context) {
            return;
        }
        detach();
        if (ctx == null) {
            return;
        }
        context = ctx;
        ctx.getSyncmaticManager().addServerPlacementConsumer(placementListener);
        needsRefresh = true;
    }

    public void reset() {
        detach();
    }

    private void detach() {
        if (context != null) {
            context.getSyncmaticManager().removeServerPlacementConsumer(placementListener);
        }
        context = null;
        rows = Collections.emptyList();
        header = null;
        needsRefresh = false;
    }

    // Snapshot rows are rebuilt lazily to avoid per-frame churn.
    private void refreshSnapshot() {
        if (context == null) {
            rows = Collections.emptyList();
            header = null;
            needsRefresh = false;
            return;
        }
        final String playerName = resolvePlayerName();
        if (playerName == null || playerName.isEmpty()) {
            rows = Collections.emptyList();
            header = null;
            needsRefresh = false;
            return;
        }
        rows = buildRows(playerName);
        header = rows.isEmpty() ? null : StringUtils.translate("syncmatica_r.hud.claimed_header");
        needsRefresh = false;
    }

    private List<Row> buildRows(final String playerName) {
        final Map<MaterialKey, Aggregate> aggregates = new HashMap<>();
        for (final ServerPlacement placement : context.getSyncmaticManager().getAll()) {
            for (final SyncmaticaMaterialEntry entry : placement.getMaterialList().getEntries()) {
                if (entry == null) {
                    continue;
                }
                if (entry.isFinished()) {
                    continue;
                }
                if (entry.getAmountMissing() <= 0) {
                    continue;
                }
                if (!entry.getClaimers().contains(playerName)) {
                    continue;
                }
                final MaterialKey key = entry.getKey();
                final int missing = entry.getAmountMissing();
                if (missing <= 0) {
                    continue;
                }
                final ItemStack stack = resolveDisplayStack(key);
                final Aggregate aggregate = aggregates.computeIfAbsent(key,
                        ignored -> new Aggregate(stack, resolveDisplayName(entry, stack)));
                aggregate.addMissing(missing);
            }
        }
        final List<Aggregate> sorted = new ArrayList<>(aggregates.values());
        sorted.sort(Comparator.comparingInt(Aggregate::getTotalMissing).reversed());
        final List<Row> snapshot = new ArrayList<>(Math.min(sorted.size(), MAX_ROWS));
        int index = 0;
        for (final Aggregate aggregate : sorted) {
            if (index >= MAX_ROWS) {
                break;
            }
            final String missingText = formatMissingVerboseText(aggregate.getTotalMissing(), aggregate.stack);
            snapshot.add(new Row(aggregate.stack, aggregate.name, missingText));
            index++;
        }
        return snapshot;
    }

    private String resolvePlayerName() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        if (client.player != null && client.player.getGameProfile() != null) {
            return client.player.getGameProfile().getName();
        }
        if (client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return null;
    }

    // No HUD when client options or rows are missing.
    private boolean shouldRender() {
        if (MinecraftClient.getInstance().options == null) {
            return false;
        }
        if (rows.isEmpty()) {
            return false;
        }
        return true;
    }

//#if MC >= 12001
//$$     @Override
//$$     public void onHudRender(final DrawContext drawContext, final float tickDelta) {
//$$         render(null, drawContext);
//$$     }
//#else
    @Override
    public void onHudRender(final MatrixStack matrices, final float tickDelta) {
        render(matrices, null);
    }
//#endif

    private void render(final MatrixStack matrices, final Object drawContext) {
        if (needsRefresh) {
            refreshSnapshot();
        }
        if (!shouldRender()) {
            return;
        }
        final MinecraftClient client = MinecraftClient.getInstance();
        final TextRenderer textRenderer = client.textRenderer;
        final int padding = scale(PADDING);
        final int margin = scale(HUD_MARGIN);
        final int rowHeight = scale(ROW_HEIGHT);
        final int rowSpacing = Math.max(2, scale(2));
        final int headerHeight = Math.max(12, scale(14));
        final int iconSize = scale(ICON_SIZE);
        final int textGap = Math.max(4, scale(4));
        final int extraWidth = Math.max(20, scale(20));
        final int contentWidth = Math.max(scale(180), computeMinWidth(textRenderer, iconSize, textGap, extraWidth));
        final int contentHeight = rows.size() * (rowHeight + rowSpacing) + headerHeight;
        final int screenWidth = client.getWindow().getScaledWidth();
        final int screenHeight = client.getWindow().getScaledHeight();
        final int x = Math.max(margin, screenWidth - contentWidth - padding * 2 - margin);
        final int y = Math.max(margin, screenHeight - contentHeight - padding * 2 - margin);
        RenderUtils.drawRect(x, y, contentWidth + padding * 2, contentHeight + padding * 2, 0x80000000);
        final int headerColor = 0xFFFFFF00;
        final String title = header == null ? "" : header;
        drawText(textRenderer, matrices, drawContext, title, x + padding, y + padding, headerColor);

        int rowY = y + padding + headerHeight;
        for (final Row row : rows) {
            drawRow(textRenderer, matrices, drawContext, x + padding, rowY, contentWidth, row, iconSize, textGap);
            rowY += rowHeight + rowSpacing;
        }
    }

    // Dynamic width keeps translations from overflowing the panel.
    private int computeMinWidth(final TextRenderer renderer, final int iconSize, final int textGap, final int extraWidth) {
        int max = header == null ? 0 : renderer.getWidth(header);
        for (final Row row : rows) {
            final int total = iconSize + textGap + renderer.getWidth(row.name)
                    + renderer.getWidth(row.missingText()) + textGap;
            if (total > max) {
                max = total;
            }
        }
        return max + extraWidth;
    }

    private void drawRow(final TextRenderer renderer, final MatrixStack matrices, final Object drawContext,
                         final int x, final int y, final int contentWidth, final Row row,
                         final int iconSize, final int textGap) {
        renderItemStack(row.stack, x, y - Math.max(1, scale(2)),
//#if MC >= 12001
//$$                 (DrawContext) drawContext
//#else
                matrices
//#endif
        );
        final int textX = x + iconSize + textGap;
        final int textY = y + Math.max(1, scale(2));
        final int rightEdge = x + contentWidth;
        drawText(renderer, matrices, drawContext, row.name, textX, textY, 0xFFFFFFFF);
        final int missingWidth = renderer.getWidth(row.missingText());
        final int missingX = rightEdge - missingWidth;
        drawText(renderer, matrices, drawContext, row.missingText(), missingX, textY, 0xFFFFAAAA);
    }

    private void drawText(final TextRenderer renderer, final MatrixStack matrices, final Object drawContext, final String text, final int x, final int y, final int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
//#if MC >= 12001
//$$         ((DrawContext) Objects.requireNonNull(drawContext)).drawText(renderer, text, x, y, color, true);
//#else
        renderer.drawWithShadow(Objects.requireNonNull(matrices), text, x, y, color);
//#endif
    }

    public void scheduleRefresh() {
        needsRefresh = true;
    }

    private ItemStack resolveDisplayStack(final MaterialKey key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        final Item item = Registry.ITEM.get(key.itemId());
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private String resolveDisplayName(final SyncmaticaMaterialEntry entry, final ItemStack stack) {
        if (!stack.isEmpty()) {
            return stack.getName().getString();
        }
        if (entry != null && entry.getKey() != null) {
            return entry.getKey().toString();
        }
        return "unknown";
    }

    private String formatMissingVerboseText(final SyncmaticaMaterialEntry entry, final ItemStack stack) {
        if (entry == null) {
            return "0";
        }
        return formatMissingVerboseText(Math.max(0, entry.getAmountMissing()), stack);
    }

    private String formatMissingVerboseText(final int totalMissing, final ItemStack stack) {
        final int stackSize = stack.isEmpty() ? 64 : Math.max(1, stack.getMaxCount());
        final int perBox = 27 * stackSize;
        final int boxes = totalMissing / perBox;
        final int remAfterBoxes = totalMissing - boxes * perBox;
        final int stacks = remAfterBoxes / stackSize;
        final int singles = remAfterBoxes - stacks * stackSize;
        return StringUtils.translate("syncmatica_r.gui.label.material.missing.compact", boxes, stacks, singles);
    }

    private void renderItemStack(final ItemStack stack, final int iconX, final int iconY,
//#if MC >= 12001
//$$             final DrawContext drawContext
//#else
            final MatrixStack matrices
//#endif
    ) {
        if (stack.isEmpty()) {
            return;
        }
        final MinecraftClient client = MinecraftClient.getInstance();
        final ItemRenderer renderer = client.getItemRenderer();
        final TextRenderer textRenderer = client.textRenderer;

//#if MC >= 12001
//$$         drawContext.drawItem(stack, iconX, iconY);
//$$         drawContext.drawItemInSlot(textRenderer, stack, iconX, iconY);
//#else
        RenderSystem.enableDepthTest();
        renderer.renderInGui(stack, iconX, iconY);
        renderer.renderGuiItemOverlay(textRenderer, stack, iconX, iconY);
        RenderSystem.disableDepthTest();
//#endif
    }

    private int scale(final int base) {
        return Math.max(1, (int) Math.round(base * hudScale));
    }

    // Immutable row snapshot used to avoid sharing mutable state across threads.
    private static final class Row {
        private final ItemStack stack;
        private final String name;
        private final String missingText;

        private Row(final ItemStack stack, final String name, final String missingText) {
            this.stack = stack;
            this.name = name;
            this.missingText = missingText;
        }

        private String missingText() {
            return missingText;
        }
    }

    private static final class Aggregate {
        private final ItemStack stack;
        private final String name;
        private int totalMissing;

        private Aggregate(final ItemStack stack, final String name) {
            this.stack = stack.copy();
            this.name = name;
        }

        private void addMissing(final int missing) {
            totalMissing += Math.max(0, missing);
        }

        private int getTotalMissing() {
            return totalMissing;
        }
    }
}
