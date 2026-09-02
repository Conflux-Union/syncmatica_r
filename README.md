# Syncmatica_r

**English** | [中文](README_CN.md)

Syncmatica_r is a Fabric mod for collaborative building with Litematica. It is a fork of
[Syncmatica](https://github.com/End-Tech/syncmatica), maintained at
[RMS-Server/syncmatica_r](https://github.com/RMS-Server/syncmatica_r). In addition to
sharing schematics across the server, it provides material tracking and build
management, allowing teams to coordinate large projects entirely in game.

## Features

### Schematic sharing

Share your Litematica placement with the server so that every player can download the
same placement at the identical position and rotation. Changes to a placement
synchronize to all players automatically. To modify a shared placement, unlock it,
edit locally, then lock it again to publish the changes. Placements load only in the
dimension they were shared in.

### Material tracking

The server reads every shared schematic, derives the material list, and keeps claims
and progress synchronized for all players:

- Players claim the materials they intend to gather. Claims are visible to the entire
  team, preventing duplicate collection.
- A floating HUD displays the progress of claimed materials; size and position are
  configurable in the client settings.
- The material dashboard (the **Material Collections** button) presents the complete
  list, sortable by missing count or item name, with completed items optionally hidden.
- Stocking areas report what is already in storage: the server scans the containers
  within a defined region, and the dashboard subtracts that stock from the
  requirements.

### Build management

Divides a shared schematic into sub-regions that players claim, so the division of work
is recorded on the server rather than in an external document:

- Each region has one owner, visible to all players.
- The server measures how much of each region has been built; progress updates within
  one to two ticks of a block being placed. Untouched placements incur no tracking
  cost.
- Placing blocks inside a region claimed by another player triggers a warning; the
  placement itself is never cancelled.
- The build dashboard (**Build Management** button or a configurable hotkey) lists the
  regions of the selected schematic in numeric order, with completed regions last.
- Optionally, claiming a region can reveal its Litematica sub-region, and dropping or
  completing the claim hides it again. Disabled by default; regions claimed by other
  players are never affected.
- Operates independently of material tracking.

### Optional web interface

The server can host a small website for viewing projects, material and build progress,
claims, and stocking areas from a browser. Disabled by default; see
[CONFIG.md](CONFIG.md#web--optional-web-interface-server) for the settings. Players
authenticate with a password set in game via `/syncmatica_r web setpassword <password>`.

### Integrations

- Client mods can read the local player's claimed material requirements through the
  [material integration interface](docs/MATERIAL_API.md).
- With TweakerMore 3.33.x installed, an **Auto Collect Material List Item - Material
  Source** option appears in TweakerMore's Features settings. Set it to `Syncmatica_r`
  and auto-collect gathers claimed materials from shared placements instead of
  Litematica's own list.

## Installation

Both sides require Fabric Loader; the client additionally requires
[Litematica](https://masa.dy.fi/mcmods/client_mods/) and
[MaLiLib](https://masa.dy.fi/mcmods/client_mods/). Place the mod file in the `mods`
folder; the server has no further dependencies.

When upgrading from 0.3.x, read the [migration guide](docs/BREAKING_CHANGES_0.4.0.md)
first: version 0.4.0 changed the permission handling.

After the first run, configuration files are created in `config/syncmatica_r/`:
`config.json` holds the server settings, `client.json` the client preferences such as
the HUD and hotkeys. Single-player worlds store the server configuration in
`<world>/syncmatica_r/config.json`. All options are documented in
[CONFIG.md](CONFIG.md); most server settings can also be changed live with
`/syncmatica_r config` without a restart.

## Usage

### Sharing schematics

1. Join a server running Syncmatica_r.
2. The Litematica main menu gains two buttons: browse the placements shared on the
   server, and download them to the client.
3. The placement overview gains a button for uploading placements to the server.
4. To modify a shared placement: unlock it, edit, then lock it again to synchronize.

### Stocking areas

The recommended method is an in-world selection: frame the container region with
Litematica's area selection (schematic tool), then press **Stocking Area from
Selection** in the placement's **Materials** screen. **Material Overview** offers the
same button for the default area. The server validates the region and schedules a
scan.

Alternatively, by command:

```text
/syncmatica_r <name> setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
/syncmatica_r default setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

A schematic-specific area belongs to that schematic alone. The default area matches
containers to schematics by signs: a sign on a container whose text equals a shared
schematic's name makes that container count as stock for that schematic.

Placement owners may set their own placement's area (this can be disabled in the
server configuration); the default area always requires `syncmatica_r.manage`.

### Server files and live settings

```text
/syncmatica_r load [file]                          # register litematic files located in the
                                                   # server's syncmatics folder without a
                                                   # placement; loaded placements appear at
                                                   # the issuer's position
/syncmatica_r config list [section]                # inspect and change server settings:
/syncmatica_r config get <section> <key>           # values are validated, applied
/syncmatica_r config set <section> <key> <value>   # immediately, and saved
/syncmatica_r config reset <section> <key>
```

`load` is intended for recovery after a backup restore, or when an administrator adds
files to `syncmatics` directly. Both command groups support tab completion.

### Permissions

| Node | Controls | Fallback |
|------|----------|----------|
| `syncmatica_r.share` | uploading placements | allowed |
| `syncmatica_r.claim` | claiming materials | allowed |
| `syncmatica_r.build.claim` | claiming build regions | allowed |
| `syncmatica_r.manage` | others' placements, stocking areas, the default area | permission level 2 |
| `syncmatica_r.command` | privileged commands such as `load`, `rescanBuild` | permission level 2 |
| `syncmatica_r.config` | live configuration commands | permission level 2 |

`load` additionally requires `syncmatica_r.command.load`. Placement owners can always
modify their own placements.

## Supported Minecraft versions

1.17.1, 1.20.1, 1.21.1, 1.21.4, 1.21.6, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2.

## License

CC0-1.0, public domain dedication.

## Contact

- Email: support@rms.net.cn
- QQ group: 362669270
- [GitHub Issues](https://github.com/RMS-Server/syncmatica_r/issues)
