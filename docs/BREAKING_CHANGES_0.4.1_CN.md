# Syncmatica_r 0.4.0 迁移文档

[English](BREAKING_CHANGES_0.4.0.md)

Syncmatica_r 0.4.0 是一次以安全修复为主的不兼容版本。服主升级前应备份世界、`config/syncmatica_r`
和原理图存储，并同时更新服务端与客户端的 mod。

## 服主权限迁移

旧版本允许任何已连接的 Syncmatica 客户端修改或删除任意共享放置。0.4.0 调整为：

| 权限节点 | 没有权限插件时的默认行为 | 用途 |
|----------|--------------------------|------|
| `syncmatica_r.share` | 默认允许 | 上传并共享新放置。 |
| `syncmatica_r.claim` | 默认允许 | 认领已有材料需求。 |
| `syncmatica_r.manage` | 原版权限等级 2 | 修改或删除其他玩家拥有的放置。 |

放置所有者始终可以修改或删除自己的放置。权限等级为 2 的 OP 仍然拥有全局管理权限。

推荐只把全局管理权限授予可信建筑成员或管理员：

```text
/lp group builders permission set syncmatica_r.share true
/lp group builders permission set syncmatica_r.claim true
/lp group trusted-builders permission set syncmatica_r.manage true
```

如果服主确实需要恢复旧版行为，让所有玩家都能修改或删除所有放置，可以给默认组授予全部三个权限：

```text
/lp group default permission set syncmatica_r.share true
/lp group default permission set syncmatica_r.claim true
/lp group default permission set syncmatica_r.manage true
```

这样会恢复旧工作方式，但也会恢复原有安全风险：任何玩家都能修改或删除其他人的放置。如果没有
LuckPerms 或其他 Fabric Permissions API 权限插件，全局管理的内置回退方式只有原版权限等级 2。建议安装
权限插件，不要为了恢复旧权限而把所有玩家设为 OP。

## 玩家会遇到的变化

- 你仍然可以修改或删除自己拥有的放置。
- 修改或删除其他玩家的放置需要 `syncmatica_r.manage`。
- 除非服主调整权限策略，否则共享放置和材料认领仍然默认可用。
- 客户端原理图现在按放置 UUID 保存。hash 校验一致的旧文件会自动迁移。
- 材料提取和备货区扫描可能在命令执行后稍晚完成，不一定在同一游戏 tick 内显示结果。

## 新的限制

- 原理图传输和解压后的原理图数据上限为 64 MB。
- 每个连接最多同时进行 8 个传输；60 秒无活动的传输会被清理。
- 服务端最多保存 4,096 个共享放置。
- 材料种类、子区域、嵌套容器深度和备货区体积均有限制，以保护服务器。
- 如果启用上传配额，失败上传仍会计入；重新连接不会清零。

## 升级检查清单

1. 停止服务器，备份世界、`config/syncmatica_r` 和原理图存储。
2. 在服务端和客户端安装 Syncmatica_r 0.4.0。
3. 玩家进入前检查 `syncmatica_r.manage` 的授权范围。
4. 检查 `config/syncmatica_r/config.json` 中的资源上限。
5. 重启服务器，并使用非 OP 测试账号验证共享、认领、修改和删除功能。
