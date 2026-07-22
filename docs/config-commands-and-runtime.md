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
| `appearance` | object | 见下文 | 面板、消息、输入栏、头像、双行布局、左右分栏、非玩家消息位置、气泡、圆角与右键菜单外观。 |
| `chatPanel` | object | `{left:4,bottomOffset:40,width:360,height:220,automaticHeight:true}` | TAKEOVER 面板的左侧位置、宽度与可选手动高度。默认自动使用屏幕最大可用高度；分离原版输入区时动态避让工具栏、回复预览和原版输入框。 |
| `ciCompatibility` | boolean | `false` | 图片发送是否优先使用受支持的 `[[CICode,...]]` bracket tag；关闭时使用标准 `[[ChatUpgrade,...]]`。 |
| `manualImageReveal` | boolean | `false` | 图片是否点击后加载。 |
| `manualAudioReveal` | boolean | `false` | 音频是否点击后加载。 |
| `manualVideoReveal` | boolean | `false` | 视频是否点击后加载。 |
| `smoothScrollEnabled` | boolean | `true` | 聊天平滑滚动。 |
| `debugChatActions` | boolean | `false` | 是否在 TAKEOVER 消息动作目录中显示“复制调试信息”。 |
| `audioVolumePercent` | int | `100` | 音频音量百分比。 |
| `videoVolumePercent` | int | `100` | 视频音量百分比。 |
| `maxReceiveBytes` | int | `2 MiB` | 接收体积上限，最大 `10 MiB`。 |
| `maxUploadBytes` | int | `2 MiB` | 上传体积上限，最大 `10 MiB`。 |
| `uploadMode` | `AUTO` / `SERVER` / `THIRD_PARTY` | `AUTO` | 上传路由模式。 |

## 聊天模式

### TAKEOVER

默认模式。特征：

- 纯文本和附件都进入统一聊天 pipeline。
- 聊天打开时由 `ChatSurfaceController` 提供完整左下锚定面板；聊天关闭时降级为紧凑 HUD。
- timeline 由共享 `ChatScene -> RichChatLayoutEngine -> ChatSceneRenderer` 管线完成布局、绘制和命中坐标生成。
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

## 设置弹窗与外观快照

聊天面板标题左侧的设置图标会打开居中叠加弹窗。弹窗完整覆盖客户端持久化字段，并按以下分类组织：

| 分类 | 配置范围 |
| --- | --- |
| 外观 | 面板背景/透明度/边框、输入栏合并方式、头像、双行布局、消息气泡、自己的消息左右分栏、非玩家消息位置、面板位置/尺寸、右键菜单密度与样式。 |
| 聊天行为 | 平滑滚动、调试动作。 |
| 媒体 | 图片/音频/视频手动加载、接收上限、音频/视频音量。 |
| 上传与兼容 | 上传模式、上传上限、CI 标签兼容、`TAKEOVER` / `COMPAT_TEXT_VANILLA` 消息管线。 |

配置编辑采用基线与草稿模型：

```text
打开设置
  -> 深拷贝当前配置为 baseline 与 draft
  -> 编辑 draft，并通过 ChatClientConfigRuntime 实时预览
  -> 保存：规范化整份 draft，先原子替换 JSON，成功后再提交全局配置
  -> 保存失败/取消/关闭/异常退出：全局配置保持不变并恢复 baseline 预览
```

`ChatAppearanceSnapshot` 是 renderer 和布局层消费的不可变帧配置。`RichChatLayoutEngine` 负责所有会改变位置的计算，包括头像 gutter、双行元信息、本人消息整体靠右、非玩家消息左/中/右对齐以及附件/命中框平移；renderer 不再二次偏移。

“双行布局”固定表示第一行昵称、时间等元信息，第二行开始消息内容。关闭头像只移除头像绘制和 gutter，不会隐藏昵称。“原版风格输入栏”只控制输入区是否与聊天面板合并，不修改附件、Emoji、清空、发送功能，也不改变 `chatInputMode` 或消息提交管线。两种布局都直接复用 `ChatScreen.input` 的原版 `EditBox`；模组只调整其位置和尺寸，不再维护第二套输入文本、光标、选区或历史状态。

新安装与恢复默认均采用原版视觉语义：面板背景和边框透明、无头像、单行、无气泡、无左右分栏、圆角为 `0`，消息行使用黑色原版背景并与 Minecraft“文本背景不透明度”选项合成。聊天打开状态与关闭后的 HUD 预览共用这套消息背景规则；外观设置中的消息背景颜色和透明度可显式覆盖默认值。

面板仍支持标题拖动和边缘/角落缩放；默认开启自动高度并持续按当前窗口重算。分离输入区时，实际 composer 顶边是自动高度、手动尺寸、拖动和缩放共同遵守的几何下界，因此输入框、回复预览和工具栏出现或变化时都不会与聊天面板重叠。手动修改 `bottomOffset` 或 `height` 会关闭自动高度；重新开启后恢复最大可用高度。旧配置中的 `chatTheme` 是废弃字段：加载时会删除并重写配置，不迁移该字段对应的旧样式，也不存在对应切换命令。

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

本地屏蔽作者后，可使用 `/chatupgrade visibility unblock <authorKey>` 恢复；`authorKey` 与动作反馈中的身份标识一致。

## 常用配置命令

| 命令 | 说明 |
| --- | --- |
| `/chatupgrade config ci <true|false>` | 切换图片发送使用 `[[CICode,...]]` 还是 `[[ChatUpgrade,...]]` bracket tag。 |
| `/chatupgrade config manual <true|false>` | 图片手动加载。 |
| `/chatupgrade config manualaudio <true|false>` | 音频手动加载。 |
| `/chatupgrade config manualvideo <true|false>` | 视频手动加载。 |
| `/chatupgrade config smoothscroll <true|false>` | 平滑滚动开关。 |
| `/chatupgrade config debugactions <true|false>` | 显示或隐藏 TAKEOVER 消息调试动作。 |
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
| TAKEOVER 消息右键菜单 | 仅当前可见消息可打开菜单；回复、复制和本人消息撤回项按消息事实显示。 |
| TAKEOVER 回复发送 | composer 显示目标摘要；纯文本和附件都携带同一 `replyToMessageId`，发送成功后清除目标。 |
| TAKEOVER 回复降级保护 | V2 不可用时不把回复静默发送成无回复语义的 legacy/bracket/vanilla 消息。 |
| TAKEOVER 多附件发送 | 最多 8 个附件显示为独立 chip；未上传项并发上传，全部成功后以单条结构化消息按原顺序发送；失败项不会卡在上传中。 |
| TAKEOVER 多附件降级保护 | V2/V1 结构化发送都不可用时保留已上传草稿并报错，不退回只保留首附件语义的 bracket 路由。 |
| TAKEOVER 回复期间追加附件 | 上传批次只消费发送开始时的草稿和回复目标；期间新增的附件与新回复目标保留。 |
| TAKEOVER 设置草稿 | 打开后实时预览；保存提交整份配置并落盘；取消、关闭或异常退出恢复打开时基线。 |
| TAKEOVER 双行与头像 | 双行开启时每条玩家消息先显示昵称/时间元信息，再显示正文；关闭头像后昵称仍存在且不保留 gutter。 |
| TAKEOVER 消息对齐 | 本人消息的头像、元信息、正文、附件、气泡和命中框整体靠右；非玩家消息按左/中/右配置整体平移。 |
| TAKEOVER 原版风格输入栏 | 只把原版 `EditBox` 和同功能紧凑工具栏移到屏幕底部；发送、附件、Emoji、清空、命令与消息管线不变。 |
| TAKEOVER 身份头像 | 可解析玩家 UUID 时绘制皮肤头部与帽层；纹理不可用时稳定回退到色块/glyph。 |
| TAKEOVER 面板几何 | 标题拖动、边缘/角落缩放和窗口尺寸变化后保持在屏幕内，并持久化 `chatPanel`。 |
| COMPAT 纯文本 | 继续使用原版 `ChatComponent -> GuiMessage` 布局和绘制，不受 TAKEOVER metrics、坐标与外观快照影响。 |
| COMPAT 附件 | 富媒体附件仍可显示。 |
| 断开重连 | 状态、pending、缓存按预期清理。 |
| 服务端无结构化支持 | 单附件且无回复时可降级 bracket；多附件或回复消息保留草稿并明确失败。 |
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