# Syncmatica_r

[![Modrinth downloads](https://img.shields.io/modrinth/dt/ZFRiWThj?logo=modrinth&label=Modrinth%20downloads)](https://modrinth.com/mod/syncmatica-revolution)

[English](README.md) | **中文**

Syncmatica_r 是一款面向 Litematica 多人协作建造的 Fabric 模组，基于
[Syncmatica](https://github.com/End-Tech/syncmatica) 分支开发，在
[RMS-Server/syncmatica_r](https://github.com/RMS-Server/syncmatica_r) 维护。除在服务器
范围内共享原理图外，还提供材料追踪与建造管理功能，使大型工程的协调完全在游戏内完成。

## 功能特性

### 原理图共享

将你的 Litematica 放置共享到服务器，所有玩家均可下载同一份放置，位置与旋转完全一致，
变更自动同步给所有玩家。修改共享放置时，先解锁、在本地编辑，再重新锁定，改动即同步给
所有人。放置仅在所属维度加载。

### 材料追踪

服务端自动读取每张共享原理图并计算材料清单，认领状态与进度对所有玩家实时同步：

- 玩家认领自己负责收集的材料。认领对全队可见，避免重复收集
- 悬浮 HUD 显示已认领材料的进度，大小与位置可在客户端设置中调整
- 「材料收集」仪表盘展示完整清单，支持按缺口数量或物品名称排序，可隐藏已完成的条目
- 备货区用于展示仓库现有库存：服务端扫描指定区域内的容器，仪表盘将这部分库存从需求中
  扣除

### 建造管理

将共享蓝图划分为若干子区域供玩家认领，分工记录直接保存在服务器上：

- 每个区域仅有一名负责人，对所有玩家可见
- 服务端实时测量每个区域的建造进度，方块放置后一至两个游戏刻内即更新；未被修改的放置
  不产生任何追踪开销
- 在其他玩家认领的区域内放置方块会收到提醒，但不会阻止放置操作
- 通过「建造管理」按钮或自定义快捷键打开仪表盘：先选择蓝图，再查看其区域列表，按数字
  顺序排列，已完成的排在最后
- 可选功能：认领区域时自动显示对应的 Litematica 子区域，取消认领或建造完成后重新隐藏。
  默认关闭，其他玩家认领的区域不受影响
- 与材料追踪互不依赖，可独立启用或禁用

### 网页界面（可选）

服务端可提供一个用于在浏览器中查看项目、材料与建造进度、认领和备货区的网站。默认
关闭，配置见 [CONFIG_CN.md](CONFIG_CN.md#web--可选网页界面服务器)。玩家在游戏内执行
`/syncmatica_r web setpassword <密码>` 设置登录密码。

### 模组联动

- 客户端模组可通过[材料联动接口](docs/MATERIAL_API.md)读取当前玩家已认领的材料需求
- 安装 TweakerMore 3.33.x 后，TweakerMore 的 Features 设置中会增加「自动收集材料列表
  物品-材料来源」选项。设置为 `Syncmatica_r` 后，自动收集将改为收集玩家在共享投影中
  认领的材料缺口

## 安装

客户端与服务端均需 Fabric Loader，客户端另需
[Litematica](https://masa.dy.fi/mcmods/client_mods/) 与
[MaLiLib](https://masa.dy.fi/mcmods/client_mods/)。将模组文件放入 `mods` 文件夹即可，服务
端无其他依赖。

从 0.3.x 升级前，请先阅读[迁移指南](docs/BREAKING_CHANGES_0.4.0_CN.md)：0.4.0 调整了权限
的处理方式。

首次运行后，配置文件创建于 `config/syncmatica_r/` 目录：`config.json` 为服务端设置，
`client.json` 为客户端偏好（HUD、快捷键等）。单人世界的服务端配置存储于
`<世界存档>/syncmatica_r/config.json`。全部配置项见 [CONFIG_CN.md](CONFIG_CN.md)，大多数
服务端设置亦可通过 `/syncmatica_r config` 在线修改，无需重启。

## 使用方法

### 共享原理图

1. 加入安装了 Syncmatica_r 的服务器
2. Litematica 主菜单会新增两个按钮：浏览服务器上的共享放置、将共享放置下载到客户端
3. 放置概览会新增一个按钮，用于将你的放置上传到服务器
4. 修改共享放置：解锁 → 编辑 → 重新锁定，改动即同步

### 备货区

推荐方式为游戏内框选：使用 Litematica 的 area selection（原理图工具）框出容器区域，
在放置的 **Materials** 界面点击 **用选区设为备货区**；**Material Overview** 提供同样的
按钮，用于设置默认备货区。服务端校验区域后安排一次扫描。

亦可通过命令设置：

```text
/syncmatica_r <名称> setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
/syncmatica_r default setStockingarea <x1> <y1> <z1> <x2> <y2> <z2>
```

专属备货区仅属于指定的原理图。默认备货区通过告示牌匹配：容器上告示牌的文字与某个共享
原理图名称一致时，该容器的内容计入该原理图的库存。

放置所有者可设置自己放置的备货区（服务端可通过配置禁用该授权）；默认备货区始终要求
`syncmatica_r.manage`。

### 服务端文件与在线配置

```text
/syncmatica_r load [文件]                          # 注册 syncmatics 目录中尚无对应放置的
                                                   # litematic 文件，加载后出现在命令执行者
                                                   # 所在位置
/syncmatica_r config list [模块]                   # 查询并在线修改服务端配置：
/syncmatica_r config get <模块> <配置项>            # 取值经校验后立即生效并保存，
/syncmatica_r config set <模块> <配置项> <值>       # 无需重启
/syncmatica_r config reset <模块> <配置项>
```

`load` 适用于恢复备份之后，或管理员直接向 `syncmatics` 目录添加文件的情况。两组命令的
参数均支持 Tab 补全。

### 权限

| 权限节点 | 控制内容 | 默认回退 |
|------|------|------|
| `syncmatica_r.share` | 上传放置 | 允许 |
| `syncmatica_r.claim` | 认领材料 | 允许 |
| `syncmatica_r.build.claim` | 认领建造区域 | 允许 |
| `syncmatica_r.manage` | 管理其他玩家的放置、各放置备货区与默认备货区 | 权限等级 2 |
| `syncmatica_r.command` | `load`、`rescanBuild` 等管理命令 | 权限等级 2 |
| `syncmatica_r.config` | 在线配置命令 | 权限等级 2 |

`load` 另需 `syncmatica_r.command.load`。放置所有者始终可以管理自己的放置。

## 支持的 Minecraft 版本

1.17.1、1.20.1、1.21.1、1.21.4、1.21.6、1.21.8、1.21.10、1.21.11、26.1、26.2。

## 许可证

CC0-1.0，公共领域贡献。

## 联系方式

- 邮箱：support@rms.net.cn
- QQ 群：362669270
- [GitHub Issues](https://github.com/RMS-Server/syncmatica_r/issues)
