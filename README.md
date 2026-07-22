# Chat upgrade

<div align="center">

![Page views](https://count.getloli.com/@LoosePrince#ChatUpgrade)

[![Fabric](https://img.shields.io/badge/Fabric-supported-141417?style=flat-square)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-supported-f16436?style=flat-square)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1%20%7C%2026.2-62b47a?style=flat-square)](https://minecraft.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ea7100?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>

Chat Upgrade 是一个 Minecraft 富媒体聊天模组，采用 **多版本 / 多加载器** 架构，同时支持 **Fabric** 与 **NeoForge**。当前发布线覆盖 **Minecraft 26.1.x / 26.2.x**；工程内还保留 **26.1.1 / 26.1.2** 兼容检查目标，但这些目标不发布独立 jar。

它保留原版聊天栏外壳，同时在默认 `TAKEOVER` 模式下接管聊天栏内容区，用自定义 `RichChatViewport` 渲染文本、行内表情、图片、音频播放器和视频播放器。服务端也安装本模组时，客户端可以把媒体直接上传到服务器，并通过结构化协议分发给其它客户端。

## 主要能力

- **富媒体聊天栏**：在聊天栏内显示图片、音频、视频，不需要弹出独立窗口。
- **TAKEOVER 自定义渲染**：默认模式下，聊天栏内容区由自定义 viewport 渲染，支持可变高度、像素滚动、裁切、hover、点击和 tooltip。
- **兼容文本模式**：`COMPAT_TEXT_VANILLA` 下，无附件纯文本尽量保留原版输入和显示链路，附件仍可走富媒体路径。
- **结构化聊天协议**：新客户端优先发送结构化消息和附件 metadata；不支持结构化时降级到标准 bracket 文本。
- **服务端媒体直传**：服务端启用后，客户端上传可生成 `chat-upgrade://media/<type>/<mediaId>`，其它客户端通过服务端拉取媒体字节。
- **行内表情**：识别 `[:token]`，按 `owo.json` 映射渲染为同高图片。
- **Bracket 协议兼容**：继续解析 `[[ChatUpgrade,url=...]]` 和图片场景下的 `[[CICode,...]]`，二者都是受支持的 bracket 协议格式。

## 聊天模式

| 模式 | 说明 |
| --- | --- |
| `TAKEOVER` | 默认模式。普通文本和附件都进入统一聊天状态层，由 `RichChatViewport` 自定义渲染。 |
| `COMPAT_TEXT_VANILLA` | 兼容模式。无附件纯文本尽量走原版链路，附件和 bracket 协议仍保留富媒体能力。 |

切换命令：

```text
/chatupgrade config inputmode takeover
/chatupgrade config inputmode compat
```

配置文件没有 `chatInputMode` 字段时，运行时等价于 `TAKEOVER`。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `/chatupgrade send <url> [name]` | 发送图片 URL。 |
| `/chatupgrade sendaudio <url> [name]` | 发送音频 URL。 |
| `/chatupgrade sendvideo <url> [name]` | 发送视频 URL。 |
| `/chatupgrade upload pick [name]` | 选择图片并上传发送。 |
| `/chatupgrade upload paste [name]` | 从剪贴板读取图片并上传发送。 |
| `/chatupgrade uploadaudio pick [name]` | 选择音频并上传发送。 |
| `/chatupgrade uploadvideo pick [name]` | 选择视频并上传发送。 |
| `/chatupgrade config uploadmode <auto/server/third>` | 切换上传路由。 |
| `/chatupgrade config modbuttonarrownavigation <true|false>` | 设置模组聊天按钮是否参与原版方向键焦点遍历，默认 `false`。 |
| `/chatupgrade config reload` | 重新读取客户端配置。 |
| `/chatupgrade config plugin status` | 查看 FFmpeg / APNG 插件状态。 |

完整命令和配置说明见 [配置、命令与运行验证](./docs/config-commands-and-runtime.md)。

## 配置文件

客户端配置：

```text
.minecraft/config/chat-upgrade/chat-upgrade.json
```

服务端媒体配置：

```text
config/chat-upgrade/server-media.json
```

常用配置包括：聊天模式、上传路由、模组聊天按钮方向键导航、手动触发加载、平滑滚动、音频/视频音量、接收/上传上限等。`modButtonArrowNavigation` 缺失或为 `false` 时，附件、表情和清空按钮不参与方向键焦点遍历，以保留原版输入框行为；设为 `true` 时，这三个按钮参与原版方向键焦点遍历。两种设置下均可使用鼠标和 `Tab` / `Shift+Tab`。

## 支持的媒体

| 类型 | 说明 |
| --- | --- |
| 图片 | `png`、`apng`、`jpg`、`jpeg`、`gif`、`webp`、`bmp`、`tif`、`tiff`、`jfif`、`ico` |
| 音频 | 常见音频格式，实际解码能力取决于 FFmpeg。 |
| 视频 | 常见视频格式，实际解码能力取决于 FFmpeg。 |

音频和视频基于 **JavaCPP FFmpeg + OpenAL**。FFmpeg native 和 APNG 插件可由模组自动准备，也可通过 `/chatupgrade config plugin ...` 命令检查或重新下载。

## 技术文档

详细架构和实现文档在 [`docs/`](./docs/README.md)：

- [项目总架构](./docs/architecture.md)
- [聊天 Pipeline 与模式边界](./docs/chat-pipeline.md)
- [RichChatViewport 实现](./docs/rich-chat-viewport.md)
- [协议与服务端路由](./docs/protocol-and-routing.md)
- [媒体上传与资源加载](./docs/media-and-upload.md)
- [配置、命令与运行验证](./docs/config-commands-and-runtime.md)
- [兼容性与扩展指南](./docs/compatibility-and-extension.md)

## 版本与加载器支持

本工程使用 **Stonecutter**（多版本编排）+ 各加载器原生工具链（Fabric Loom / NeoForge ModDevGradle）+ 自写平台抽象层，单一源码树编译到多个目标，不需要单文件多版本/多框架兼容。

| Minecraft | 加载器 | 目标 |
| --- | --- | --- |
| 26.1 | Fabric / NeoForge | `26.1-fabric` / `26.1-neoforge` |
| 26.2 | Fabric / NeoForge | `26.2-fabric` / `26.2-neoforge` |

各版本依赖坐标在 `gradle/targets/<version>.properties` 维护，新增版本只需加一个 properties 文件并在 `settings.gradle.kts` 注册。

## 工程结构

```text
src/
  common/      # 加载器无关的全部逻辑（媒体/UI/状态/编解码/服务端业务/mixin）+ platform 抽象
  fabric/      # Fabric 入口 + 平台实现 + fabric.mod.json
  neoforge/    # NeoForge 入口 + 平台实现 + neoforge.mods.toml
buildSrc/      # 源码合并与版本预处理（//? if >=26.2 { ... }）
gradle/targets/  # 各 Minecraft 版本依赖坐标
versions/      # Stonecutter 目标工作区（由源码生成）
```

平台抽象（`com.chat.upgrade.platform`）：`Platform`/`PlatformServices`、`Net`/`NetworkRegistrar`/`NetworkSender`、`CommandSink`/`CommandAdapter`，由各加载器实现，common 不直接依赖 Fabric/NeoForge API。

> 注：NeoForge 的 modId 不允许连字符，模组技术 id 统一为 `chatupgrade`（资源命名空间 `assets/chatupgrade/`），配置目录仍为 `config/chat-upgrade/`。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | `26.1` / `26.2` |
| 加载器 | Fabric（26.1.x 使用 Loader `>=0.18.6`，26.2.x 使用 `>=0.19.3`）/ NeoForge `26.x` |
| Java | `>= 25` |
| Gradle | Wrapper `9.5.1`（NeoForge ModDevGradle / Loom 1.17 要求） |
| 依赖坐标 | 见 `gradle/targets/<version>.properties` |

## 构建

构建指定目标（产物在 `versions/<target>/build/libs/`）：

```powershell
.\gradlew.bat :26.1-fabric:build
.\gradlew.bat :26.1-neoforge:build
.\gradlew.bat :26.2-fabric:build
.\gradlew.bat :26.2-neoforge:build
```

默认 **不** 把 FFmpeg 五平台 native 打进 jar（约 **2.5 MiB**）；首次播放音视频时由 `FfmpegNativeBootstrap` 按当前平台下载到 `config/chat-upgrade/libs/`。若需要离线全平台 fat jar（约 **116 MiB**），显式开启：

```powershell
.\gradlew.bat :26.1-fabric:build -PembedFfmpegNatives=true
```

开发运行（先用 `stonecutter.gradle.kts` 切换 active 目标，或直接对目标调用）：

```powershell
.\gradlew.bat :26.1-fabric:runClient --stacktrace
.\gradlew.bat :26.1-neoforge:runClient --stacktrace
```

服务端 smoke：

```powershell
.\gradlew.bat :26.1-fabric:runServer --args="--world chat-upgrade-runtime-smoke --port 25575 --nogui"
```
