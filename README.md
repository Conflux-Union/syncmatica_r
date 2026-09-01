# Syncmatica Revolution

**[English]** | [中文](README_CN.md)

> **0.4.0 breaking release:** Server owners must review the
> [migration guide](docs/BREAKING_CHANGES_0.4.0.md), especially the new placement management permission.

> **Project Origin**: Enhanced fork of [Syncmatica](https://github.com/End-Tech/syncmatica) with collaborative material tracking. Maintained at [RMS-Server/syncmatica_r](https://github.com/RMS-Server/syncmatica_r).

---

## Features

### Core: Schematic Sharing

Syncmatica_r integrates with Litematica to share schematics and their placements across a server:

- **Upload & Download**: Share your Litematica placements with the server; download others' shared placements
- **Placement Sync**: Position, rotation, and other placement data sync automatically to all players
- **Lock/Unlock Workflow**: Unlock a placement to modify it locally, then lock to share changes with everyone
- **Dimension Awareness**: Placements only load when you're in the same dimension

### Material Progress Tracking

A real-time coordination system for collaborative building projects:

- **Requirement Extraction**: Server automatically scans schematic material needs and syncs to all players
- **Claim System**: Players can claim specific materials to prevent duplicate collection
- **Stocking Area Scan**: Server scans configured container regions to track available inventory
- **Live HUD Overlay**: Client-side floating window displays material progress with customizable scale
- **GUI Dashboard**: Access via the **Material Collections** button to coordinate team resource collection
- **Sorting & Filtering**: Sort by missing count or item name; hide completed items

### Build Management

Divides a large build between players, so the assignment lives in the game rather than in a spreadsheet:

- **Region Claims**: Each sub-region of a shared schematic can be taken by one player, visible to everyone
- **Completion Tracking**: The server measures how much of each region is already built and reports it live
- **Foreign Build Warning**: Building inside a region somebody else took warns you, and never cancels the placement
- **GUI Dashboard**: Access via the **Build Management** button or a configurable hotkey — schematics first, then the
  regions of the one picked. Regions are listed in numeric order, with completed ones at the bottom
- **Claim-Following Visibility**: Optionally let claiming a region show that Litematica sub-region, and dropping or
  finishing it hide the sub-region again. Off by default; regions claimed by others are never touched
- **Independent of Materials**: Works whether or not material tracking is enabled

---

## Installation

### Client Requirements

1. Fabric Loader
2. [Litematica](https://masa.dy.fi/mcmods/client_mods/) (required)
3. [MaLiLib](https://masa.dy.fi/mcmods/client_mods/) (required by Litematica)
4. Syncmatica_r mod file

Place all mod files in the `mods` folder.

### Server Requirements

1. Fabric Loader
2. Syncmatica_r mod file

No additional dependencies required on the server side.

### Configuration

After first run, configuration files are created at:

| File | Location | Purpose |
|------|----------|---------|
| Main config | `config/syncmatica_r/config.json` | Server quota, material tracking and build management settings |
| HUD settings | `config/syncmatica_r/hud_settings.json` | Client HUD scale and enable toggle |
| Material list | `config/syncmatica_r/material_list_settings.json` | Client sort mode and filter preferences |
| Build warnings | `config/syncmatica_r/build_warning_settings.json` | Client toggle for the foreign build warning |
| Build visibility | `config/syncmatica_r/build_visibility_settings.json` | Client toggle for following claims with Litematica sub-region visibility |
| Client notices | `config/syncmatica_r/client_notices.json` | Dismissed migration notice version |

For integrated (single-player) servers, the main config is stored in `<world-folder>/syncmatica_r/config.json`.

See [CONFIG.md](CONFIG.md) for detailed configuration options.

### Mod Integration

Client mods can read the local player's claimed material requirements through the
side-effect-free [material integration interface](docs/MATERIAL_API.md). The
interface does not replace Litematica's active material list.

When TweakerMore 3.33.x is installed, Syncmatica_r adds an **Auto Collect Material
List Item - Material Source** option to TweakerMore's Features settings. Selecting
`Syncmatica_r` makes auto-collect use the local player's claimed shared-placement
deficits; the default remains `Litematica`. This integration does not modify
Litematica's active material list.

---

## Usage

### Sharing Schematics

1. Join a server with Syncmatica_r installed
2. Open the main menu — two extra buttons appear:
   - View shared placements on the server
   - Download shared placements to your client
3. Open your Litematica placement overview — an extra button lets you upload your placements to the server
4. To modify a shared placement: unlock it locally, make changes, then lock again to sync

### Loading Server-Side Litematic Files

Litematic files that exist in the server's `syncmatics` folder but are not registered as shared
placements (for example after a damaged placement store, or files dropped in by an admin) can be
re-registered without a client re-share:

```
/syncmatica_r load          # register every unregistered litematic file
/syncmatica_r load <file>   # register a single file; tab completion shows candidates
```

Loaded placements are positioned at the command issuer and broadcast to all connected clients.
Files not named after their content hash are renamed into the hash-based storage scheme so client
downloads resolve. The subcommand requires both `syncmatica_r.command` and
`syncmatica_r.command.load`, each with vanilla permission level 2 as the fallback.

### Managing Server Configuration

Server service settings can be inspected and changed without restarting:

```text
/syncmatica_r config list                         # list every server setting
/syncmatica_r config list materials               # list one section
/syncmatica_r config get materials max_schematic_blocks
/syncmatica_r config set materials max_schematic_blocks 64000000
/syncmatica_r config reset materials max_schematic_blocks
```

`set` validates the value, applies it immediately, and writes it to `config.json`. `reset` restores
one setting to its default. Section names, keys, and Boolean values support tab completion. Material
extraction settings automatically refresh all shared schematics; changing a feature switch also
refreshes connected clients.

These commands expose the `quota`, `materials`, `build`, and `debug` sections documented in
[CONFIG.md](CONFIG.md). Client-only preferences are not included. They require
`syncmatica_r.config`, with vanilla permission level 2 as the fallback.

### Setting Up Stocking Areas

Stocking areas are container regions the server scans for inventory tracking.
They can be set either by framing the area in-world with Litematica's schematic
tool item, or by typing the coordinates into a command.

**From an in-world selection (recommended):**

1. Switch Litematica to **Area Selection** tool mode and frame the container region
   with the schematic tool item, exactly as you would for any other selection
2. Open **Materials** for the shared placement and press **Stocking Area from Selection**,
   or open **Material Overview** and press **Default Area from Selection**
3. The server validates the box, stores it, and schedules a scan

The area is read from your currently selected sub-region box, so a selection with a
single box works without highlighting anything first. Litematica keeps drawing the
box; Syncmatica_r only reads its corners.

Permissions are per-placement for both the selection and command paths: the placement owner, or
anyone with `syncmatica_r.manage`, may set that placement's area. Servers can set
`materials.allow_owner_stocking_area_management` to `false` to require `syncmatica_r.manage` for
both paths. Setting the **default** area always requires `syncmatica_r.manage` (vanilla permission
level 2 as fallback).

**Create a default stocking area (matches all schematics via sign labels):**

```
/syncmatica_r default setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

**Create a schematic-specific stocking area:**

```
/syncmatica_r <SchematicName> setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

**Permissions:**

- A placement owner may run that placement's `setStockingarea` command when
  `materials.allow_owner_stocking_area_management` is enabled; this action does not require
  `syncmatica_r.command`.
- `syncmatica_r.manage` permits stocking-area changes for every placement and the default area,
  with vanilla permission level 2 as the fallback.
- `syncmatica_r.command` protects privileged project commands such as `rescanBuild`.
- `load` requires both `syncmatica_r.command` and `syncmatica_r.command.load`.
- `syncmatica_r.config` controls live server configuration commands, with vanilla permission level 2 as the fallback.
- Other network permission nodes are `syncmatica_r.share`, `syncmatica_r.claim`, and
  `syncmatica_r.build.claim`.

**How it works:**

- **Default area**: The server looks for signs attached to containers within the region. If a sign's text matches a shared schematic's name, that container's contents count as inventory for that schematic.
- **Named area**: The region becomes exclusive to the specified schematic — no sign labels needed.

---

## Supported Minecraft Versions

| Version | Status |
|---------|--------|
| 1.17.1  | ✅ Supported |
| 1.20.1  | ✅ Supported |
| 1.21.1  | ✅ Supported |
| 1.21.4  | ✅ Supported |
| 1.21.6  | ✅ Supported |
| 1.21.8  | ✅ Supported |
| 1.21.10 | ✅ Supported |
| 1.21.11 | ✅ Supported |
| 26.1    | ✅ Supported |
| 26.2    | ✅ Supported |

---

## License

CC0-1.0 — Public domain dedication.

---

## Contact

- **Email**: support@rms.net.cn
- **QQ Group**: 362669270
- **GitHub Issues**: [Report a bug](https://github.com/RMS-Server/syncmatica_r/issues)
