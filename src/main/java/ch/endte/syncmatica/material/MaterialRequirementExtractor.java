package ch.endte.syncmatica.material;

import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to translate litematic files into Syncmatica material requirements.
 */
public final class MaterialRequirementExtractor {

    private static final Logger LOGGER = LogManager.getLogger(MaterialRequirementExtractor.class);

    private MaterialRequirementExtractor() {
    }

    public static Map<MaterialKey, Integer> extract(final File litematicFile) {
        final Map<MaterialKey, Integer> requirements = new HashMap<>();
        if (litematicFile == null || !litematicFile.isFile()) {
            return requirements;
        }
        try {
            final LitematicaSchematic schematic = LitematicaSchematic.createFromFile(litematicFile, litematicFile.getName());
            if (schematic == null) {
                return requirements;
            }
            final MaterialListSchematic materialList = new MaterialListSchematic(schematic, true);
            materialList.reCreateMaterialList();
            for (final MaterialListEntry entry : materialList.getMaterialsAll()) {
                final ItemStack stack = entry.getStack();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                final Identifier itemId = Registry.ITEM.getId(stack.getItem());
                if (itemId == null) {
                    continue;
                }
                final MaterialKey key = new MaterialKey(itemId, "");
                requirements.merge(key, entry.getCountTotal(), Integer::sum);
            }
        } catch (final Exception exception) {
            LOGGER.warn("Failed to extract material requirements from {}", litematicFile, exception);
        }
        return requirements;
    }
}
