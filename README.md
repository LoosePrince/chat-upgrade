# Chat upgrade

[![Fabric](https://img.shields.io/badge/Fabric-loader-141417?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ea7100?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
![Page views](https://count.getloli.com/@LoosePrince#ChatUpgrade)

基于 **Fabric** 的 Minecraft **客户端**模组：在聊天里识别形如 `[[ChatUpgrade,url=…]]`（可选 `[[CICode,url=…]]`）的括号载荷，把链接换成简短占位文案，并在聊天栏旁绘制 URL 预览图（下载中 / 失败提示 / 缩放后的贴图）。

## 功能概要

- **解析与展示**：进服聊天中的括号 URL 载荷 → 占位符 + 异步拉取图片 → 在对应消息下方预留行高并绘制预览。
- **发送**：客户端命令（如 `/chatupgrade send`、`upload` 等）拼出载荷并发送；可选上传到 Catbox 再发链接。
- **配置**：`config/chat-upgrade.json` 中的 `ciCompatibility` 控制使用 `CICode` 还是 `ChatUpgrade` 标签；支持游戏内重载相关选项。

## 运行环境

| 项目 | 版本 |
|------|------|
| Minecraft | 26.1 |
| Fabric Loader | ≥ 0.18.6 |
| Java | ≥ 25 |
| Fabric API | 见 `gradle.properties` |

## 构建

```powershell
.\gradlew.bat build
```

开发客户端：

```powershell
.\gradlew.bat runClient
```
