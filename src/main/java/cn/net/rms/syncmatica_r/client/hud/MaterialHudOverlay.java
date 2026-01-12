package cn.net.rms.syncmatica_r.client.hud;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.client.HudPreferences;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.SyncmaticaMaterialEntry;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import cn.net.rms.syncmatica_r.util.NbtHelper;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12110
//$$ import net.minecraft.client.gui.DrawContext;
//$$ import net.minecraft.client.render.RenderTickCounter;
//$$ import net.minecraft.component.DataComponentTypes;
//$$ import net.minecraft.entity.TypedEntityData;
//$$ import net.minecraft.block.entity.BlockEntityType;
//$$ import net.minecraft.text.Text;
//#elseif MC >= 12005
//$$ import net.minecraft.client.gui.DrawContext;
//$$ import net.minecraft.client.render.RenderTickCounter;
//$$ import net.minecraft.component.DataComponentTypes;
//$$ import net.minecraft.component.type.NbtComponent;
//$$ import net.minecraft.text.Text;
//#elseif MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//$$ import net.minecraft.text.Text;
//#endif
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
//#if MC >= 12001
//$$ import net.minecraft.registry.Registries;
//#else
import net.minecraft.util.registry.Registry;
//#endif
//#if MC >= 12110
//$$ import net.minecraft.entity.EquipmentSlot;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

// Lightweight HUD overlay that surfaces the most blocking stocking tasks.
public final class MaterialHudOverlay implements HudRenderCallback {

    private static final int MAX_ROWS = 6;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int HUD_MARGIN = 8;
    private static final MaterialHudOverlay INSTANCE = new MaterialHudOverlay();
    private static final Logger LOGGER = LogManager.getLogger(MaterialHudOverlay.class);

    // Keeps the HUD in sync with placement changes without extra ticking.
    private final Consumer<ServerPlacement> placementListener = placement -> refreshSnapshot();

    private Context context;
    private List<Row> rows = Collections.emptyList();
    private String header = null;
    private boolean needsRefresh;
    private double hudScale = 1.0d;
    private int lastInventoryFingerprint;
    private boolean inventoryFingerprintInitialized;

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
        inventoryFingerprintInitialized = false;
        lastInventoryFingerprint = 0;
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
        final Map<MaterialKey, Integer> playerInventoryTotals = buildPlayerInventoryTotals();
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
                        ignored -> new Aggregate(key, stack, resolveDisplayName(entry, stack)));
                aggregate.addMissing(missing);
            }
        }
        final List<Aggregate> sorted = new ArrayList<>(aggregates.values());
        sorted.removeIf(aggregate -> aggregate.applyInventoryOffset(playerInventoryTotals
                .getOrDefault(aggregate.getKey(), 0)) <= 0);
        sorted.sort(Comparator.comparingInt(Aggregate::getEffectiveMissing).reversed());
        final List<Row> snapshot = new ArrayList<>(Math.min(sorted.size(), MAX_ROWS));
        int index = 0;
        for (final Aggregate aggregate : sorted) {
            if (index >= MAX_ROWS) {
                break;
            }
            final String missingText = formatMissingVerboseText(aggregate.getEffectiveMissing(), aggregate.stack);
            snapshot.add(new Row(aggregate.stack, aggregate.name, missingText));
            index++;
        }
        return snapshot;
    }

    private void observeInventoryFingerprint() {
        final Integer fingerprint = computeInventoryFingerprint();
        if (fingerprint == null) {
            if (inventoryFingerprintInitialized) {
                inventoryFingerprintInitialized = false;
                needsRefresh = true;
            }
            return;
        }
        if (!inventoryFingerprintInitialized || fingerprint != lastInventoryFingerprint) {
            lastInventoryFingerprint = fingerprint;
            inventoryFingerprintInitialized = true;
            needsRefresh = true;
        }
    }

    private Integer computeInventoryFingerprint() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return null;
        }
        final PlayerInventory inventory = client.player.getInventory();
        if (inventory == null) {
            return null;
        }
        int fingerprint = 1;
        //#if MC >= 12110
        //$$ fingerprint = accumulateFingerprint(inventory.getMainStacks(), fingerprint);
        //$$ fingerprint = accumulateFingerprintFromStack(client.player.getEquippedStack(EquipmentSlot.OFFHAND), fingerprint);
        //#else
        fingerprint = accumulateFingerprint(inventory.main, fingerprint);
        fingerprint = accumulateFingerprint(inventory.offHand, fingerprint);
        //#endif
        return fingerprint;
    }

    private Map<MaterialKey, Integer> buildPlayerInventoryTotals() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Collections.emptyMap();
        }
        final PlayerInventory inventory = client.player.getInventory();
        if (inventory == null) {
            return Collections.emptyMap();
        }
        final Map<MaterialKey, Integer> totals = new HashMap<>();
        //#if MC >= 12110
        //$$ accumulateStacks(inventory.getMainStacks(), totals);
        //$$ accumulateStack(client.player.getEquippedStack(EquipmentSlot.OFFHAND), totals);
        //#else
        accumulateStacks(inventory.main, totals);
        accumulateStacks(inventory.offHand, totals);
        //#endif
        return totals;
    }

    private void accumulateStacks(final Iterable<ItemStack> stacks, final Map<MaterialKey, Integer> totals) {
        if (stacks == null) {
            return;
        }
        for (final ItemStack stack : stacks) {
            accumulateStack(stack, totals);
        }
    }

    private void accumulateStack(final ItemStack stack, final Map<MaterialKey, Integer> totals) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final Item item = stack.getItem();
        if (item == null || item == Items.AIR) {
            return;
        }
        //#if MC >= 12001
        //$$ final Identifier itemId = Registries.ITEM.getId(item);
        //#else
        final Identifier itemId = Registry.ITEM.getId(item);
        //#endif
        if (itemId == null) {
            return;
        }
        totals.merge(new MaterialKey(itemId, ""), stack.getCount(), Integer::sum);
        if (!(item instanceof BlockItem)) {
            return;
        }
//#if MC >= 12110
//$$         final TypedEntityData<BlockEntityType<?>> blockEntityData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
//$$         if (blockEntityData == null) {
//$$             return;
//$$         }
//$$         final NbtCompound blockEntityTag = blockEntityData.copyNbtWithoutId();
//$$         if (!blockEntityTag.contains("Items")) {
//$$             return;
//$$         }
//$$         blockEntityTag.getList("Items").ifPresent(items -> accumulateContainedItems(items, totals));
//#elseif MC >= 12005
//$$         final NbtComponent blockEntityData = stack.getComponents().getOrDefault(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.DEFAULT);
//$$         if (blockEntityData.isEmpty()) {
//$$             return;
//$$         }
//$$         final NbtCompound blockEntityTag = blockEntityData.copyNbt();
//$$         if (!blockEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
//$$             return;
//$$         }
//$$         accumulateContainedItems(blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE), totals);
//#else
        final NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) {
            return;
        }
        final NbtCompound blockEntityTag = nbt.getCompound("BlockEntityTag");
        if (!blockEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
            return;
        }
        accumulateContainedItems(blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE), totals);
//#endif
    }

    private void accumulateContainedItems(final NbtList itemsNbt, final Map<MaterialKey, Integer> totals) {
        for (int i = 0; i < itemsNbt.size(); i++) {
            final NbtCompound itemNbt = NbtHelper.getCompound(itemsNbt, i);
            if (itemNbt == null) {
                continue;
            }
            final String id = NbtHelper.getString(itemNbt, "id");
            if (id.isEmpty()) {
                continue;
            }
            final Optional<Identifier> itemId = IdentifierUtil.tryParse(id);
            if (!itemId.isPresent()) {
                logInvalidItemId("accumulateContainedItems", id);
                continue;
            }
            //#if MC >= 12110
            //$$ final Item item = Registries.ITEM.getEntry(itemId.get()).map(entry -> entry.value()).orElse(Items.AIR);
            //#elseif MC >= 12001
            //$$ final Item item = Registries.ITEM.get(itemId.get());
            //#else
            final Item item = Registry.ITEM.get(itemId.get());
            //#endif
            if (item == Items.AIR) {
                continue;
            }
            final int count = NbtHelper.getByte(itemNbt, "Count") & 0xFF;
            if (count <= 0) {
                continue;
            }
            totals.merge(new MaterialKey(itemId.get(), ""), count, Integer::sum);
            final NbtCompound tag = NbtHelper.getCompound(itemNbt, "tag");
            if (tag == null) {
                continue;
            }
            final NbtCompound blockEntityTag = NbtHelper.getCompound(tag, "BlockEntityTag");
            if (blockEntityTag == null) {
                continue;
            }
            final NbtList nestedItems = NbtHelper.getList(blockEntityTag, "Items");
            if (nestedItems != null) {
                accumulateContainedItems(nestedItems, totals);
            }
        }
    }

    private void logInvalidItemId(final String source, final String rawId) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Ignoring invalid item identifier '{}' while computing {}", rawId, source);
        }
    }

    private int accumulateFingerprint(final Iterable<ItemStack> stacks, final int seed) {
        int fingerprint = seed;
        if (stacks == null) {
            return fingerprint;
        }
        for (final ItemStack stack : stacks) {
            fingerprint = accumulateStackFingerprint(stack, fingerprint);
        }
        return fingerprint;
    }

    private int accumulateFingerprintFromStack(final ItemStack stack, final int seed) {
        return accumulateStackFingerprint(stack, seed);
    }

    private int accumulateStackFingerprint(final ItemStack stack, final int seed) {
        int fingerprint = seed;
        if (stack == null || stack.isEmpty()) {
            return 31 * fingerprint;
        }
        final Item item = stack.getItem();
        //#if MC >= 12001
        //$$ final Identifier itemId = item == Items.AIR ? null : Registries.ITEM.getId(item);
        //#else
        final Identifier itemId = item == Items.AIR ? null : Registry.ITEM.getId(item);
        //#endif
        fingerprint = 31 * fingerprint + (itemId == null ? 0 : itemId.hashCode());
        fingerprint = 31 * fingerprint + stack.getCount();
        if (!(item instanceof BlockItem)) {
            return fingerprint;
        }
//#if MC >= 12110
//$$         final TypedEntityData<BlockEntityType<?>> blockEntityData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
//$$         if (blockEntityData == null) {
//$$             return fingerprint;
//$$         }
//$$         final NbtCompound blockEntityTag = blockEntityData.copyNbtWithoutId();
//$$         if (!blockEntityTag.contains("Items")) {
//$$             return fingerprint;
//$$         }
//$$         final NbtList itemsList = blockEntityTag.getList("Items").orElse(null);
//$$         if (itemsList == null) {
//$$             return fingerprint;
//$$         }
//$$         return accumulateFingerprintFromNbt(itemsList, fingerprint);
//#elseif MC >= 12005
//$$         final NbtComponent blockEntityData = stack.getComponents().getOrDefault(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.DEFAULT);
//$$         if (blockEntityData.isEmpty()) {
//$$             return fingerprint;
//$$         }
//$$         final NbtCompound blockEntityTag = blockEntityData.copyNbt();
//$$         if (!blockEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
//$$             return fingerprint;
//$$         }
//$$         return accumulateFingerprintFromNbt(blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE), fingerprint);
//#else
        final NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) {
            return fingerprint;
        }
        final NbtCompound blockEntityTag = nbt.getCompound("BlockEntityTag");
        if (!blockEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
            return fingerprint;
        }
        return accumulateFingerprintFromNbt(blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE), fingerprint);
//#endif
    }

    private int accumulateFingerprintFromNbt(final NbtList itemsNbt, final int seed) {
        int fingerprint = seed;
        for (int i = 0; i < itemsNbt.size(); i++) {
            final NbtCompound itemNbt = NbtHelper.getCompound(itemsNbt, i);
            if (itemNbt == null) {
                continue;
            }
            final String id = NbtHelper.getString(itemNbt, "id");
            final Identifier itemId;
            if (id.isEmpty()) {
                itemId = null;
            } else {
                final Optional<Identifier> parsedId = IdentifierUtil.tryParse(id);
                if (!parsedId.isPresent()) {
                    logInvalidItemId("fingerprint", id);
                    continue;
                }
                itemId = parsedId.get();
            }
            final int count = NbtHelper.getByte(itemNbt, "Count") & 0xFF;
            fingerprint = 31 * fingerprint + (itemId == null ? 0 : itemId.hashCode());
            fingerprint = 31 * fingerprint + count;
            final NbtCompound tag = NbtHelper.getCompound(itemNbt, "tag");
            if (tag == null) {
                continue;
            }
            final NbtCompound blockEntityTag = NbtHelper.getCompound(tag, "BlockEntityTag");
            if (blockEntityTag == null) {
                continue;
            }
            final NbtList nestedItems = NbtHelper.getList(blockEntityTag, "Items");
            if (nestedItems != null) {
                fingerprint = accumulateFingerprintFromNbt(nestedItems, fingerprint);
            }
        }
        return fingerprint;
    }

    private String resolvePlayerName() {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        if (client.player != null && client.player.getGameProfile() != null) {
            return SyncmaticaUtil.getProfileName(client.player.getGameProfile());
        }
        if (client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return null;
    }

    // No HUD when client options or rows are missing.
    private boolean shouldRender() {
        return HudPreferences.isHudEnabled()
                && MinecraftClient.getInstance().options != null
                && !rows.isEmpty();
    }

//#if MC >= 12005
//$$     @Override
//$$     public void onHudRender(final DrawContext drawContext, final RenderTickCounter tickCounter) {
//$$         render(null, drawContext);
//$$     }
//#elseif MC >= 12001
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
        if (!HudPreferences.isHudEnabled()) {
            return;
        }
        observeInventoryFingerprint();
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
//#if MC >= 12111
//$$         RenderUtils.drawRect(GuiContext.fromGuiGraphics((DrawContext) drawContext), x, y, contentWidth + padding * 2, contentHeight + padding * 2, 0x80000000);
//#elseif MC >= 12110
//$$         RenderUtils.drawRect((DrawContext) drawContext, x, y, contentWidth + padding * 2, contentHeight + padding * 2, 0x80000000);
//#else
        RenderUtils.drawRect(x, y, contentWidth + padding * 2, contentHeight + padding * 2, 0x80000000);
//#endif
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
        private final MaterialKey key;
        private final ItemStack stack;
        private final String name;
        private int totalMissing;
        private int effectiveMissing;

        private Aggregate(final MaterialKey key, final ItemStack stack, final String name) {
            this.key = key;
            this.stack = stack.copy();
            this.name = name;
            this.effectiveMissing = 0;
        }

        private void addMissing(final int missing) {
            totalMissing += Math.max(0, missing);
        }

        private int applyInventoryOffset(final int inventoryCount) {
            effectiveMissing = Math.max(0, totalMissing - Math.max(0, inventoryCount));
            return effectiveMissing;
        }

        private int getEffectiveMissing() {
            return effectiveMissing;
        }

        private MaterialKey getKey() {
            return key;
        }
    }
}
