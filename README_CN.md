# Syncmatica_r

[English](README.md) | **[中文]**

> **项目来源**：本项目为 [Syncmatica](https://github.com/End-Tech/syncmatica) 的二次修改版本，在原有功能基础上增加了材料进度追踪等协作增强特性。

## 核心新增功能：共享材料进度追踪

**Material Progress Tracking** 为多人协作建筑提供实时材料需求共享系统：

- **材料需求共享**：服务端自动扫描建筑所需材料，所有玩家实时同步查看
- **认领机制**：玩家可认领特定材料，避免重复收集
- **补货区域扫描**：服务端可配置扫描指定区域的容器库存，追踪可用库存
- **HUD实时显示**：客户端悬浮窗显示材料进度，支持自定义位置和样式

通过 `MaterialGatherings` 按钮打开材料追踪界面，协调团队资源收集效率。

---

Syncmatica_r 是一个旨在集成到 litematica 的模组，使得原理图及其放置可以轻松共享。

### 注意：请谨慎使用

Syncmatica_r 为用户提供了很大的权限，可能对服务器造成影响。只有在你确信用户不会过度滥用它的情况下才使用此模组。

## 安装设置

Syncmatica_r 是一个同时适用于 Minecraft 客户端和服务端的模组。
该模组支持 Minecraft 1.16+。官方构建版本涵盖 1.17.1，新的预处理目标通过 Fabric API 0.92.5+1.20.1 支持 1.20.1。
它是为 [Minecraft Fabric](https://fabricmc.net/) 制作的。它依赖于 [litematica 和 malilib](https://masa.dy.fi/mcmods/client_mods/) 来提供所有客户端功能。在报告 Syncmatica_r 功能的 bug 之前，请确保更新 litematica、malilib 和其他可能冲突的模组（如 Multiconnect）:)

### 客户端

你首先需要安装 fabric 并将 litematica 和 malilib 模组添加到你的客户端。下一步是将 Syncmatica_r 模组文件移动到 mods 文件夹。现在你就可以开始使用了。

### 服务端

对于服务端，你只需要安装 fabric 并将 Syncmatica 放入 mods 文件夹，就可以了。

运行模组一次后，它将创建一个配置文件，你可以使用它来根据需要配置模组。
更多信息请参阅 [配置文档](https://github.com/RMS-Server/syncmatica_r/blob/master/CONFIG.md)。

## 使用方法

在客户端安装后，你可以正常加入任何服务器。对于安装了 Syncmatica_r 的服务器，你将获得一些额外的按钮。其中 2 个位于主菜单中，允许你查看服务器上共享的放置并下载它们。另一个在你的原理图放置概览中，允许你与服务器共享你自己的 litematics。

你需要与 syncmatic 在同一维度才能加载它。

要修改放置，只需在客户端上解锁放置。进行更改后再次锁定以与所有人共享更改。

## 联系方式

- 邮箱:support@rms.net.cn
- QQ 群:362669270
