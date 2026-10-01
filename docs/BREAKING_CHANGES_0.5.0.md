# Syncmatica_r 0.5.0 Migration Guide

[中文](BREAKING_CHANGES_0.5.0_CN.md)

Syncmatica_r 0.5.0 replaces per-placement coordinate stocking areas with a named,
server-wide stocking area registry. Server owners should back up the world,
`config/syncmatica_r`, and the schematic storage before upgrading. Update the mod on
both the server and the clients.

## What server owners should know

- **The coordinate commands are gone.** `/syncmatica_r <name> setStockingarea <x1> <y1>
  <z1> <x2> <y2> <z2>` and `/syncmatica_r default setStockingarea …` no longer exist.
  Areas are now created and edited through the named registry:
  `/syncmatica_r stockingArea list|new|edit|delete`, and a placement selects its area
  with `/syncmatica_r <name> setStockingarea <area_name>` or
  `/syncmatica_r <name> clearStockingarea`.
- **The legacy `set_stocking_area` packet is rejected.** A 0.4.x client
  connected to a 0.5.0 server cannot push a selection-based stocking area; the server
  answers with an in-game error message asking the player to update. Such clients are
  also warned right after joining that stocking areas are read-only for them, while
  still seeing each placement's current area (the server sends the resolved
  coordinates). Update the clients to manage areas.
- **Permission model.** Creating areas requires the stocking-area command permission
  (`syncmatica_r.command`, vanilla level 2 by default). Editing or deleting an area is
  limited to its creator or admins. Binding an area to a placement follows
  `allow_owner_stocking_area_management` as before.

## Storage changes

- **Placement JSON switches to `stockingAreaRef`.** The old per-placement
  `stockingArea` coordinate object is migrated on first load: each one becomes a named
  registry entry (named after the placement) and the placement stores the area's UUID.
  The old key is no longer written. Downgrading to 0.4.x afterwards loses the areas:
  0.4.x does not read `stockingAreaRef` or the registry.
- **The server meta file gains `stockingAreas`.** `meta.json` now stores the named
  registry under a `stockingAreas` object. A legacy `defaultStockingArea` coordinate
  entry is migrated into the reserved default area of the registry.

## Upgrade checklist

1. Stop the server and back up the world, `config/syncmatica_r`, and the schematic
   storage (including `meta.json`).
2. Install Syncmatica_r 0.5.0 on the server and clients.
3. Start the server once so legacy per-placement and default areas migrate into the
   registry, then verify with `/syncmatica_r stockingArea list`.
4. Tell players who used the old coordinate command about the new
   `stockingArea new`/`edit` commands and binding by name.
