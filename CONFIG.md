# Syncmatica_r Configuration Manual (2026-03)

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
about `debug`. Two additional top-level keys control the optional GitHub update check on the client.

```json
{
  "checkupdate": true,
  "check_pre_release": false,
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
    "max_schematic_blocks": 8000000,
    "max_stocking_area_blocks": 1000000
  },
  "debug": {
    "doPackageLogging": false
  }
}
```

Clients can safely delete the `quota` and `materials` sections; the loader will re-create them when the game later runs
as a server. The two top-level update keys are ignored on servers.

## `quota` — Server Upload Control

| Key     | Default   | Range            | Meaning |
|---------|-----------|------------------|---------|
| enabled | `false`   | `true` / `false` | When `true`, schematic uploads received by the server are tracked per player. |
| limit   | `40000000`| ≥ 0 (bytes)      | Total schematic bytes a player may upload during the current server uptime. |

- Limits apply to each player identity and count every accepted upload chunk, including transfers that later fail.
  Disconnecting and reconnecting does not reset the counter; it resets when the server shuts down.
- Set `enabled` to `false` when bandwidth policing is not needed; the code short-circuits and never records quota data.
- Independent hard limits still cap each file, each packet, concurrent exchanges, and exchange lifetime when quota
  accounting is disabled.

## `materials` — Server Material Tracking

This block is only consumed when the MaterialService is present (dedicated or integrated servers). Disabling the block
removes material progress/claims from the advertised feature set.

| Key                        | Default  | Enforced Minimum | Purpose |
|----------------------------|----------|------------------|---------|
| enabled                    | `true`   | —                | Master toggle for material aggregation and syncing. |
| scan_interval              | `200`    | `20` ticks       | How often the default stocking area is rescanned when idle. |
| scan_blocks_per_tick       | `2048`   | `64`–`65,536` blocks | Shared work budget for incremental scans. |
| include_container_contents | `false`  | —                | When `true`, chests/shulkers inside the schematic contribute their inventories to material counts. |
| max_schematic_megabytes    | `64`     | `1`–`64` MB      | Maximum compressed transfer size and decompressed NBT allocation. |
| max_schematic_blocks       | `8000000`| `1,000,000`–`64,000,000` | Maximum decoded schematic block volume. |
| max_stocking_area_blocks   | `1000000`| `1,024`–`64,000,000` | Maximum volume accepted for a stocking area. |

Operational notes:

- `scan_blocks_per_tick` and `scan_interval` work together. Lower them if the server stutters; raise them when scans
  take too long.
- Schematic extraction runs on a bounded background worker. NBT allocation and block volume are both checked before
  results are applied on the server thread; nested container traversal stops after 10 levels.
- Stocking area commands schedule an incremental scan; they no longer scan the entire cuboid during command execution.
- Changing these settings after the service started requires a full server restart.

## Limit Diagnostics on the Client

When a limit above blocks an operation, the server names the cause instead of failing silently. Clients that announce
the `LIMIT_REPORT` feature also receive the offending and configured values as a language-neutral detail such as
`96.0 MB > 64.0 MB`; older clients still get the plain message and the historic wire layout.

| Situation | Where it shows | Configuration involved |
|-----------|----------------|------------------------|
| Shared schematic exceeds the accepted transfer size | Error message on the sharing client | `materials.max_schematic_megabytes` |
| Shared schematic exhausts the player's upload quota | Error message on the sharing client | `quota.limit` |
| Stored schematic is larger than the current limit, so it cannot be served | Error message on the downloading client; the pending download is cancelled instead of timing out | `materials.max_schematic_megabytes` |
| Material list cannot be built | Reason line when the material list screen opens, hover text on the material button, and a one-shot message to the placement owner | `materials.enabled`, `materials.max_schematic_megabytes`, `materials.max_schematic_blocks` |

Lowering `max_schematic_megabytes` or `max_schematic_blocks` below the size of already-shared schematics is the common
cause: existing placements keep their metadata but stop serving downloads and material lists, and every affected client
now says which limit rejected them.

## `debug` — Packet Logging (Client + Server)

| Key              | Default | Meaning |
|------------------|---------|---------|
| doPackageLogging | `false` | When enabled, every Syncmatica_r packet send/receive is logged via Log4j at `INFO`. |

## Client update flags — `checkupdate` and `check_pre_release`

These keys live at the root of the configuration object and are honored only on the client.

| Key                 | Default | Meaning |
|---------------------|---------|---------|
| `checkupdate`       | `true`  | Master toggle for the GitHub release check. When `false`, the mod never contacts GitHub and no update toast is shown. |
| `check_pre_release` | `false` | When `true`, stable builds may treat newer pre-release tags as valid updates. When `false`, stable builds only consider newer stable tags; pre-release builds always see both newer pre-release and stable tags as updates. |

Keep this switch off on production servers; it is noisy and exposes packet metadata in plain logs. Toggle it only while
diagnosing protocol problems.

## Network permissions

- `syncmatica_r.share`: upload new placements; allowed by default when no provider handles the node.
- `syncmatica_r.claim`: claim existing material requirements; allowed by default.
- `syncmatica_r.manage`: modify or delete placements owned by another player; defaults to permission level 2.
- Placement owners can always modify or delete their own placements.

## Troubleshooting

- File reset after launch? The JSON syntax was invalid. Reapply your edits on top of the regenerated file.
- Options disappearing? The loader always adds missing keys with defaults, so remove-only edits will be restored on the
  next run. Leave the key present and change its value instead.
- Materials UI still visible after setting `enabled` to `false`? Make sure you edited the server’s config (not the
  client) and restarted the server so the new flag propagates into the advertised feature set.
