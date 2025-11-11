# Syncmatica_r Configuration Manual (2025-11)

**English | [中文](CONFIG_CN.md)**

---

Syncmatica_r keeps all runtime configuration inside a single `config.json`. The loader merges defaults for every
service section on startup and rewrites the file whenever it has to insert missing keys or recover from invalid JSON.
Because of that, always edit the file while the game/server is stopped; changes are only read during the next startup.

## Location and Lifecycle

- **Dedicated server & standalone client:** `config/syncmatica_r/config.json` inside the game directory. The folder is
  created automatically if it does not exist.
- **Integrated (single-player) server:** `<world-folder>/syncmatica_r/config.json` so each save can carry its own quota
  and material policy.
- **Legacy fallback:** If `config/syncmatica_r` does not exist but `config/syncmatica` does, the loader will consume the
  legacy file once. As soon as the modern folder gets created, only `syncmatica_r` is consulted.
- **Error recovery:** When the JSON cannot be parsed or a section is missing, Syncmatica_r rewrites the entire file with
  the last known good configuration plus defaults. Unknown keys are preserved, but their order may change.

## JSON Structure Overview

The root object is a flat collection of sections. Servers use all three blocks shown below, while clients only care
about `debug`.

```json
{
  "quota": {
    "enabled": false,
    "limit": 40000000
  },
  "materials": {
    "enabled": true,
    "scan_interval": 200,
    "scan_blocks_per_tick": 2048,
    "include_container_contents": false,
    "max_schematic_megabytes": 64,
    "max_schematic_blocks": 8000000
  },
  "debug": {
    "doPackageLogging": false
  }
}
```

Clients can safely delete the `quota` and `materials` sections; the loader will re-create them when the game later runs
as a server.

## `quota` — Server Upload Control

| Key     | Default   | Range            | Meaning |
|---------|-----------|------------------|---------|
| enabled | `false`   | `true` / `false` | When `true`, every upload/download exchange is tracked per player. |
| limit   | `40000000`| ≥ 0 (bytes)      | Total bytes a player may transfer during the current server uptime. |

- Limits apply to each `ExchangeTarget` (player identity). The counter resets when the server shuts down because the
  service clears its in-memory `progress` map during `shutdown()`.
- Set `enabled` to `false` when bandwidth policing is not needed; the code short-circuits and never records quota data.

## `materials` — Server Material Tracking

This block is only consumed when the MaterialService is present (dedicated or integrated servers). Disabling the block
removes material progress/claims from the advertised feature set.

| Key                        | Default  | Enforced Minimum | Purpose |
|----------------------------|----------|------------------|---------|
| enabled                    | `true`   | —                | Master toggle for material aggregation and syncing. |
| scan_interval              | `200`    | `20` ticks       | How often the default stocking area is rescanned when idle. |
| scan_blocks_per_tick       | `2048`   | `64` blocks      | Work budget for each active scan. Higher values finish long schematics faster but cost more CPU per tick. |
| include_container_contents | `false`  | —                | When `true`, chests/shulkers inside the schematic contribute their inventories to material counts. |
| max_schematic_megabytes    | `64`     | `1` MB           | Litematic files larger than this are skipped during requirement extraction to avoid I/O abuse. |
| max_schematic_blocks       | `8000000`| `1,000,000`      | Upper bound passed to the extractor to avoid decoding unreasonably large block volumes. |

Operational notes:

- `scan_blocks_per_tick` and `scan_interval` work together. Lower them if the server stutters; raise them when scans
  take too long.
- Both `max_schematic_*` guards prevent players from forcing multi-gigabyte parses. When tripped, the server simply
  logs a warning and skips requirement extraction for that placement.
- Changing these settings after the service started requires a full server restart.

## `debug` — Packet Logging (Client + Server)

| Key              | Default | Meaning |
|------------------|---------|---------|
| doPackageLogging | `false` | When enabled, every Syncmatica_r packet send/receive is logged via Log4j at `INFO`. |

Keep this switch off on production servers; it is noisy and exposes packet metadata in plain logs. Toggle it only while
diagnosing protocol problems.

## Troubleshooting

- File reset after launch? The JSON syntax was invalid. Reapply your edits on top of the regenerated file.
- Options disappearing? The loader always adds missing keys with defaults, so remove-only edits will be restored on the
  next run. Leave the key present and change its value instead.
- Materials UI still visible after setting `enabled` to `false`? Make sure you edited the server’s config (not the
  client) and restarted the server so the new flag propagates into the advertised feature set.
