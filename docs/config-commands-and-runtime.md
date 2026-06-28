# 配置、命令与运行验证

这篇文档说明项目常用配置、命令、构建运行和 smoke 验证方式。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | `26.1` / `26.2` |
| 加载器 | Fabric（26.1.x 使用 Loader `>=0.18.6`，26.2.x 使用 `>=0.19.3`）/ NeoForge `26.x` |
| Java | `>= 25` |
| Gradle | Wrapper `9.5.1` |
| 依赖坐标 | `gradle/targets/<version>.properties` |

工程使用 **Stonecutter** 管理多版本目标；Fabric 侧用 **Fabric Loom 1.17**，NeoForge 侧用 **ModDevGradle**。公共逻辑在 `src/common`，经 `buildSrc` 预处理合并到各目标。

## 构建与运行

构建指定目标（产物在 `versions/<target>/build/libs/`，默认约 **2.5 MiB**）：

```powershell
.\gradlew.bat :26.1-fabric:build
.\gradlew.bat :26.1-neoforge:build
.\gradlew.bat :26.2-fabric:build
.\gradlew.bat :26.2-neoforge:build
```

默认不把 FFmpeg 五平台 native 嵌入 jar；需要离线 fat jar（约 116 MiB）时加 `-PembedFfmpegNatives=true`。CI 与发布均使用 slim 包，运行时由 `FfmpegNativeBootstrap` 按平台下载 native。

开发客户端（可直接指定目标，或在 `stonecutter.gradle.kts` 中切换 active 目标）：

```powershell
.\gradlew.bat :26.1-fabric:runClient --stacktrace
.\gradlew.bat :26.1-neoforge:runClient --stacktrace
```

服务端 smoke：

```powershell
.\gradlew.bat :26.1-fabric:runServer --args="--world chat-upgrade-runtime-smoke --port 25575 --nogui"
```

检查 Java 进程：

```powershell
jps -l -v
```

## 客户端配置

配置文件：

```text
.minecraft/config/chat-upgrade/chat-upgrade.json
```

主要字段：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `chatInputMode` | `TAKEOVER` / `COMPAT_TEXT_VANILLA` | `TAKEOVER` | 聊天输入/渲染模式。字段缺失时等价于 `TAKEOVER`。 |
| `ciCompatibility` | boolean | `false` | 图片发送是否优先使用受支持的 `[[CICode,...]]` bracket tag；关闭时使用标准 `[[ChatUpgrade,...]]`。 |
| `manualImageReveal` | boolean | `false` | 图片是否点击后加载。 |
| `manualAudioReveal` | boolean | `false` | 音频是否点击后加载。 |
| `manualVideoReveal` | boolean | `false` | 视频是否点击后加载。 |
| `smoothScrollEnabled` | boolean | `true` | 聊天平滑滚动。 |
| `audioVolumePercent` | int | `100` | 音频音量百分比。 |
| `videoVolumePercent` | int | `100` | 视频音量百分比。 |
| `maxReceiveBytes` | int | `2 MiB` | 接收体积上限，最大 `10 MiB`。 |
| `maxUploadBytes` | int | `2 MiB` | 上传体积上限，最大 `10 MiB`。 |
| `uploadMode` | `AUTO` / `SERVER` / `THIRD_PARTY` | `AUTO` | 上传路由模式。 |

## 聊天模式

### TAKEOVER

默认模式。特征：

- 纯文本和附件都进入统一聊天 pipeline。
- 聊天栏内容区由 `RichChatViewport` 自定义渲染。
- 不依赖 phantom 行作为主路径。
- 更适合完整富媒体体验和后续 UI 扩展。

### COMPAT_TEXT_VANILLA

兼容模式。特征：

- 无附件纯文本尽量保留原版输入和显示链路。
- 有附件时仍可使用富媒体能力。
- 适合与其它聊天显示/文本处理类模组共存。

切换命令：

```text
/chatupgrade config inputmode takeover
/chatupgrade config inputmode compat
```

## 上传模式

```text
/chatupgrade config uploadmode auto
/chatupgrade config uploadmode server
/chatupgrade config uploadmode third
```

| 模式 | 说明 |
| --- | --- |
| `auto` | 服务端声明可用时选择服务端直传，否则选择第三方。已选择服务端后如果上传失败，不会在同一次发送中再自动重试第三方。 |
| `server` | 强制服务端直传；服务端能力或上传失败时直接失败，不自动回退第三方。 |
| `third` | 强制第三方上传。 |

## 常用发送命令

| 命令 | 说明 |
| --- | --- |
| `/chatupgrade send <url> [name]` | 发送图片 URL。 |
| `/chatupgrade sendaudio <url> [name]` | 发送音频 URL。 |
| `/chatupgrade sendvideo <url> [name]` | 发送视频 URL。 |
| `/chatupgrade upload folder <path> [name]` | 从本机路径上传图片并发送。 |
| `/chatupgrade upload pick [name]` | 文件选择器选择图片并发送。 |
| `/chatupgrade upload paste [name]` | 从剪贴板读取图片并发送。 |
| `/chatupgrade uploadaudio folder <path> [name]` | 上传本地音频并发送。 |
| `/chatupgrade uploadaudio pick [name]` | 文件选择器选择音频并发送。 |
| `/chatupgrade uploadvideo folder <path> [name]` | 上传本地视频并发送。 |
| `/chatupgrade uploadvideo pick [name]` | 文件选择器选择视频并发送。 |

`name` 多数情况下可省略，默认使用资源类型或文件名。

## 常用配置命令

| 命令 | 说明 |
| --- | --- |
| `/chatupgrade config ci <true|false>` | 切换图片发送使用 `[[CICode,...]]` 还是 `[[ChatUpgrade,...]]` bracket tag。 |
| `/chatupgrade config manual <true|false>` | 图片手动加载。 |
| `/chatupgrade config manualaudio <true|false>` | 音频手动加载。 |
| `/chatupgrade config manualvideo <true|false>` | 视频手动加载。 |
| `/chatupgrade config smoothscroll <true|false>` | 平滑滚动开关。 |
| `/chatupgrade config audiovolume <1-100>` | 音频音量。 |
| `/chatupgrade config videovolume <1-100>` | 视频音量。 |
| `/chatupgrade config maxreceive <1-10>` | 接收上限，单位 MiB。 |
| `/chatupgrade config maxupload <1-10>` | 上传上限，单位 MiB。 |
| `/chatupgrade config reload` | 重新读取配置文件。 |

## 插件命令

| 命令 | 说明 |
| --- | --- |
| `/chatupgrade config plugin status` | 查看 FFmpeg / APNG 插件状态。 |
| `/chatupgrade config plugin load ffmpeg` | 手动加载 FFmpeg。 |
| `/chatupgrade config plugin load apng` | 手动加载 APNG 插件。 |
| `/chatupgrade config plugin load all` | 加载全部插件。 |
| `/chatupgrade config plugin download ffmpeg` | 重新下载 FFmpeg。 |
| `/chatupgrade config plugin download apng` | 重新下载 APNG 插件。 |
| `/chatupgrade config plugin download all` | 重新下载全部插件。 |

## 服务端配置

服务端配置文件：

```text
config/chat-upgrade/server-media.json
```

主要字段：

| 字段 | 说明 |
| --- | --- |
| `enabled` | 是否启用服务端直传与下发。 |
| `storageMode` | `MEMORY` 或 `DISK`。 |
| `diskFolderName` | 磁盘存储目录名。 |
| `maxSingleBytes` | 单媒体最大字节数。 |
| `maxChunkBytes` | 自定义包分块大小。 |
| `maxTotalBytes` | 总容量上限。 |
| `ttlSeconds` | 过期秒数。 |

## Smoke 验证矩阵

| 场景 | 预期 |
| --- | --- |
| TAKEOVER 纯文本 | 结构化发送，viewport 文本渲染。 |
| TAKEOVER 图片 | 图片节点显示，滚动/裁切/点击正常。 |
| TAKEOVER 音频 | 音频播放器显示，播放/循环/进度可用。 |
| TAKEOVER 视频 | 视频节点显示，预览/播放/进度可用。 |
| TAKEOVER 表情 | `[:token]` 显示为行内图片，滚动裁切正确。 |
| COMPAT 纯文本 | 尽量原版输入和显示。 |
| COMPAT 附件 | 富媒体附件仍可显示。 |
| 断开重连 | 状态、pending、缓存按预期清理。 |
| 服务端无结构化支持 | 发送降级 bracket 文本或原版聊天包。 |
| vanilla 接收端 | 收到安全可读文本。 |

## 常见排查入口

| 问题 | 优先检查 |
| --- | --- |
| 消息没显示 | `ChatUpgradeChatPipelineGate`、`RichChatStateStore` 是否写入。 |
| 媒体不显示 | loader 状态、URL、manual reveal、接收上限。 |
| 服务端上传失败 | `server-media.json`、能力包、大小限制、日志。 |
| 兼容模式纯文本异常 | 是否误入 TAKEOVER 增强路径。 |
| 表情不显示 | `owo.json` 映射、`InlineEmojiCodec`、图片加载状态。 |
| 滚动/点击错位 | viewport visible bounds、hit box、scrollPx。 |