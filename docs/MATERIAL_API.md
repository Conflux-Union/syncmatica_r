# Material Integration Interface

Syncmatica_r exposes a read-only client interface for mods that consume claimed
material requirements. It does not replace Litematica's active material list and
does not perform inventory clicks.

## Interface

```java
import cn.net.rms.syncmatica_r.api.MaterialRequirement;
import cn.net.rms.syncmatica_r.api.SyncmaticaMaterialApi;

List<MaterialRequirement> requirements =
        SyncmaticaMaterialApi.getClaimedMaterialRequirements(playerUuid);
```

The method returns an immutable snapshot with one entry per item id and variant.
Requirements from multiple shared placements are aggregated and sorted by item id,
then variant. Aggregated amounts are capped at `Integer.MAX_VALUE`.

Each `MaterialRequirement` contains:

- `itemId()`: the namespaced item id, such as `minecraft:stone`;
- `variant()`: Syncmatica's variant discriminator, currently empty for normal
  schematic materials;
- `missingAmount()`: the amount still absent from the server-managed stocking
  areas.

## Contract

- Call the interface on the Minecraft client thread.
- Pass the UUID from the local player's game profile.
- An empty list means that the client context is unavailable or that the player
  has no outstanding claimed materials.
- The snapshot includes only unfinished entries explicitly claimed by the supplied
  player UUID.
- `missingAmount()` does not subtract items already present in the player's
  inventory. Consumers such as automatic collectors should apply their own live
  inventory count exactly once.
- The interface has no side effects on Litematica state, HUD renderers, Syncmatica
  claims, containers, or player inventory.

Consumers should keep Syncmatica_r optional and load their compatibility code only
when Fabric Loader reports the `syncmatica_r` mod id as present.
