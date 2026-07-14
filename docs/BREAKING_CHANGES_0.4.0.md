# Syncmatica_r 0.4.0 Migration Guide

[中文](BREAKING_CHANGES_0.4.0_CN.md)

Syncmatica_r 0.4.0 is a security-focused breaking release. Server owners should back up the world,
`config/syncmatica_r`, and schematic storage before upgrading. Update the mod on both the server and clients.

## Permission migration for server owners

Previous releases allowed any connected Syncmatica client to modify or delete any shared placement. Version 0.4.0
changes this behavior:

| Permission | Default without a permission provider | Purpose |
|------------|---------------------------------------|---------|
| `syncmatica_r.share` | Allowed | Upload and share new placements. |
| `syncmatica_r.claim` | Allowed | Claim existing material requirements. |
| `syncmatica_r.manage` | Vanilla permission level 2 | Modify or delete placements owned by another player. |

Placement owners can always modify or delete their own placements. Operators with permission level 2 retain global
management access.

The recommended setup grants global management only to trusted builders or moderators:

```text
/lp group builders permission set syncmatica_r.share true
/lp group builders permission set syncmatica_r.claim true
/lp group trusted-builders permission set syncmatica_r.manage true
```

To restore the old behavior and let every player modify or delete every placement, grant all three permissions to the
default group:

```text
/lp group default permission set syncmatica_r.share true
/lp group default permission set syncmatica_r.claim true
/lp group default permission set syncmatica_r.manage true
```

This restores the old workflow, but also restores its security risk: any player can alter or delete another player's
placement. Without LuckPerms or another Fabric Permissions API provider, the only built-in management fallback is
vanilla permission level 2. Installing a permission provider is strongly preferred over making every player an
operator.

## What players should expect

- You can still modify or delete placements that you own.
- Modifying or deleting another player's placement now requires `syncmatica_r.manage`.
- Sharing and material claiming continue to work by default unless the server owner changes their permission policy.
- Client schematic files are now stored by placement UUID. Matching legacy files are migrated automatically after
  verification.
- Material extraction and stocking-area scans may finish shortly after a command instead of in the same game tick.

## New limits

- Schematic transfers and decompressed schematic data are limited to 64 MB.
- A connection can have at most 8 active transfers, and inactive transfers expire after 60 seconds.
- A server can hold at most 4,096 shared placements.
- Material entries, subregions, nested containers, and stocking-area volume are bounded to protect the server.
- If upload quota is enabled, failed uploads still count and reconnecting does not reset the counter.

## Upgrade checklist

1. Stop the server and back up the world, `config/syncmatica_r`, and schematic storage.
2. Install Syncmatica_r 0.4.0 on the server and clients.
3. Review `syncmatica_r.manage` assignments before allowing players to join.
4. Review the limits in `config/syncmatica_r/config.json`.
5. Restart the server and test sharing, claiming, modifying, and deleting with a non-operator account.
