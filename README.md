# Chat upgrade

<div align="center">

![Page views](https://count.getloli.com/@LoosePrince#ChatUpgrade)

[![Fabric](https://img.shields.io/badge/Fabric-loader-141417?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ea7100?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>

Chat Upgrade 是一个基于 **Fabric** 的 Minecraft 富媒体聊天模组。

它保留原版聊天栏外壳，同时在默认 `TAKEOVER` 模式下接管聊天栏内容区，用自定义 `RichChatViewport` 渲染文本、行内表情、图片、音频播放器和视频播放器。服务端也安装本模组时，客户端可以把媒体直接上传到服务器，并通过结构化协议分发给其它客户端。

## 主要能力

- **富媒体聊天栏**：在聊天栏内显示图片、音频、视频，不需要弹出独立窗口。
- **TAKEOVER 自定义渲染**：默认模式下，聊天栏内容区由自定义 viewport 渲染，支持可变高度、像素滚动、裁切、hover、点击和 tooltip。
- **兼容文本模式**：`COMPAT_TEXT_VANILLA` 下，无附件纯文本尽量保留原版输入和显示链路，附件仍可走富媒体路径。
- **结构化聊天协议**：新客户端优先发送结构化消息和附件 metadata，旧 `[[ChatUpgrade,...]]` 文本继续作为兜底。
- **服务端媒体直传**：服务端启用后，客户端上传可生成 `chatupgrade://media/...`，其它客户端通过服务端拉取媒体字节。
- **行内表情**：识别 `[:token]`，按 `owo.json` 映射渲染为同高图片。
- **旧协议兼容**：继续解析 `[[ChatUpgrade,url=...]]` 和图片场景下的 `[[CICode,...]]`。

## 聊天模式

| 模式 | 说明 |
| --- | --- |
| `TAKEOVER` | 默认模式。普通文本和附件都进入统一聊天状态层，由 `RichChatViewport` 自定义渲染。 |
| `COMPAT_TEXT_VANILLA` | 兼容模式。无附件纯文本尽量走原版链路，附件和旧协议仍保留富媒体能力。 |

切换命令：

```text
/chatupgrade config inputmode takeover
/chatupgrade config inputmode compat
```

配置文件没有 `chatInputMode` 字段时，运行时等价于 `TAKEOVER`。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `/chatupgrade send <url> <name>` | 发送图片 URL。 |
| `/chatupgrade sendaudio <url> <name>` | 发送音频 URL。 |
| `/chatupgrade sendvideo <url> <name>` | 发送视频 URL。 |
| `/chatupgrade upload pick <name>` | 选择图片并上传发送。 |
| `/chatupgrade upload paste <name>` | 从剪贴板读取图片并上传发送。 |
| `/chatupgrade uploadaudio pick <name>` | 选择音频并上传发送。 |
| `/chatupgrade uploadvideo pick <name>` | 选择视频并上传发送。 |
| `/chatupgrade config uploadmode <auto/server/third>` | 切换上传路由。 |
| `/chatupgrade config reload` | 重新读取客户端配置。 |
| `/chatupgrade plugin status` | 查看 FFmpeg / APNG 插件状态。 |

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

常用配置包括：聊天模式、上传路由、手动触发加载、平滑滚动、音频/视频音量、接收/上传上限等。

## 支持的媒体

| 类型 | 说明 |
| --- | --- |
| 图片 | `png`、`apng`、`jpg`、`jpeg`、`gif`、`webp`、`bmp`、`tif`、`tiff`、`jfif`、`ico` |
| 音频 | 常见音频格式，实际解码能力取决于 FFmpeg。 |
| 视频 | 常见视频格式，实际解码能力取决于 FFmpeg。 |

音频和视频基于 **JavaCPP FFmpeg + OpenAL**。FFmpeg native 和 APNG 插件可由模组自动准备，也可通过 `/chatupgrade plugin ...` 命令检查或重新下载。

## 技术文档

详细架构和实现文档在 [`docs/`](./docs/README.md)：

- [项目总架构](./docs/architecture.md)
- [聊天 Pipeline 与模式边界](./docs/chat-pipeline.md)
- [RichChatViewport 实现](./docs/rich-chat-viewport.md)
- [协议与服务端路由](./docs/protocol-and-routing.md)
- [媒体上传与资源加载](./docs/media-and-upload.md)
- [配置、命令与运行验证](./docs/config-commands-and-runtime.md)
- [兼容性与扩展指南](./docs/compatibility-and-extension.md)

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | `26.1` |
| Fabric Loader | `>= 0.18.6` |
| Java | `>= 25` |
| Fabric API | 见 `gradle.properties` |

## 构建

```powershell
.\gradlew.bat build
```

开发客户端：

```powershell
.\gradlew.bat runClient --stacktrace
```

服务端 smoke：

```powershell
.\gradlew.bat runServer --args="--world chat-upgrade-runtime-smoke --port 25575 --nogui"
```