# Syncmatica_r Configuration

**English | [中文](CONFIG_CN.md)**

All runtime configuration is stored in a single `config.json`. On startup the loader
fills in missing keys with defaults; if the JSON cannot be parsed, the file is rebuilt
from the last known good state, and unknown keys are preserved. Manual edits take
effect on the next restart; most server settings can also be changed live with
commands.

## Location

- **Dedicated server and standalone client:** `config/syncmatica_r/config.json`
- **Single-player worlds:** `<world>/syncmatica_r/config.json`, so each save carries its
  own quota and material policy
- **Legacy layout:** if `config/syncmatica_r` does not exist but the old
  `config/syncmatica` does, the legacy file is consumed once; afterwards only
  `syncmatica_r` is read

## Changing settings live

```text
/syncmatica_r config list [section]
/syncmatica_r config get <section> <key>
/syncmatica_r config set <section> <key> <value>
/syncmatica_r config reset <section> <key>
```

`set` validates the value, applies it immediately, and writes it to `config.json`;
invalid values are rejected rather than clamped. Sections, keys, and booleans support
tab completion. The commands cover the server sections below and require
`syncmatica_r.config`, with vanilla permission level 2 as the fallback. Client
preferences are not included.

## Structure

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
    "allow_owner_stocking_area_management": true,
    "max_schematic_megabytes": 64,
    "max_schematic_blocks": 8000000,
    "max_stocking_area_blocks": 1000000
  },
  "build": {
    "enabled": true,
    "completion_enabled": true,
    "scan_blocks_per_tick": 4096,
    "scan_interval": 1200,
    "full_rescan_interval": 36000
  },
  "web": {
    "enabled": false,
    "bind_address": "127.0.0.1",
    "port": 8080,
    "session_hours": 24,
    "secure_cookie": false,
    "max_request_bytes": 65536,
    "request_timeout_seconds": 10
  },
  "debug": {
    "doPackageLogging": false
  }
}
```

Servers use every section; a client requires only `debug` and the two update flags at
the root. Client installations may delete `quota`, `materials`, `build`, and `web`: the
loader recreates them if the game later runs as a server.

## `quota` — Upload Limit (Server)

| Key     | Default    | Range            | Meaning |
|---------|------------|------------------|---------|
| enabled | `false`    | `true` / `false` | Track schematic uploads received from each player. |
| limit   | `40000000` | ≥ 0 (bytes)      | Total schematic bytes a player may upload during the current server uptime. |

- The counter is recorded per player identity and includes every accepted upload,
  including transfers that subsequently fail. Reconnecting does not reset it; a server
  restart does.
- With `enabled` set to `false`, no quota data is recorded. Regardless of this setting,
  each file, packet, concurrent exchange, and exchange lifetime remains capped by hard
  limits.

## `materials` — Material Tracking (Server)

| Key | Default | Range | Purpose |
|-----|---------|-------|---------|
| enabled | `true` | — | Master toggle for material aggregation and syncing. |
| scan_interval | `200` | ≥ `20` ticks | How often the default stocking area is rescanned while idle. |
| scan_blocks_per_tick | `2048` | `64`–`65,536` blocks | Shared per-tick budget for incremental scans. |
| include_container_contents | `false` | — | Count the inventories of chests and shulkers inside the schematic. |
| allow_owner_stocking_area_management | `true` | — | Let placement owners set their own placement's stocking area through commands or the GUI. When `false`, both paths require `syncmatica_r.manage`. |
| max_schematic_megabytes | `64` | `1`–`64` MB | Maximum compressed transfer size and decompressed NBT allocation. |
| max_schematic_blocks | `8000000` | `1,000,000`–`64,000,000` | Maximum decoded schematic block volume. |
| max_stocking_area_blocks | `1000000` | `1,024`–`64,000,000` | Maximum volume accepted for a stocking area. |

- Lower `scan_blocks_per_tick` and raise `scan_interval` if scans cause server stutter;
  raise the budget if scans take too long.
- Schematic extraction runs on a background worker; transfer size and decoded block
  volume are both validated before results are applied.
- Changing extraction limits or `include_container_contents` re-extracts every shared
  schematic; other keys take effect immediately.

## `build` — Build Management (Server)

Build management reads the schematic itself, so it operates independently of material
tracking: either feature can be disabled without affecting the other. Disabling this
section hides region claims from every client.

| Key | Default | Range | Purpose |
|-----|---------|-------|---------|
| enabled | `true` | — | Master toggle for region claims and everything below. |
| completion_enabled | `true` | — | Measure how much of each region is built. Claims keep working without it. |
| scan_blocks_per_tick | `4096` | `64`–`65,536` blocks | Per-tick budget of the completion scan. |
| scan_interval | `1200` | ≥ `100` ticks | How often columns nobody has counted yet are retried. |
| full_rescan_interval | `36000` | ≥ `1200` ticks, or `0` | How often every column is re-counted from scratch; `0` disables the sweep. |

- Scanning is driven by block changes. A placement nobody touches is not scanned at
  all, and a placement under construction only re-counts the chunk columns that
  changed, so progress updates within one to two ticks of a block being placed. An idle
  schematic incurs no cost regardless of size.
- One placement is scanned at a time: a large schematic results in a longer scan, not a
  server stall.
- Columns outside the loaded area retain their last count, since nothing can be built
  inside an unloaded chunk. A region far larger than the area players keep loaded is
  still measured, incrementally over time. Columns never loaded count as unbuilt;
  `scan_interval` controls how often the server retries them.
- Edits that bypass the game, such as a bulk editor writing chunk sections directly or
  offline region-file edits, are not reported and leave stale counts behind.
  `full_rescan_interval` is the sweep that recovers from them: increase it when nothing
  on the server edits in that manner, or set `0` to disable it, leaving
  `/syncmatica_r <project> rescanBuild` as the manual remedy.
- Completion compares block identity, not full block state: a region built with every
  stair facing the wrong way still counts as complete, consistent with the material
  list's counting rule.
- Claims are keyed by region name, so they survive re-shares and restarts. A region
  removed from the schematic loses its claim with it.
- Counts are stored in the world in which they were measured, under
  `<world>/syncmatica_r/build_scan/`; restoring a backup restores the matching counts.
- Two switches belong to the player rather than the operator and reside in the client
  configuration (`config/syncmatica_r/client.json`, also editable in the MaLiLib
  settings screen): the foreign-build warning (`General.warnOnForeignPlacement`, enabled
  by default) and sub-region visibility following claims (`General.followClaims`,
  disabled by default).
- Changes to this section require a server restart.

## `web` — Optional Web Interface (Server)

The web service is disabled by default. Restart the server after changing this
section.

| Key | Default | Accepted value | Purpose |
|-----|---------|----------------|---------|
| enabled | `false` | `true` / `false` | Start the web service with the server. |
| bind_address | `127.0.0.1` | Non-blank resolvable host name or IP | Interface the HTTP listener accepts connections on. |
| port | `8080` | `1`–`65,535` | HTTP listener port. |
| session_hours | `24` | `1`–`8,760` hours | Browser session lifetime. |
| secure_cookie | `false` | `true` / `false` | Add the `Secure` flag to the session cookie; enable when users connect over HTTPS. |
| max_request_bytes | `65536` | `1,024`–`1,048,576` bytes | Maximum accepted JSON request body. |
| request_timeout_seconds | `10` | `1`–`120` seconds | Timeout for authentication and Minecraft-server operations. |

After enabling it, players set a password with
`/syncmatica_r web setpassword <password>` and can disable it again with
`/syncmatica_r web disable`. Credentials persist across restarts in
`config/syncmatica_r/web-credentials.json` (single-player:
`<world>/syncmatica_r/web-credentials.json`). Sessions are held in memory, so a server
restart signs browsers out; passwords remain valid.

When serving over HTTPS, set `secure_cookie` to `true`. Behind a reverse proxy, keep the
default `127.0.0.1` bind and terminate HTTPS at the proxy; change `bind_address` only
when direct access from another host is required.

The site supports viewing shared projects, material and build progress, claims, and
project-specific stocking areas, under the same server permissions as the in-game
operations. It does not provide project deletion, schematic upload/download, default
stocking-area management, configuration changes, or manual rescans.

## Limits and client messages

When a limit blocks an operation, the server reports which limit was involved instead
of failing silently, including the values: for example `96.0 MB > 64.0 MB`.

| Situation | Where it shows | Configuration involved |
|-----------|-----------------|-------------------------|
| Shared schematic exceeds the accepted transfer size | Error message on the sharing client | `materials.max_schematic_megabytes` |
| Shared schematic exhausts the player's upload quota | Error message on the sharing client | `quota.limit` |
| Stored schematic is larger than the current limit, so it cannot be served | Error message on the downloading client; the pending download is cancelled instead of timing out | `materials.max_schematic_megabytes` |
| Material list cannot be built | Reason line when the material list screen opens, hover text on the material button, and a one-shot message to the placement owner | `materials.enabled`, `materials.max_schematic_megabytes`, `materials.max_schematic_blocks` |

Lowering `max_schematic_megabytes` or `max_schematic_blocks` below the size of
already-shared schematics is the common cause: those placements retain their metadata
but stop serving downloads and material lists, and affected clients are informed which
limit rejected them.

## `debug` — Packet Logging (Client + Server)

| Key | Default | Meaning |
|-----|---------|---------|
| doPackageLogging | `false` | Log every Syncmatica_r packet sent or received via Log4j at `INFO`. Enable only while diagnosing protocol issues; it produces substantial log output. |

## Client update flags

`checkupdate` and `check_pre_release` reside at the root of the configuration object and
apply only on the client.

| Key | Default | Meaning |
|-----|---------|---------|
| `checkupdate` | `true` | Master toggle for the GitHub release check. When `false`, the mod never contacts GitHub and no update toast is shown. |
| `check_pre_release` | `false` | When `true`, stable builds also treat newer pre-release tags as updates. When `false`, stable builds consider only newer stable tags; pre-release builds always see both. |

## Network permissions

- `syncmatica_r.share`: upload new placements. Allowed by default.
- `syncmatica_r.claim`: claim existing material requirements. Allowed by default.
- `syncmatica_r.build.claim`: take responsibility for a sub-region. Allowed by default,
  and separate from `syncmatica_r.claim` so that material gathering and region building
  can be assigned to different players.
- `syncmatica_r.manage`: modify or delete placements owned by another player, manage any
  placement's stocking area, and manage the default stocking area. Permission level 2.
- Placement owners can always modify or delete their own placements.

## Troubleshooting

- Configuration file was reset after launch: the JSON was invalid. Reapply the edits on
  the regenerated file.
- A removed option reappeared: the loader restores missing keys with defaults. Keep the
  key in place and change its value instead.
- Materials UI remains visible after setting `enabled` to `false`: verify that the
  server's configuration was edited (not the client's) and that the server was
  restarted so the change reaches connected clients.
