package cn.net.rms.syncmatica_r.compat.tweakermore;

import cn.net.rms.syncmatica_r.api.MaterialRequirement;
import cn.net.rms.syncmatica_r.api.SyncmaticaMaterialApi;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
//#if MC >= 260100
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//#elseif MC >= 12001
//$$ import net.minecraft.registry.Registries;
//#else
import net.minecraft.util.registry.Registry;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SyncmaticaMaterialListAdapter extends MaterialListBase {
    private static final Logger LOGGER = LogManager.getLogger(SyncmaticaMaterialListAdapter.class);

    private SyncmaticaMaterialListAdapter(final List<MaterialListEntry> entries) {
        setMaterialListEntries(entries);
    }

    public static MaterialListBase create(final UUID playerId) {
        return new SyncmaticaMaterialListAdapter(toEntries(
                SyncmaticaMaterialApi.getClaimedMaterialRequirements(playerId)));
    }

    static List<MaterialListEntry> toEntries(final List<MaterialRequirement> requirements) {
        final List<MaterialListEntry> entries = new ArrayList<>();
        for (final MaterialRequirement requirement : requirements) {
            if (!requirement.variant().isEmpty()) {
                LOGGER.debug("Skipping unsupported Syncmatica_r material variant '{}' for {}",
                        requirement.variant(), requirement.itemId());
                continue;
            }
            final ItemStack stack = resolveStack(requirement.itemId());
            if (stack.isEmpty()) {
                LOGGER.debug("Skipping unknown Syncmatica_r material item {}", requirement.itemId());
                continue;
            }
            entries.add(new MaterialListEntry(
                    stack,
                    requirement.missingAmount(),
                    requirement.missingAmount(),
                    0,
                    0));
        }
        return entries;
    }

    private static ItemStack resolveStack(final String itemId) {
        // Match the existing material render paths. A direct registry lookup from an
        // Identifier local is incorrectly remapped to getEntry() for Minecraft 1.21.4.
        final MaterialKey key = IdentifierUtil.tryParse(itemId)
                .map(id -> new MaterialKey(id, ""))
                .orElse(null);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        //#if MC >= 260100
        //$$ final Item item = BuiltInRegistries.ITEM.getValue(key.itemId());
        //#elseif MC >= 12001
        //$$ final Item item = Registries.ITEM.get(key.itemId());
        //#else
        final Item item = Registry.ITEM.get(key.itemId());
        //#endif
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public String getName() {
        return "Syncmatica_r";
    }

    @Override
    public String getTitle() {
        return "Syncmatica_r";
    }

    @Override
    public void reCreateMaterialList() {
    }
}
