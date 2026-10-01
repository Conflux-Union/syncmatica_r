# Syncmatica_r 0.5.0 迁移文档

[English](BREAKING_CHANGES_0.5.0.md)

Syncmatica_r 0.5.0 将逐放置的坐标式备货区替换为服务端全局的命名备货区注册表。服主升级前应备份
世界、`config/syncmatica_r` 和原理图存储，并同时更新服务端与客户端的 mod。

## 服主需要知道的

- **坐标命令已移除。** `/syncmatica_r <名称> setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>`
  和 `/syncmatica_r default setStockingarea …` 不复存在。备货区现在通过命名注册表创建和编辑：
  `/syncmatica_r stockingArea list|new|edit|delete`；放置通过
  `/syncmatica_r <名称> setStockingarea <备货区名>` 绑定备货区，或用
  `/syncmatica_r <名称> clearStockingarea` 解绑。
- **旧的 `set_stocking_area` 数据包会被拒绝。** 0.4.x 客户端连接 0.5.0 服务端后无法再推送基于
  选区的备货区；服务端会回复游戏内错误消息，提示玩家更新。此类客户端在加入后还会立即收到警告：
  备货区对其为只读，但仍能看到每个放置的当前区域（服务端会发送解析后的坐标）。请更新客户端以
  管理备货区。
- **权限模型。** 创建备货区需要备货区命令权限（`syncmatica_r.command`，默认原版等级 2）。编辑或
  删除备货区仅限其创建者或管理员。为放置绑定备货区仍遵循
  `allow_owner_stocking_area_management`。

## 存储变化

- **放置 JSON 改用 `stockingAreaRef`。** 旧的逐放置 `stockingArea` 坐标对象会在首次加载时迁移：
  每一项都会变成以放置命名的注册表条目，放置保存该区域的 UUID。旧键不再写出。之后降级回 0.4.x
  会丢失备货区：0.4.x 既不读取 `stockingAreaRef`，也不读取注册表。
- **服务端 meta 文件新增 `stockingAreas`。** `meta.json` 现在把命名注册表保存在 `stockingAreas`
  对象下。旧的 `defaultStockingArea` 坐标条目会迁移为注册表中保留的默认区域。

## 升级检查清单

1. 停止服务器，备份世界、`config/syncmatica_r` 和原理图存储（包括 `meta.json`）。
2. 在服务端和客户端安装 Syncmatica_r 0.5.0。
3. 启动服务器一次，让旧的逐放置区域和默认区域迁移进注册表，然后用
   `/syncmatica_r stockingArea list` 核对。
4. 告知使用旧坐标命令的玩家新的 `stockingArea new`/`edit` 命令和按名称绑定的方式。
