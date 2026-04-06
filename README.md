# Chat upgrade


<div align="center">

![Page views](https://count.getloli.com/@LoosePrince#ChatUpgrade)

[![Fabric](https://img.shields.io/badge/Fabric-loader-141417?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ea7100?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>


基于 **Fabric** 的 Minecraft **客户端**模组：在聊天里识别形如 `[[ChatUpgrade,url=…]]` 的括号载荷，把链接换成简短占位文案，并在聊天栏旁绘制 URL 预览（图片或音频播放器）。

## 功能概要

- **解析与展示**：进服聊天中的括号 URL 载荷 → 占位符 + 异步拉取资源（图片/音频）→ 在对应消息下方预留行高并绘制预览。
- **发送**：客户端命令（如 `/chatupgrade send`、`upload`、`sendaudio`、`uploadaudio`）拼出载荷并发送；可选上传到 Litterbox（约 1 小时有效）再发链接。
- **配置**：`config/chat-upgrade.json` 中的 `ciCompatibility`、`manualImageReveal` 等；支持游戏内写入与重载。
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
| `/chatupgrade send <url> <name>` | 向聊天发送图片载荷；`name` 可省略（默认「图片」）。 |
| `/chatupgrade sendaudio <url> <name>` | 向聊天发送音频载荷；`name` 可省略（默认「音频」）。 |
| `/chatupgrade upload folder <path> <name>` | 从本机路径上传至 Litterbox（约 1 小时有效）再发送。`<path>` 为**第一个参数**（Brigadier 可引用字符串：路径里有空格时用一对 `"` 包成一段即可）；`<name>` 为**第二个**可选参数（可含空格）。例：`/chatupgrade upload folder "D:\My Pictures\a.png"`、`/chatupgrade upload folder "D:\img\a.png" 截图`。无空格的路径也可不写引号。 |
| `/chatupgrade upload pick <name>` | 打开文件选择器选图并上传发送；`name` 可省略。 |
| `/chatupgrade upload paste <name>` | 从剪贴板读取图片并上传发送；`name` 可省略（默认「粘贴」）。 |
| `/chatupgrade uploadaudio folder <path> <name>` | 从本机路径上传音频到 Litterbox 并发送音频载荷。 |
| `/chatupgrade uploadaudio pick <name>` | 打开文件选择器选音频并上传发送。 |
| `/chatupgrade config ci <true 或 false>` | 开关 **CICode** 兼容：`true` 为 `[[CICode,url=…]]`，`false` 为 `[[ChatUpgrade,url=…]]`；写入配置。 |
| `/chatupgrade config manual <true 或 false>` | 开关**手动渲染**：`true` 时需打开聊天后点击 `[图片: …]` 再加载预览；写入配置。 |
| `/chatupgrade config reload` | 从磁盘重新读取 `config/chat-upgrade.json`。 |


配置项与文件位置：**`.minecraft/config/chat-upgrade.json`** 。字段说明：`ciCompatibility`（布尔）、`manualImageReveal`（布尔）。

图像上传扩展名支持：`png/apng/jpg/jpeg/gif/webp/bmp/tif/tiff/jfif/ico`
音频上传扩展名支持：`ogg/wav/mp3/flac/m4a/aac/opus/webm`。  
音频播放能力取决于客户端 Java Sound 可解码格式（不支持的格式会提示加载失败）。

### 外置 ImageIO 插件

- 这是**可选启用**能力：不放插件也能正常使用基础图片预览；仅在你需要 APNG 等扩展格式时再安装。
- 启动时会扫描：**`.minecraft/config/chat-upgrade/libs/`** 。
- 下载一个 **Java ImageIO 插件 jar**（示例：`com.tianscar.imageio:imageio-apng`，文件名类似 `imageio-apng-1.0.1.jar`），放到上述目录后，**重启游戏**生效。
- 推荐下载来源：
  - Maven Central（artifact 页面）：[com.tianscar.imageio:imageio-apng](https://central.sonatype.com/artifact/com.tianscar.imageio/imageio-apng)
  - 直接下载链接示例（1.0.1）：[imageio-apng-1.0.1.jar](https://repo1.maven.org/maven2/com/tianscar/imageio/imageio-apng/1.0.1/imageio-apng-1.0.1.jar)
- 若目录为空或插件加载失败，模组会自动降级为内置格式支持（不影响基础功能）。

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