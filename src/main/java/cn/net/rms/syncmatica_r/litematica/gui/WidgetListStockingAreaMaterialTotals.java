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
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif
//#if MC >= 12110
//$$ import net.minecraft.client.gui.Click;
//#endif
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WidgetListStockingAreaMaterialTotals extends WidgetListBase<
        SyncmaticaMaterialEntry,
        WidgetMaterialProgressEntry> {

    private static final Comparator<SyncmaticaMaterialEntry> ENTRY_COMPARATOR = (left, right) -> {
        final int leftMissing = left.getAmountMissing();
        final int rightMissing = right.getAmountMissing();
        if (leftMissing != rightMissing) {
            return Integer.compare(rightMissing, leftMissing);
        }
        final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
        final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
        return leftKey.compareTo(rightKey);
    };
    private static final int TOTALS_SECTION_HEIGHT = 18;
    private static final int TOTALS_PADDING_X = 6;

    private final List<ServerPlacement> placements = new ArrayList<>();
    private final List<SyncmaticaMaterialEntry> cachedEntries = new ArrayList<>();
    private final Map<SyncmaticaMaterialEntry, AggregateBucket> entryIndex = new HashMap<>();
    private final Totals totals = new Totals();
    private int widgetHeight;

    public WidgetListStockingAreaMaterialTotals(final int x, final int y, final int width, final int height,
                                                final WidgetStockingAreaMaterialEntry.StockingAreaSummary summary) {
        super(x, y, width, height, null);
        browserEntryHeight = 20;
        if (summary != null) {
            placements.addAll(summary.getPlacements());
        }
        configureBrowserBounds(width, height);
    }

    @Override
    public void setSize(final int width, final int height) {
        super.setSize(width, height);
        configureBrowserBounds(width, height);
    }

    private void configureBrowserBounds(final int width, final int height) {
        widgetHeight = height;
        browserEntryWidth = width - 8;
        browserWidth = width;
        browserEntriesOffsetY = 18;
        browserHeight = Math.max(browserEntriesOffsetY + 1, height - TOTALS_SECTION_HEIGHT);
    }

//#if MC >= 12111
//$$     @Override
//$$     public void drawContents(final GuiContext guiContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         drawHeader(guiContext);
//$$         super.drawContents(guiContext, mouseX, mouseY, partialTicks);
//$$         drawTotalsSection(guiContext);
//$$     }
//#elseif MC >= 12001
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         drawHeader(drawContext);
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$         drawTotalsSection(drawContext);
//$$     }
//#else
    @Override
    public void drawContents(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        drawHeader(matrixStack);
        super.drawContents(matrixStack, mouseX, mouseY, partialTicks);
        drawTotalsSection(matrixStack);
    }
//#endif

    @Override
    protected Collection<SyncmaticaMaterialEntry> getAllEntries() {
        rebuildAggregates();
        return cachedEntries;
    }

    @Override
    protected WidgetMaterialProgressEntry createListEntryWidget(final int x, final int y, final int listIndex,
                                                                final boolean isOdd, final SyncmaticaMaterialEntry entry) {
        final AggregateBucket bucket = entryIndex.get(entry);
        return new WidgetStockingAreaMaterialTotalsEntry(
                x, y, browserEntryWidth, browserEntryHeight, entry, listIndex, this, bucket);
    }

    @Override
    protected List<String> getEntryStringsForFilter(final SyncmaticaMaterialEntry entry) {
        final List<String> filter = new ArrayList<>(2);
        if (entry.getKey() != null) {
            filter.add(entry.getKey().toString().toLowerCase());
        }
        return filter;
    }

//#if MC >= 12110
//$$     @Override
//$$     public boolean onMouseClicked(final Click click, final boolean isLeftClick) {
//$$         if (isInTotalsArea((int) click.x(), (int) click.y())) {
//$$             return false;
//$$         }
//$$         return super.onMouseClicked(click, isLeftClick);
//$$     }
//#else
    @Override
    public boolean onMouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (isInTotalsArea(mouseX, mouseY)) {
            return false;
        }
        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }
//#endif

    boolean handleAggregateToggle(final AggregateBucket bucket) {
        if (bucket == null) {
            return false;
        }
        if (bucket.isCompletelyUnclaimed()) {
            return broadcastToggle(bucket);
        }
        final String self = getCurrentPlayerName();
        if (bucket.canAggregateUnclaim(self)) {
            return broadcastToggle(bucket);
        }
        return false;
    }

    boolean canAggregateClaim(final AggregateBucket bucket) {
        return bucket != null && bucket.isCompletelyUnclaimed();
    }

    boolean canAggregateUnclaim(final AggregateBucket bucket) {
        final String self = getCurrentPlayerName();
        return bucket != null && bucket.canAggregateUnclaim(self);
    }

    private boolean broadcastToggle(final AggregateBucket bucket) {
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || !(context.getCommunicationManager() instanceof ClientCommunicationManager)) {
            return false;
        }
        final ClientCommunicationManager manager = (ClientCommunicationManager) context.getCommunicationManager();
        final ExchangeTarget server = manager.getServer();
        if (server == null) {
            return false;
        }
        for (final ServerPlacement placement : bucket.getSourcePlacements()) {
            if (placement == null) {
                continue;
            }
            final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(placement.getId());
            buf.writeString(bucket.getKey().itemId().toString());
            buf.writeString(bucket.getKey().variant());
            server.sendPacket(PacketType.MATERIAL_CLAIM_TOGGLE.toIdentifier(server.getProtocolFlavor()), buf, context);
        }
        return true;
    }

    private String getCurrentPlayerName() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "";
        }
        return SyncmaticaUtil.getProfileName(client.player.getGameProfile());
    }

    private void rebuildAggregates() {
        cachedEntries.clear();
        entryIndex.clear();
        totals.reset();
        final Map<MaterialKey, AggregateBucket> merged = new HashMap<>();
        for (final ServerPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            for (final SyncmaticaMaterialEntry entry : placement.getMaterialList().getEntries()) {
                final MaterialKey key = entry.getKey();
                if (key == null) {
                    continue;
                }
                merged.computeIfAbsent(key, AggregateBucket::new).add(placement, entry);
            }
        }
        for (final AggregateBucket bucket : merged.values()) {
            final SyncmaticaMaterialEntry aggregated = bucket.toEntry();
            cachedEntries.add(aggregated);
            entryIndex.put(aggregated, bucket);
            totals.required += Math.max(0, aggregated.getAmountRequired());
            totals.stock += Math.max(0, aggregated.getAmountPresent());
            totals.missing += Math.max(0, aggregated.getAmountMissing());
        }
        cachedEntries.sort(ENTRY_COMPARATOR);
    }

//#if MC >= 12111
//$$     private void drawHeader(final GuiContext guiContext) {
//$$         RenderUtils.drawRect(guiContext, posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$         drawString(guiContext, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$         final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
//$$         drawString(guiContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$         final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
//$$         drawString(guiContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$         final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
//$$         drawString(guiContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$     }
//#elseif MC >= 12106
//$$     private void drawHeader(final DrawContext drawContext) {
//$$         RenderUtils.drawRect(drawContext, posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$         drawString(drawContext, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$         final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
//$$         drawString(drawContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$         final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
//$$         drawString(drawContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$         final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
//$$         drawString(drawContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$     }
//#elseif MC >= 12001
//$$     private void drawHeader(final DrawContext drawContext) {
//$$         RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
//$$         final int baseX = posX + 6;
//$$         final int textColor = 0xFFFFFFFF;
//$$         final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
//$$         final int missingColumnRight = posX + browserEntryWidth - 8;
//$$         final int stockColumnRight = missingColumnRight - 100;
//$$         drawString(drawContext, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
//$$                 baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
//$$         final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
//$$         drawString(drawContext, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
//$$         final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
//$$         drawString(drawContext, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
//$$         final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
//$$         drawString(drawContext, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
//$$     }
//#else
    private void drawHeader(final MatrixStack matrixStack) {
        RenderUtils.drawRect(posX, posY, browserWidth, browserEntriesOffsetY, 0x30000000);
        final int baseX = posX + 6;
        final int textColor = 0xFFFFFFFF;
        final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
        final int missingColumnRight = posX + browserEntryWidth - 8;
        final int stockColumnRight = missingColumnRight - 100;
        drawString(matrixStack, StringUtils.translate("syncmatica_r.gui.label.material.column.material"),
                baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, posY + 6, textColor);
        final String requiredLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.required");
        drawString(matrixStack, requiredLabel, requiredColumnRight - getStringWidth(requiredLabel), posY + 6, textColor);
        final String stockLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.stock");
        drawString(matrixStack, stockLabel, stockColumnRight - getStringWidth(stockLabel), posY + 6, textColor);
        final String missingLabel = StringUtils.translate("syncmatica_r.gui.label.material.column.missing");
        drawString(matrixStack, missingLabel, missingColumnRight - getStringWidth(missingLabel), posY + 6, textColor);
    }
//#endif

//#if MC >= 12111
//$$     private void drawTotalsSection(final GuiContext guiContext) {
//$$         final int footerTop = getTotalsSectionTop();
//$$         RenderUtils.drawRect(guiContext, posX, footerTop - 1, browserWidth, 1, 0x60000000);
//$$         RenderUtils.drawRect(guiContext, posX, footerTop, browserWidth, TOTALS_SECTION_HEIGHT, 0x20000000);
//$$         drawTotalsRow(new TextDrawer() {
//$$             @Override
//$$             public void drawString(final String text, final int x, final int y, final int color) {
//$$                 WidgetListStockingAreaMaterialTotals.this.drawString(guiContext, text, x, y, color);
//$$             }
//$$         });
//$$     }
//#elseif MC >= 12106
//$$     private void drawTotalsSection(final DrawContext drawContext) {
//$$         final int footerTop = getTotalsSectionTop();
//$$         RenderUtils.drawRect(drawContext, posX, footerTop - 1, browserWidth, 1, 0x60000000);
//$$         RenderUtils.drawRect(drawContext, posX, footerTop, browserWidth, TOTALS_SECTION_HEIGHT, 0x20000000);
//$$         drawTotalsRow(new TextDrawer() {
//$$             @Override
//$$             public void drawString(final String text, final int x, final int y, final int color) {
//$$                 WidgetListStockingAreaMaterialTotals.this.drawString(drawContext, text, x, y, color);
//$$             }
//$$         });
//$$     }
//#elseif MC >= 12001
//$$     private void drawTotalsSection(final DrawContext drawContext) {
//$$         drawTotalsRow(new TextDrawer() {
//$$             @Override
//$$             public void drawString(final String text, final int x, final int y, final int color) {
//$$                 WidgetListStockingAreaMaterialTotals.this.drawString(drawContext, text, x, y, color);
//$$             }
//$$         });
//$$     }
//#else
    private void drawTotalsSection(final MatrixStack matrixStack) {
        drawTotalsRow(new TextDrawer() {
            @Override
            public void drawString(final String text, final int x, final int y, final int color) {
                WidgetListStockingAreaMaterialTotals.this.drawString(matrixStack, text, x, y, color);
            }
        });
    }
//#endif

    private void drawTotalsRow(final TextDrawer drawer) {
        final int footerTop = getTotalsSectionTop();
        final int textColor = 0xFFFFFFFF;
        final int baseX = posX + TOTALS_PADDING_X;
        final int requiredColumnRight = baseX + WidgetMaterialProgressEntry.REQUIRED_COLUMN_RIGHT_OFFSET;
        final int missingColumnRight = posX + browserEntryWidth - 8;
        final int stockColumnRight = missingColumnRight - 100;
//#if MC < 12106
        RenderUtils.drawRect(posX, footerTop - 1, browserWidth, 1, 0x60000000);
        RenderUtils.drawRect(posX, footerTop, browserWidth, TOTALS_SECTION_HEIGHT, 0x20000000);
//#endif
        final String totalLabel = StringUtils.translate("syncmatica_r.gui.label.material.total");
        final String requiredValue = formatNumber(totals.required);
        final String stockValue = formatNumber(totals.stock);
        final String missingValue = formatNumber(totals.missing);
        drawer.drawString(totalLabel,
                baseX + WidgetMaterialProgressEntry.NAME_COLUMN_LEFT_OFFSET, footerTop + 4, textColor);
        drawer.drawString(requiredValue,
                requiredColumnRight - getStringWidth(requiredValue), footerTop + 4, textColor);
        drawer.drawString(stockValue,
                stockColumnRight - getStringWidth(stockValue), footerTop + 4, textColor);
        drawer.drawString(missingValue,
                missingColumnRight - getStringWidth(missingValue), footerTop + 4, textColor);
    }

    private int getTotalsSectionTop() {
        final int headerBottom = posY + browserEntriesOffsetY;
        final int rawTop = posY + widgetHeight - TOTALS_SECTION_HEIGHT;
        return Math.max(rawTop, headerBottom + 1);
    }

    private boolean isInTotalsArea(final int mouseX, final int mouseY) {
        final int footerTop = getTotalsSectionTop();
        final int footerBottom = footerTop + TOTALS_SECTION_HEIGHT;
        return mouseX >= posX && mouseX <= posX + browserWidth
                && mouseY >= footerTop && mouseY <= footerBottom;
    }

    private String formatNumber(final int value) {
        return String.valueOf(Math.max(0, value));
    }

    private static final class Totals {
        private int required;
        private int stock;
        private int missing;

        private void reset() {
            required = 0;
            stock = 0;
            missing = 0;
        }
    }

    static final class AggregateBucket {
        private final MaterialKey key;
        private final SyncmaticaMaterialEntry entry = new SyncmaticaMaterialEntry();
        private final List<ServerPlacement> sourcePlacements = new ArrayList<>();
        private int required;
        private int stock;
        private boolean claimersDiverged;
        private boolean sawUnclaimed;
        private boolean hasAnyClaimer;
        private List<String> sharedClaimers;
        private boolean partialClaim;

        private AggregateBucket(final MaterialKey key) {
            this.key = key;
            entry.setKey(key);
        }

        private void add(final ServerPlacement placement, final SyncmaticaMaterialEntry source) {
            sourcePlacements.add(placement);
            required += Math.max(0, source.getAmountRequired());
            stock += Math.max(0, source.getAmountPresent());
            final List<String> claimers = source.getClaimers();
            if (claimers.isEmpty()) {
                sawUnclaimed = true;
            } else {
                hasAnyClaimer = true;
                if (sharedClaimers == null) {
                    sharedClaimers = new ArrayList<>(claimers);
                } else if (!sharedClaimers.equals(claimers)) {
                    claimersDiverged = true;
                }
            }
        }

        private SyncmaticaMaterialEntry toEntry() {
            entry.setAmountRequired(required);
            entry.setStockingSupplied(stock);
            partialClaim = computePartialClaimState();
            if (isUniformlyClaimed()) {
                entry.setClaimers(sharedClaimers == null ? Collections.emptyList() : sharedClaimers);
                partialClaim = false;
            } else {
                entry.setClaimers(Collections.emptyList());
            }
            return entry;
        }

        private List<ServerPlacement> getSourcePlacements() {
            return sourcePlacements;
        }

        private MaterialKey getKey() {
            return key;
        }

        private boolean isUniformlyClaimed() {
            return hasAnyClaimer && !sawUnclaimed && !claimersDiverged && sharedClaimers != null && !sharedClaimers.isEmpty();
        }

        private boolean isCompletelyUnclaimed() {
            return !hasAnyClaimer && sawUnclaimed && !sourcePlacements.isEmpty();
        }

        private boolean canAggregateUnclaim(final String player) {
            return isUniformlyClaimed() && player != null && !player.isEmpty() && sharedClaimers.contains(player);
        }

        private List<String> getSharedClaimers() {
            if (sharedClaimers == null) {
                return Collections.emptyList();
            }
            return sharedClaimers;
        }

        boolean isPartiallyClaimed() {
            return partialClaim;
        }

        private boolean computePartialClaimState() {
            return (hasAnyClaimer && sawUnclaimed) || claimersDiverged;
        }
    }

    private interface TextDrawer {
        void drawString(String text, int x, int y, int color);
    }
}
