# Chat Upgrade

Chat Upgrade 是一个面向 Minecraft 26.x高版本的富媒体聊天模组，支持 Fabric 与 NeoForge。公共逻辑位于 `src/common`，加载器绑定位于 `src/fabric` 和 `src/neoforge`。

## 能力概览

- 在聊天中显示文本、行内表情、图片、音频和视频。
- `TAKEOVER` 模式统一接管富媒体聊天 surface；`COMPAT_TEXT_VANILLA` 模式尽量保留原版纯文本链路。
- 通过结构化协议发送正文、附件、回复和撤回信息。
- 服务端安装并启用媒体服务后，可上传、保存和分块下发媒体。
- 不支持结构化协议的客户端仍可接收 bracket 降级文本或安全可读文本。
- 支持文件选择器、剪贴板和 URL 命令发送媒体。

## 快速开始

1. 将对应加载器和 Minecraft 版本的构建产物安装到客户端。
2. 启动游戏后，使用 `/chatupgrade config plugin status` 检查 FFmpeg 与 APNG 插件状态。
3. 直接发送文本，或使用 `/chatupgrade send <url>` 发送图片。
4. 需要服务端媒体直传时，在服务端配置 `config/chat-upgrade/server-media.json`，再将客户端上传模式设为 `server` 或 `auto`。

配置文件位置：

```text
客户端：.minecraft/config/chat-upgrade/chat-upgrade.json
服务端：config/chat-upgrade/server-media.json
```



## 聊天模式


| 模式                    | 行为                                        |
| --------------------- | ----------------------------------------- |
| `TAKEOVER`            | 默认模式。普通文本和附件进入统一状态、布局和渲染管线。               |
| `COMPAT_TEXT_VANILLA` | 无附件纯文本尽量使用原版输入与显示；附件和 bracket 协议仍保留富媒体能力。 |


```text
/chatupgrade config inputmode takeover
/chatupgrade config inputmode compat
```



## 常用命令

```text
/chatupgrade send <url> [name]
/chatupgrade sendaudio <url> [name]
/chatupgrade sendvideo <url> [name]
/chatupgrade upload pick [name]
/chatupgrade upload paste [name]
/chatupgrade upload folder <path> [name]
/chatupgrade uploadaudio pick [name]
/chatupgrade uploadaudio folder <path> [name]
/chatupgrade uploadvideo pick [name]
/chatupgrade uploadvideo folder <path> [name]
/chatupgrade config uploadmode <auto|server|third>
/chatupgrade config reload
/chatupgrade config plugin status
```

命令、配置字段和服务端参数见[配置、命令与运行验证](./docs/config-commands-and-runtime.md)。

## 构建

环境要求：Java 25；Gradle 使用仓库内 Wrapper 9.5.1。目标版本依赖维护在 `gradle/targets/`。

```powershell
.\gradlew.bat :26.1-fabric:build
.\gradlew.bat :26.1-neoforge:build
.\gradlew.bat :26.2-fabric:build
.\gradlew.bat :26.2-neoforge:build
.\gradlew.bat test
```

开发运行：

```powershell
.\gradlew.bat :26.1-fabric:runClient --stacktrace
.\gradlew.bat :26.1-neoforge:runClient --stacktrace
```

默认构建不嵌入 FFmpeg 平台 native；运行时按当前平台准备。需要离线 fat jar 时显式设置：

```powershell
.\gradlew.bat :26.1-fabric:build -PembedFfmpegNatives=true
```

普通目标产物位于对应目标的 `build/libs/`。发布收集任务会将产物复制到根项目 `build/libs/<mod-version>/<loader>/`。`26.1.1` 和 `26.1.2` 是兼容性测试目标，不作为发布目标。

## 工程结构

```text
src/common/       公共聊天、媒体、协议、服务端逻辑与测试
src/fabric/       Fabric 入口和平台实现
src/neoforge/     NeoForge 入口和平台实现
buildSrc/         公共构建逻辑和版本预处理器
gradle/targets/   Minecraft 目标版本和依赖坐标
docs/             技术文档
```

技术 mod id 和资源命名空间为 `chatupgrade`；配置目录使用可读名称 `chat-upgrade`。

## 技术文档

- [文档入口](./docs/README.md)
- [项目架构](./docs/architecture.md)
- [聊天 Pipeline](./docs/chat-pipeline.md)
- [RichChatViewport](./docs/rich-chat-viewport.md)
- [协议与服务端路由](./docs/protocol-and-routing.md)
- [媒体上传与资源加载](./docs/media-and-upload.md)
- [配置、命令与运行验证](./docs/config-commands-and-runtime.md)
- [兼容性与扩展](./docs/compatibility-and-extension.md)



## 支持矩阵


| Minecraft | Fabric                                | NeoForge      | Java |
| --------- | ------------------------------------- | ------------- | ---- |
| 26.1.x    | Loader 0.18.6，Fabric API 0.145.1+26.1 | 26.1.0.1-beta | 25   |
| 26.2.x    | Loader 0.19.3，Fabric API 0.152.1+26.2 | 26.2.0.1-beta | 25   |


版本号和发布开关以 `gradle/targets/*.properties` 与 `settings.gradle.kts` 为准。