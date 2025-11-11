package cn.net.rms.syncmatica_r.litematica_mixin;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SchematicPlacementManager.class)
public interface MixinSchematicPlacementManager {
    @Invoker(value = "onPrePlacementChange", remap = false)
    void preSubregionChange(SchematicPlacement schematicPlacement);
}
