# Syncmatica_r 配置手册

**[English](CONFIG.md) | 中文**

Syncmatica_r 的全部运行配置保存在单个 `config.json` 文件中。启动时加载器会自动补全缺失
的配置项；JSON 无法解析时，会以上次完好的配置重建文件，未知键会被保留。手动编辑的配置
在下次重启时生效；大部分服务端设置亦可通过命令在线修改。

## 文件位置

- **专用服务器与独立客户端：** `config/syncmatica_r/config.json`
- **单人世界：** `<世界存档>/syncmatica_r/config.json`，每个存档可拥有独立的配额与材料
  规则
- **旧版兼容：** 若 `config/syncmatica_r` 不存在而旧版 `config/syncmatica` 存在，将一次性
  迁移旧文件；此后仅读取 `syncmatica_r`

## 在线修改配置

```text
/syncmatica_r config list [模块]
/syncmatica_r config get <模块> <配置项>
/syncmatica_r config set <模块> <配置项> <值>
/syncmatica_r config reset <模块> <配置项>
```

`set` 会先校验取值，通过后立即生效并写入 `config.json`；非法值将被拒绝而非静默修正。
模块名、配置项与布尔值支持 Tab 补全。命令覆盖以下全部服务端模块，需要
`syncmatica_r.config` 权限，默认回退至原版权限等级 2。客户端偏好不在此范围内。

## 配置结构

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

服务器使用全部模块；客户端仅需 `debug` 与根级的两个更新开关。客户端可删除 `quota`、
`materials`、`build` 与 `web` 模块，日后运行服务器时加载器会自动重建。

## `quota` — 上传流量控制（服务器）

| 配置项 | 默认值 | 取值范围 | 说明 |
|--------|--------|----------|------|
| enabled | `false` | `true` / `false` | 统计服务端从每个玩家接收的原理图上传流量 |
| limit | `40000000` | ≥ 0（字节） | 单个玩家在服务器运行期间允许上传的原理图总字节数 |

- 流量按玩家身份累计，所有已接收的传输均计入，包括最终失败的传输。重新连接不清零，
  服务器重启后方才清零。
- `enabled` 保持 `false` 时不记录任何流量数据。无论该项状态如何，单文件大小、数据包
  大小、并发交换数与交换时长均受硬限制约束。

## `materials` — 材料追踪（服务器）

| 配置项 | 默认值 | 取值范围 | 作用 |
|--------|--------|----------|------|
| enabled | `true` | — | 总开关，控制材料统计与同步 |
| scan_interval | `200` | ≥ `20` 游戏刻 | 空闲时重扫默认备货区的间隔 |
| scan_blocks_per_tick | `2048` | `64`–`65,536` 个方块 | 增量扫描的每刻共享工作预算 |
| include_container_contents | `false` | — | 是否统计原理图内箱子、潜影盒等容器的物品 |
| allow_owner_stocking_area_management | `true` | — | 是否允许投影所有者通过命令或 GUI 设置自己投影的备货区；设为 `false` 后两条路径均要求 `syncmatica_r.manage` |
| max_schematic_megabytes | `64` | `1`–`64` MB | 压缩传输大小与解压后 NBT 分配的上限 |
| max_schematic_blocks | `8000000` | `1,000,000`–`64,000,000` | 解码后方块体积上限 |
| max_stocking_area_blocks | `1000000` | `1,024`–`64,000,000` | 备货区允许的最大体积 |

- 扫描引起服务器卡顿时，可调低 `scan_blocks_per_tick` 并调大 `scan_interval`；扫描过慢
  则可调高预算。
- 原理图解析在后台线程执行，传输大小与解码体积均先经校验，通过后方在服务端生效。
- 修改提取上限或 `include_container_contents` 后会重新提取所有共享原理图；其余配置项
  立即生效。

## `build` — 建造管理（服务器）

建造管理独立读取蓝图，与材料追踪互不依赖，两个功能可分别启用或禁用。禁用本模块后，
所有客户端均不再显示区域认领。

| 配置项 | 默认值 | 取值范围 | 作用 |
|--------|--------|----------|------|
| enabled | `true` | — | 总开关，控制区域认领及以下全部功能 |
| completion_enabled | `true` | — | 是否测量每个区域的建造进度。关闭后认领仍可正常使用 |
| scan_blocks_per_tick | `4096` | `64`–`65,536` 个方块 | 完成度扫描的每刻工作预算 |
| scan_interval | `1200` | ≥ `100` 游戏刻 | 尚有未统计区块列时重试的间隔 |
| full_rescan_interval | `36000` | ≥ `1200` 游戏刻，或 `0` | 全部区块列从头重新统计的间隔；`0` 禁用该轮兜底扫描 |

- 扫描由方块变化驱动。未被修改的放置不进行任何扫描；正在施工的放置仅重新统计发生
  变化的区块列，方块放置后一至两个游戏刻内进度即更新。闲置的蓝图无论大小均不产生开销。
- 同一时刻仅扫描一个放置：大蓝图的代价是扫描时间较长，而非服务器卡顿。
- 加载范围外的区块列保留上次统计值，因为未加载的区块内无法施工。区域远大于玩家加载
  范围时仍可完成测量，仅随区块加载分批推进。从未加载过的区块列按未建造计算；
  `scan_interval` 控制服务端重试这些区块列的频率。
- 不经过游戏本身的写入（如批量编辑工具直接修改区块 section、停服后修改 region 文件）
  不会被上报，会留下过期的统计数。`full_rescan_interval` 即为此提供的兜底重扫：服务器上
  无此类工具时可调长该间隔；设为 `0` 可完全禁用，此后仅能通过
  `/syncmatica_r <项目名> rescanBuild` 手动重测。
- 完成度仅比较方块种类，不比较完整方块状态：楼梯朝向全部装反的区域同样计为完成，与
  材料清单的统计口径一致。
- 认领按区域名称记录，重新分享、重新提取与重启服务器均不会丢失；区域从蓝图中移除时，
  对应认领随之消失。
- 统计数据保存在其测量的世界中，位于 `<存档>/syncmatica_r/build_scan/`，还原备份时对应
  的统计数会一并恢复。
- 两项开关属于玩家而非服主，保存在客户端配置中（`config/syncmatica_r/client.json`，
  MaLiLib 设置界面亦可修改）：越界建造提醒（`General.warnOnForeignPlacement`，默认开启）
  与 Litematica 子区域可见性跟随认领（`General.followClaims`，默认关闭）。
- 修改本模块后需重启服务器方可生效。

## `web` — 可选网页界面（服务器）

网页服务默认关闭。修改本模块后请重启服务器。

| 配置项 | 默认值 | 有效范围 | 作用 |
|--------|--------|----------|------|
| enabled | `false` | `true` / `false` | 服务器启动时是否启动网页服务 |
| bind_address | `127.0.0.1` | 非空且可解析的主机名或 IP | HTTP 监听地址 |
| port | `8080` | `1`–`65,535` | HTTP 监听端口 |
| session_hours | `24` | `1`–`8,760` 小时 | 浏览器会话有效期 |
| secure_cookie | `false` | `true` / `false` | 为会话 Cookie 添加 `Secure` 标记；通过 HTTPS 访问时应开启 |
| max_request_bytes | `65536` | `1,024`–`1,048,576` 字节 | 接受的 JSON 请求体大小上限 |
| request_timeout_seconds | `10` | `1`–`120` 秒 | 登录校验与 Minecraft 服务端操作的超时时间 |

启用后，玩家在游戏内执行 `/syncmatica_r web setpassword <密码>` 设置网页密码，执行
`/syncmatica_r web disable` 停用。密码凭据跨重启保存于
`config/syncmatica_r/web-credentials.json`（单人世界为
`<世界存档>/syncmatica_r/web-credentials.json`）。会话仅保存在内存中，因此服务器重启后
浏览器需重新登录，原密码仍然有效。

通过 HTTPS 提供服务时应将 `secure_cookie` 设为 `true`。使用反向代理时保留默认的
`127.0.0.1` 监听地址，HTTPS 在代理层终结；仅当其他主机需要直连时才修改 `bind_address`。

网页支持查看共享项目、材料与建造进度、认领及项目专属备货区，权限规则与游戏内一致。
不提供项目删除、原理图上传下载、默认备货区管理、配置修改与手动重扫。

## 上限提示

某项上限拦截操作时，服务端会说明所涉及的限制并附上具体数值，例如
`96.0 MB > 64.0 MB`，而非静默失败。

| 场景 | 提示位置 | 相关配置 |
|------|----------|----------|
| 分享的投影超过允许的传输大小 | 分享方客户端的错误提示 | `materials.max_schematic_megabytes` |
| 分享的投影耗尽玩家上传配额 | 分享方客户端的错误提示 | `quota.limit` |
| 服务器上已存的投影大于当前上限，无法下发 | 下载方客户端的错误提示，同时立即取消下载而非等待超时 | `materials.max_schematic_megabytes` |
| 材料列表无法生成 | 打开材料列表界面时的原因提示、材料按钮的悬停文本，以及发给放置所有者的一次性消息 | `materials.enabled`、`materials.max_schematic_megabytes`、`materials.max_schematic_blocks` |

最常见的原因是将 `max_schematic_megabytes` 或 `max_schematic_blocks` 调整至已分享投影
体积以下：这些放置的元数据仍然保留，但下载与材料列表均会失效，受影响的客户端可见
拦截其操作的具体限制。

## `debug` — 调试模式（客户端和服务器）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| doPackageLogging | `false` | 在日志中记录所有数据包收发详情（Log4j，`INFO` 级别）。仅应在排查网络问题时开启，日志量较大 |

## 客户端更新开关

`checkupdate` 与 `check_pre_release` 位于配置文件根对象，仅在客户端生效。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `checkupdate` | `true` | GitHub 更新检查总开关。设为 `false` 后客户端不访问 GitHub，亦不弹出更新提示 |
| `check_pre_release` | `false` | 为 `true` 时，正式版客户端亦将更高版本的预发布视为更新；为 `false` 时仅认可更高的正式版。预发布客户端始终两者均认可 |

## 网络权限

- `syncmatica_r.share`：上传新放置；默认允许。
- `syncmatica_r.claim`：认领已有材料需求；默认允许。
- `syncmatica_r.build.claim`：认领共享蓝图的子区域；默认允许。与 `syncmatica_r.claim`
  相互独立，便于将材料收集与区域建造分配给不同玩家。
- `syncmatica_r.manage`：修改或删除其他玩家的放置、管理任意投影的备货区及默认备货区；
  默认回退至权限等级 2。
- 放置所有者始终可以修改或删除自己的放置。

## 常见问题

- 配置文件被重置：JSON 格式存在错误。请在重建后的文件上重新修改。
- 删除的配置项再次出现：加载器会将缺失的键按默认值补全。禁用某项功能时应修改其取值，
  而非删除该行。
- 将 `enabled` 设为 `false` 后材料界面仍然显示：请确认修改的是服务器配置而非客户端配置，
  并已重启服务器使改动同步至在线客户端。
