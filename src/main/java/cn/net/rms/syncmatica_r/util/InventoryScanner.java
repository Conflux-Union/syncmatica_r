package cn.net.rms.syncmatica_r.util;

import cn.net.rms.syncmatica_r.material.MaterialKey;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
//#if MC >= 12001
//$$ import net.minecraft.registry.Registries;
//#else
import net.minecraft.util.registry.Registry;
//#endif
//#if MC >= 12101
//$$ import net.minecraft.component.DataComponentTypes;
//$$ import net.minecraft.component.type.ContainerComponent;
//#elseif MC >= 12005
//$$ import net.minecraft.component.DataComponentTypes;
//$$ import net.minecraft.component.type.NbtComponent;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * Utility class for scanning inventories and accumulating material counts.
 * Handles nested containers (shulker boxes, etc.) with depth limiting.
 */
public final class InventoryScanner {
    private static final int MAX_SHULKER_NESTING_DEPTH = 10;
    private static final Logger LOGGER = LogManager.getLogger(InventoryScanner.class);

    private InventoryScanner() {
    }

    /**
     * Scans a single ItemStack and accumulates material counts.
     * Handles nested containers recursively.
     *
     * @param stack  the ItemStack to scan
     * @param totals the map to accumulate material counts into
     */
    public static void scanItemStack(final ItemStack stack, final Map<MaterialKey, Integer> totals) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

//#if MC >= 12001
//$$         final Identifier itemId = Registries.ITEM.getId(stack.getItem());
//#else
        final Identifier itemId = Registry.ITEM.getId(stack.getItem());
//#endif
        boolean hasShulkerContents = false;

        if (stack.getItem() instanceof BlockItem) {
//#if MC >= 12101
//$$             final ContainerComponent container = stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT);
//$$             if (!container.equals(ContainerComponent.DEFAULT)) {
//$$                 hasShulkerContents = true;
//$$                 for (final ItemStack item : container.iterateNonEmpty()) {
//$$                     final Identifier nestedItemId = Registries.ITEM.getId(item.getItem());
//$$                     final MaterialKey nestedKey = new MaterialKey(nestedItemId, "");
//$$                     totals.merge(nestedKey, item.getCount(), Integer::sum);
//$$                 }
//$$             }
//#elseif MC >= 12005
//$$             final NbtComponent blockEntityData = stack.getComponents().getOrDefault(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.DEFAULT);
//$$             if (!blockEntityData.isEmpty()) {
//$$                 final NbtCompound blockEntityTag = blockEntityData.copyNbt();
//$$                 final NbtList items = NbtHelper.getList(blockEntityTag, "Items");
//$$                 if (items != null && items.size() > 0) {
//$$                     hasShulkerContents = true;
//$$                     scanShulkerBoxContents(items, totals, 0);
//$$                 }
//$$             }
//#else
            final NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.contains("BlockEntityTag")) {
                final NbtCompound blockEntityTag = nbt.getCompound("BlockEntityTag");
                if (blockEntityTag.contains("Items")) {
                    final NbtList items = blockEntityTag.getList("Items", 10);
                    if (items != null && items.size() > 0) {
                        hasShulkerContents = true;
                        scanShulkerBoxContents(items, totals, 0);
                    }
                }
            }
//#endif
        }

        if (!hasShulkerContents) {
            final MaterialKey key = new MaterialKey(itemId, "");
            totals.merge(key, stack.getCount(), Integer::sum);
        }
    }

    /**
     * Recursively scans shulker box contents from NBT data.
     *
     * @param itemsNbt the NBT list containing items
     * @param totals   the map to accumulate material counts into
     * @param depth    current nesting depth (for recursion limiting)
     */
    public static void scanShulkerBoxContents(final NbtList itemsNbt, final Map<MaterialKey, Integer> totals, final int depth) {
        if (depth > MAX_SHULKER_NESTING_DEPTH) {
            LOGGER.warn("Shulker box nesting depth exceeded limit ({}), skipping further scanning", depth);
            return;
        }

        for (int i = 0; i < itemsNbt.size(); i++) {
            final NbtCompound itemNbt = NbtHelper.getCompound(itemsNbt, i);
            if (itemNbt == null) {
                continue;
            }
            final String rawId = NbtHelper.getString(itemNbt, "id");
            if (rawId.isEmpty()) {
                continue;
            }
            final Optional<Identifier> itemId = IdentifierUtil.tryParse(rawId);
            if (!itemId.isPresent()) {
                continue;
            }
            final int count = NbtHelper.getByte(itemNbt, "Count") & 0xFF;
            if (count <= 0) {
                continue;
            }

            final NbtCompound tag = NbtHelper.getCompound(itemNbt, "tag");
            boolean hasNestedContents = false;
            if (tag != null) {
                final NbtCompound blockEntityTag = NbtHelper.getCompound(tag, "BlockEntityTag");
                if (blockEntityTag != null) {
                    final NbtList nestedItems = NbtHelper.getList(blockEntityTag, "Items");
                    if (nestedItems != null && nestedItems.size() > 0) {
                        hasNestedContents = true;
                        scanShulkerBoxContents(nestedItems, totals, depth + 1);
                    }
                }
            }

            if (!hasNestedContents) {
                final MaterialKey itemKey = new MaterialKey(itemId.get(), "");
                totals.merge(itemKey, count, Integer::sum);
            }
        }
    }
}
