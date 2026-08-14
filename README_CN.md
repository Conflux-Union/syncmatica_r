# 共享原理图增强版

[English](README.md) | **[中文]**

> **0.4.0 不兼容版本：** 服主升级前必须阅读[迁移文档](docs/BREAKING_CHANGES_0.4.0_CN.md)，尤其要检查新的放置管理权限。

> **项目来源**：基于 [Syncmatica](https://github.com/End-Tech/syncmatica) 的增强分支，新增协作材料追踪功能。在 [RMS-Server/syncmatica_r](https://github.com/RMS-Server/syncmatica_r) 维护。

---

## 功能特性

### 核心功能：原理图共享

Syncmatica_r 与 Litematica 模组集成，实现服务器范围内的原理图共享：

- **上传与下载**：将你的 Litematica 放置共享到服务器；下载其他玩家共享的放置
- **放置同步**：位置、旋转等放置数据自动同步给所有玩家
- **锁定/解锁工作流**：解锁放置即可本地修改，重新锁定后变更同步给所有人
- **维度感知**：只有与原理图处于同一维度时才会加载

### 材料进度追踪

为多人协作建筑项目提供实时协调系统：

- **需求提取**：服务端自动扫描原理图所需材料，实时同步给所有玩家
- **认领机制**：玩家可认领特定材料，避免重复收集
- **备货区扫描**：服务端扫描配置的容器区域，追踪可用库存
- **实时 HUD 显示**：客户端悬浮窗口显示材料进度，支持自定义缩放
- **GUI 仪表盘**：通过「材料收集」按钮协调团队资源收集
- **排序与过滤**：按缺失数量或物品名称排序；隐藏已完成项目

---

## 安装

### 客户端要求

1. Fabric Loader
2. [Litematica](https://masa.dy.fi/mcmods/client_mods/)（必需）
3. [MaLiLib](https://masa.dy.fi/mcmods/client_mods/)（Litematica 依赖）
4. Syncmatica_r 模组文件

将所有模组文件放入 `mods` 文件夹。

### 服务端要求

1. Fabric Loader
2. Syncmatica_r 模组文件

服务端无需其他依赖。

### 配置

首次运行后，配置文件创建于：

| 文件 | 位置 | 用途 |
|------|------|------|
| 主配置 | `config/syncmatica_r/config.json` | 服务端配额、材料追踪设置 |
| HUD 设置 | `config/syncmatica_r/hud_settings.json` | 客户端 HUD 缩放与开关 |
| 材料列表 | `config/syncmatica_r/material_list_settings.json` | 客户端排序与过滤偏好 |
| 客户端提醒 | `config/syncmatica_r/client_notices.json` | 已关闭的迁移提醒版本 |

整合包（单人）服务端的主配置存储在 `<世界存档>/syncmatica_r/config.json`。

详细配置选项请参阅 [CONFIG_CN.md](CONFIG_CN.md)。

---

## 使用方法

### 共享原理图

1. 加入安装了 Syncmatica_r 的服务器
2. 打开主菜单 — 会出现两个额外按钮：
   - 查看服务器上共享的放置
   - 下载共享放置到客户端
3. 打开 Litematica 放置概览 — 额外按钮可将你的放置上传到服务器
4. 修改共享放置：先本地解锁，修改完成后重新锁定即可同步

### 加载服务端 Litematic 文件

服务器 `syncmatics` 目录中存在但未注册为共享放置的 litematic 文件（例如放置存储损坏后，
或管理员手动放入的文件），可以直接重新注册，无需客户端重新分享：

```
/syncmatica_r load          # 注册所有未注册的 litematic 文件
/syncmatica_r load <文件名>  # 注册单个文件；Tab 补全会列出候选文件
```

加载的放置定位在命令执行者所在位置，并广播给所有在线客户端。文件名不符合内容哈希命名的
文件会被重命名为哈希存储格式，以保证客户端下载可用。该子命令使用 `syncmatica_r.command.load`
权限节点，默认回退到原版权限等级 2。

### 设置备货区

备货区是服务端扫描库存的容器区域。既可以在游戏里用 Litematica 的工具物品直接框选，
也可以继续用命令敲坐标。

**用游戏内选区设置（推荐）：**

1. 把 Litematica 切到 **Area Selection** 工具模式，用工具物品框出容器区域，和平时框选一样
2. 打开该共享投影的 **Materials** 界面，点 **用选区设为备货区**；
   或打开 **Material Overview**，点 **用选区设为默认备货区**
3. 服务端校验体积后保存，并排入一次扫描

读取的是当前选中的子区域框，因此只有一个框的选区不需要先点选。框的绘制仍由 Litematica 负责，
Syncmatica_r 只读取两个角坐标。

这条路径的权限按投影区分：投影所有者，或拥有 `syncmatica_r.manage` 的玩家，可以设置该投影的备货区；
设置**默认**备货区始终需要 `syncmatica_r.manage`（回退到原版权限等级 2）。

**创建默认备货区（通过告示牌匹配所有原理图）：**

```
/syncmatica_r default setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

**创建专属备货区：**

```
/syncmatica_r <原理图名称> setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

**权限：**

- 权限节点：`syncmatica_r.command`（`load` 子命令额外检查 `syncmatica_r.command.load`）
- 回退行为：如果没有权限管理器处理该节点，则仍按原版权限等级 2（OP）允许执行。
- LuckPerms 示例：`/lp group <group> permission set syncmatica_r.command true`
- 网络权限节点：`syncmatica_r.share`、`syncmatica_r.claim` 和 `syncmatica_r.manage`。
- 所有者可以修改或移除自己的放置；管理其他玩家的放置需要 `syncmatica_r.manage`，默认回退到权限等级 2。

**工作原理：**

- **默认区域**：服务端检索区域内依附于容器的告示牌，若告示牌文字与共享原理图名称匹配，该容器内容计入该原理图的库存
- **命名区域**：区域成为指定原理图的专属备货区，无需告示牌标记

---

## 支持的 Minecraft 版本

| 版本   | 状态     |
|--------|----------|
| 1.17.1 | ✅ 支持  |
| 1.20.1 | ✅ 支持  |
| 1.21.1 | ✅ 支持  |
| 1.21.4 | ✅ 支持  |
| 1.21.6 | ✅ 支持  |
| 1.21.8 | ✅ 支持  |
| 1.21.10 | ✅ 支持  |
| 1.21.11 | ✅ 支持  |
| 26.1 | ✅ 支持  |
| 26.2 | ✅ 支持  |

---

## 许可证

CC0-1.0 — 公共领域贡献。

---

## 联系方式

- **邮箱**：support@rms.net.cn
- **QQ 群**：362669270
- **GitHub Issues**：[提交问题](https://github.com/RMS-Server/syncmatica_r/issues)
