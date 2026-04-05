# Chat upgrade


<div align="center">

![Page views](https://count.getloli.com/@LoosePrince#ChatUpgrade)

[![Fabric](https://img.shields.io/badge/Fabric-loader-141417?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ea7100?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>


基于 **Fabric** 的 Minecraft **客户端**模组：在聊天里识别形如 `[[ChatUpgrade,url=…]]`（可选 `[[CICode,url=…]]`）的括号载荷，把链接换成简短占位文案，并在聊天栏旁绘制 URL 预览图（下载中 / 失败提示 / 缩放后的贴图）。

## 功能概要

- **解析与展示**：进服聊天中的括号 URL 载荷 → 占位符 + 异步拉取图片 → 在对应消息下方预留行高并绘制预览。
- **发送**：客户端命令（如 `/chatupgrade send`、`upload` 等）拼出载荷并发送；可选上传到 Catbox 再发链接。
- **配置**：`config/chat-upgrade.json` 中的 `ciCompatibility`、`manualImageReveal` 等；支持游戏内写入与重载。

## 命令

均为 **Fabric 客户端命令**（`/chatupgrade …`），在聊天框输入即可；**发送聊天 / 上传**类子命令需要**已进世界且在线**（否则提示无法发送）。


| 命令 | 说明 |
|------|------|
| `/chatupgrade send <url> <name>` | 向聊天发送图片载荷；`name` 可省略（默认「图片」）。 |
| `/chatupgrade upload folder <path> <name>` | 从本机路径上传至 Catbox 再发送。`<path>` 为**第一个参数**（Brigadier 可引用字符串：路径里有空格时用一对 `"` 包成一段即可）；`<name>` 为**第二个**可选参数（可含空格）。例：`/chatupgrade upload folder "D:\My Pictures\a.png"`、`/chatupgrade upload folder "D:\img\a.png" 截图`。无空格的路径也可不写引号。 |
| `/chatupgrade upload pick <name>` | 打开文件选择器选图并上传发送；`name` 可省略。 |
| `/chatupgrade upload paste <name>` | 从剪贴板读取图片并上传发送；`name` 可省略（默认「粘贴」）。 |
| `/chatupgrade config ci <true 或 false>` | 开关 **CICode** 兼容：`true` 为 `[[CICode,url=…]]`，`false` 为 `[[ChatUpgrade,url=…]]`；写入配置文件。 |
| `/chatupgrade config manual <true 或 false>` | 开关**手动渲染**：`true` 时需打开聊天后点击 `[图片: …]` 再加载预览；写入配置。 |
| `/chatupgrade config reload` | 从磁盘重新读取 `config/chat-upgrade.json`。 |


配置项与文件位置：`**.minecraft/config/chat-upgrade.json`**（开发环境多为 `run/config/chat-upgrade.json`）。字段说明：`ciCompatibility`（布尔）、`manualImageReveal`（布尔）。

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