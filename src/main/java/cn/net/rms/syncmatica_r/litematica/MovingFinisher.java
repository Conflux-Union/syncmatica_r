package cn.net.rms.syncmatica_r.litematica;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

public interface MovingFinisher {
    void onFinishedMoving(String subRegionName, SchematicPlacementManager manager);
}
