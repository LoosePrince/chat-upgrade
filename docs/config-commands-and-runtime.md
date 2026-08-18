# 配置、命令与运行验证

本文记录当前实现中的用户配置、命令、服务端参数和验证方式。配置字段很多，本文只列出稳定且有实际行为的字段；完整外观字段以 `ChatUpgradeConfig.AppearanceConfig` 为准。

## 环境与目标

| 项目 | 当前值 |
| --- | --- |
| Minecraft | 26.1.x、26.2.x |
| Java | 25 |
| Gradle | Wrapper 9.5.1 |
| Fabric | 26.1 使用 Loader 0.18.6 / API 0.145.1+26.1；26.2 使用 Loader 0.19.3 / API 0.152.1+26.2 |
| NeoForge | 26.1.0.1-beta、26.2.0.1-beta |

依赖坐标、版本范围和发布开关以 `gradle/targets/*.properties` 为准。

## 构建、测试和运行

```powershell
.\gradlew.bat :26.1-fabric:build
.\gradlew.bat :26.1-neoforge:build
.\gradlew.bat :26.2-fabric:build
.\gradlew.bat :26.2-neoforge:build
.\gradlew.bat test
.\gradlew.bat :26.1-fabric:runClient --stacktrace
.\gradlew.bat :26.1-neoforge:runServer --args="--world chat-upgrade-runtime-smoke --port 25575 --nogui"
```

目标任务的常规产物在目标项目 `build/libs/`。发布目标为 `26.1` 和 `26.2`；`26.1.1`、`26.1.2` 的 `release_target=false`，用于兼容性运行检查，不应作为正式发布包。

默认不嵌入五个平台的 FFmpeg native：

```powershell
.\gradlew.bat :26.1-fabric:build -PembedFfmpegNatives=true
```

## 客户端配置

文件：`.minecraft/config/chat-upgrade/chat-upgrade.json`。

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `chatInputMode` | `TAKEOVER` | `TAKEOVER` 或 `COMPAT_TEXT_VANILLA`。 |
| `uploadMode` | `AUTO` | `AUTO`、`SERVER` 或 `THIRD_PARTY`。 |
| `manualImageReveal` | `false` | 图片点击后才加载。 |
| `manualAudioReveal` | `false` | 音频点击后才加载。 |
| `manualVideoReveal` | `false` | 视频点击后才加载。 |
| `smoothScrollEnabled` | `true` | 是否启用平滑滚动。 |
| `debugChatActions` | `false` | 是否显示调试动作。 |
| `ciCompatibility` | `false` | 图片 bracket 发送是否使用 `CICode` 标签。 |
| `maxReceiveBytes` | 2 MiB | 单次接收上限，最多 10 MiB。 |
| `maxUploadBytes` | 2 MiB | 单次上传上限，最多 10 MiB。 |
| `audioVolumePercent` | 100 | 音频音量，命令范围 1–100。 |
| `videoVolumePercent` | 100 | 视频音量，命令范围 1–100。 |
| `chatHistoryEnabled` | `true` | 是否保存客户端聊天历史。 |
| `chatHistoryMaxMessages` | 500 | 客户端历史上限。 |
| `chatPanel` | 左 4、底部 40、宽 360、高 220、自动高度 | TAKEOVER 面板几何。 |

设置面板对配置使用 baseline/draft：打开时复制基线，编辑时预览 draft，保存时规范化并原子写盘；取消、关闭或保存失败不替换全局配置。

## 服务端配置

文件：`config/chat-upgrade/server-media.json`。

| 字段 | 默认 | 作用 |
| --- | --- | --- |
| `enabled` | `true` | 启用服务端媒体上传和下发。 |
| `storageMode` | `MEMORY` | `MEMORY` 或 `DISK`。 |
| `maxSingleBytes` | 2 MiB | 单媒体上限，最多 10 MiB。 |
| `maxChunkBytes` | 32 KiB | 分块大小，规范化范围 1–256 KiB。 |
| `maxTotalBytes` | 200 MiB | 存储总容量，0 表示不设总量上限。 |
| `ttlSeconds` | 3600 | 媒体 TTL，0 表示不过期。 |
| `uploadTimeoutSeconds` | 30 | 上传超时，规范化范围 5–300 秒。 |
| `maxPendingUploadsPerPlayer` | 2 | 单玩家并发上传数。 |
| `maxPendingUploadsGlobal` | 64 | 全局并发上传数。 |
| `allowExternalAttachmentUrls` | `false` | 是否允许外部附件 URL。 |
| `chatHistoryEnabled` | `false` | 是否启用服务端聊天历史。 |
| `chatHistoryMaxMessages` | 500 | 服务端历史上限。 |
| `chatHistoryReplayLimit` | 100 | 单次历史回放上限。 |
| `diskFolderName` | `server-media-store` | DISK 模式下的存储目录名。 |

服务端还对结构化消息、上传包、媒体请求、metadata 写入和历史请求执行速率限制。修改限制时应同步测试拒绝路径。

## 命令

### 发送和上传

```text
/chatupgrade send <url> [name]
/chatupgrade sendaudio <url> [name]
/chatupgrade sendvideo <url> [name]
/chatupgrade upload folder <path> [name]
/chatupgrade upload pick [name]
/chatupgrade upload paste [name]
/chatupgrade uploadaudio folder <path> [name]
/chatupgrade uploadaudio pick [name]
/chatupgrade uploadvideo folder <path> [name]
/chatupgrade uploadvideo pick [name]
/chatupgrade visibility unblock <author>
```

### 配置

```text
/chatupgrade config uploadmode <mode>
/chatupgrade config inputmode takeover|compat
/chatupgrade config ci <true|false>
/chatupgrade config manual <true|false>
/chatupgrade config manualaudio <true|false>
/chatupgrade config manualvideo <true|false>
/chatupgrade config smoothscroll <true|false>
/chatupgrade config debugactions <true|false>
/chatupgrade config audiovolume <1-100>
/chatupgrade config videovolume <1-100>
/chatupgrade config maxreceive <1-10>
/chatupgrade config maxupload <1-10>
/chatupgrade config reload
```

上传模式含义：`auto` 在服务端能力可用时选择服务端，否则选择第三方；`server` 强制服务端；`third` 强制第三方。一次发送已经选定提供方后，失败不会在同一批次自动切换提供方。

### 插件

```text
/chatupgrade config plugin status
/chatupgrade config plugin load <ffmpeg|apng|all>
/chatupgrade config plugin download <ffmpeg|apng|all>
```

FFmpeg 和 APNG 插件位于 `config/chat-upgrade/libs/` 的运行时目录；失败时对应媒体类型不可用，但不应导致整个客户端退出。

## 最小验证矩阵

1. 四个发布目标均执行 `build`。
2. `test` 通过，重点覆盖协议限制、媒体上传、配置迁移和服务端存储。
3. TAKEOVER 测试纯文本、图片、音频、视频、表情、滚动和右键菜单。
4. COMPAT 测试无附件纯文本不进入 TAKEOVER viewport，附件仍可显示。
5. 测试 V2、V1、bracket 和 vanilla 接收端，以及回复、多附件和撤回的降级保护。
6. 测试服务端 MEMORY/DISK、大小上限、TTL、分块、速率限制和断线清理。

## 排错顺序

- 消息不见：检查 pipeline gate、协议限制和 `RichChatStateStore` 入站日志。
- 媒体失败：检查 URL、手动加载开关、客户端接收上限、插件状态和服务端 media capability。
- 上传失败：检查 `uploadMode`、服务端 `enabled`、大小/并发限制和日志。
- 显示或点击错位：检查 layout bounds、visible bounds、scrollPx 和 hit box。
- 配置不生效：执行 `config reload`，确认 JSON 字段名称和 normalize 后的值。