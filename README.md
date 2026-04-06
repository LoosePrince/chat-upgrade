# Chat upgrade


<div align="center">

![Page views](https://count.getloli.com/@LoosePrince#ChatUpgrade)

[![Fabric](https://img.shields.io/badge/Fabric-loader-141417?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ea7100?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>


基于 **Fabric** 的 Minecraft **客户端**模组：在聊天中识别形如 `[[ChatUpgrade,url=…]]` 的载荷文本，将链接替换为占位符，并在聊天栏内联渲染资源预览（图片、音频播放器、视频播放器）。

## 功能概要

- **解析与展示**：进服聊天中的括号 URL 载荷 → 占位符 + 异步拉取资源（图片/音频/视频）→ 在对应消息下方预留行高并绘制预览。
- **发送**：客户端命令（如 `/chatupgrade send`、`upload`、`sendaudio`、`uploadaudio`、`sendvideo`、`uploadvideo`）拼出载荷并发送；可选上传到 Litterbox（约 1 小时有效）再发链接。
- **配置**：`config/chat-upgrade/chat-upgrade.json` 支持协议兼容、手动触发渲染、接收/上传上限、音频/视频音量；支持游戏内写入与重载。
- **ChatImage兼容**：你可以切换到 `ChatImage兼容` 模式以发送 [ChatImage](https://www.mcmod.cn/class/9111.html) 格式的图片

## 特点

### 直接在聊天栏中渲染

- **特点**：预览图走与聊天 HUD **同一套**绘制路径，按消息行对齐、随聊天区域滚动与透明度变化，而不是单独弹窗或全屏覆盖层。
- **优势**：阅读上下文连贯，不占额外 UI 层级；占位文案与预览在**同一消息**上完成替换与绘制，体验与原生聊天一致。
- **技术方案**：通过 Mixin 在 `ChatComponent` 提取/绘制状态时绑定 `GuiGraphicsExtractor` 作用域，在对应消息行位置调用 GUI 管线贴图与填充（如 `RenderPipelines.GUI_TEXTURED`），使预览成为聊天渲染流水线的延伸。

### 高清渲染

- **特点**：贴图分辨率按 **窗口帧缓冲像素 ÷ GUI 逻辑尺寸** 估算密度，并在该密度上再乘固定**超采样**系数，使纹理纹素数高于「仅按逻辑像素」时的需求。
- **优势**：高分辨率窗口、高 GUI 缩放或缩略显示时，预览仍接近**物理像素级**清晰度，减轻把小图拉大造成的模糊。
- **技术方案**：加载时用 `NativeImage` 一次缩放到目标纹素尺寸并注册 `DynamicTexture`；绘制时 **绘制矩形**仍为逻辑宽高，**纹理宽高**为实际纹素，`blit` 将高分辨率纹理映射到固定 GUI 矩形实现缩小采样；窗口或 GUI 缩放变化时清空纹理缓存，按新比例重新生成。

## 命令

均为 **Fabric 客户端命令**（`/chatupgrade …`），在聊天框输入即可。


| 命令 | 说明 |
|------|------|
| `/chatupgrade send <url> <name>` | 向聊天发送图片载荷；`url` 是图片链接；`name` 可省略（默认「图片」）。 |
| `/chatupgrade sendaudio <url> <name>` | 向聊天发送音频载荷；`url` 是音频链接；`name` 可省略（默认「音频」）。 |
| `/chatupgrade sendvideo <url> <name>` | 向聊天发送视频载荷；`url` 是视频链接；`name` 可省略（默认「视频」）。 |
| `/chatupgrade upload folder <path> <name>` | 从本机路径上传至 Litterbox（约 1 小时有效）再发送。`<path>` 为路径；`<name>` 可省略（默认「音频」）。|
| `/chatupgrade upload pick <name>` | 打开文件选择器选图并上传发送；`name` 可省略。 |
| `/chatupgrade upload paste <name>` | 从剪贴板读取图片并上传发送；`name` 可省略（默认「粘贴」）。 |
| `/chatupgrade uploadaudio folder <path> <name>` | 从本机路径上传音频到 Litterbox 并发送音频载荷。 |
| `/chatupgrade uploadaudio pick <name>` | 打开文件选择器选音频并上传发送。 |
| `/chatupgrade uploadvideo folder <path> <name>` | 从本机路径上传视频到 Litterbox 并发送视频载荷。 |
| `/chatupgrade uploadvideo pick <name>` | 打开文件选择器选视频并上传发送。 |
| `/chatupgrade config ci <true 或 false>` | 开关 **CICode** 图片兼容：`true` 时仅图片发送 `[[CICode,url=…]]`，音频/视频仍发送 `[[ChatUpgrade,url=…]]`；`false` 时全部发送 `[[ChatUpgrade,url=…]]`；写入配置。 |
| `/chatupgrade config manual <true 或 false>` | 开关**图片手动触发渲染**：`true` 时需点击 `[图片: …]` 后才加载预览；写入配置。 |
| `/chatupgrade config manualaudio <true 或 false>` | 开关**音频手动触发渲染**：`true` 时需点击 `[音频: …]` 后才加载预览；写入配置。 |
| `/chatupgrade config manualvideo <true 或 false>` | 开关**视频手动触发渲染**：`true` 时需点击 `[视频: …]` 后才加载预览；写入配置。 |
| `/chatupgrade config audiovolume <1-100>` | 设置音频播放音量百分比（1~100）；写入配置并立即作用于已加载音频会话。 |
| `/chatupgrade config videovolume <1-100>` | 设置视频音量百分比（1~100）；写入配置。 |
| `/chatupgrade config maxreceive <1-10>` | 设置接收体积上限（MiB）；写入配置。 |
| `/chatupgrade config maxupload <1-10>` | 设置上传体积上限（MiB）；写入配置。 |
| `/chatupgrade config reload` | 从磁盘重新读取 `config/chat-upgrade/chat-upgrade.json`。 |
| `/chatupgrade plugin status` | 查看 FFmpeg / APNG 插件状态（是否就绪、是否已尝试、jar 是否存在）。 |
| `/chatupgrade plugin load ffmpeg` | 手动触发 FFmpeg 加载（不强制重新下载）。 |
| `/chatupgrade plugin load apng` | 手动触发 APNG 插件加载（不强制重新下载）。 |
| `/chatupgrade plugin load all` | 手动触发 FFmpeg + APNG 加载（不强制重新下载）。 |
| `/chatupgrade plugin download ffmpeg` | 强制重新下载并加载 FFmpeg（会删除本地对应 jar 后重下）。 |
| `/chatupgrade plugin download apng` | 强制重新下载并加载 APNG 插件（会删除本地 jar 后重下）。 |
| `/chatupgrade plugin download all` | 强制重新下载并加载 FFmpeg + APNG。 |


配置文件位置：**`.minecraft/config/chat-upgrade/chat-upgrade.json`**

配置项说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `ciCompatibility` | `boolean` | `true` 时仅图片发送优先使用 `[[CICode,...]]`（音频/视频发送仍为 `[[ChatUpgrade,...]]`）；解析阶段始终兼容 `CICode` 与 `ChatUpgrade`。 |
| `manualImageReveal` | `boolean` | 图片改为点击占位符后才触发加载。 |
| `manualAudioReveal` | `boolean` | 音频改为点击占位符后才触发加载。 |
| `manualVideoReveal` | `boolean` | 视频改为点击占位符后才触发加载。 |
| `audioVolumePercent` | `int(1~100)` | 音频播放音量百分比。 |
| `videoVolumePercent` | `int(1~100)` | 视频播放音量百分比。 |
| `maxReceiveBytes` | `int` | 接收上限（字节），最大 10 MiB。 |
| `maxUploadBytes` | `int` | 上传上限（字节），最大 10 MiB。 |

图像上传扩展名支持：`png/apng/jpg/jpeg/gif/webp/bmp/tif/tiff/jfif/ico`
音频上传扩展名支持：`ogg/wav/mp3/flac/m4a/aac/opus/webm`。  
音频播放能力取决于客户端 Java Sound 可解码格式（不支持的格式会提示加载失败）。
视频上传扩展名支持：`mp4/webm/mov/mkv/m4v/avi`。  
视频解码基于 **JavaCPP FFmpeg**，`mp4` 为必测路径；其他格式按解码器能力尽可能支持。
默认发布包不内置 FFmpeg native：首次启动会自动下载当前平台整包到 `config/chat-upgrade/libs/`，并将 native 按 `java.library.path` 规则释放到 Minecraft 的 `...-natives` 目录后启用（下载失败会记录日志并导致视频不可用）。
发布包不内置 imageio-apng 插件：首次启动会自动下载到 `config/chat-upgrade/libs/`，并由模组在启动阶段通过外置插件加载器注册到 ImageIO SPI（下载失败会记录日志并导致 APNG 不可用）。

## 运行环境


| 项目            | 版本                    |
| ------------- | --------------------- |
| Minecraft     | 26.1                  |
| Fabric Loader | ≥ 0.18.6              |
| Java          | ≥ 25                  |
| Fabric API    | 见 `gradle.properties` |


## 构建

```powershell
.\gradlew.bat build
```

开发客户端：

```powershell
.\gradlew.bat runClient
```