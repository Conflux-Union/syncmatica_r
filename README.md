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
| Main config | `config/syncmatica_r/config.json` | Server quota, material tracking settings |
| HUD settings | `config/syncmatica_r/hud_settings.json` | Client HUD scale and enable toggle |
| Material list | `config/syncmatica_r/material_list_settings.json` | Client sort mode and filter preferences |
| Client notices | `config/syncmatica_r/client_notices.json` | Dismissed migration notice version |

For integrated (single-player) servers, the main config is stored in `<world-folder>/syncmatica_r/config.json`.

See [CONFIG.md](CONFIG.md) for detailed configuration options.

---

## Usage

### Sharing Schematics

1. Join a server with Syncmatica_r installed
2. Open the main menu — two extra buttons appear:
   - View shared placements on the server
   - Download shared placements to your client
3. Open your Litematica placement overview — an extra button lets you upload your placements to the server
4. To modify a shared placement: unlock it locally, make changes, then lock again to sync

### Setting Up Stocking Areas

Stocking areas are container regions the server scans for inventory tracking.

**Create a default stocking area (matches all schematics via sign labels):**

```
/syncmatica_r default setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

**Create a schematic-specific stocking area:**

```
/syncmatica_r <SchematicName> setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

**Permission:**

- Permission node: `syncmatica_r.command`
- Fallback behavior: if no permissions provider handles the node, vanilla permission level 2 (operators) can use the command.
- LuckPerms example: `/lp group <group> permission set syncmatica_r.command true`
- Network permission nodes: `syncmatica_r.share`, `syncmatica_r.claim`, and `syncmatica_r.manage`.
- Owners may modify or remove their own placements. Managing another player's placement requires
  `syncmatica_r.manage`, with vanilla permission level 2 as the fallback.

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
| 1.21.8  | ✅ Supported |
| 1.21.10 | ✅ Supported |
| 1.21.11 | ✅ Supported |

---

## License

CC0-1.0 — Public domain dedication.

---

## Contact

- **Email**: support@rms.net.cn
- **QQ Group**: 362669270
- **GitHub Issues**: [Report a bug](https://github.com/RMS-Server/syncmatica_r/issues)
