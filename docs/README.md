# Chat Upgrade 技术文档

这个目录用于从架构、协议、运行时和扩展角度说明整个项目。根目录 `README.md` 只保留简短入口，详细实现以这里为准。

## 推荐阅读顺序

### 只想了解项目能做什么

1. [项目总架构](./architecture.md)
2. [配置、命令与运行验证](./config-commands-and-runtime.md)
3. [媒体上传与资源加载](./media-and-upload.md)

### 想维护聊天栏 TAKEOVER 架构

1. [聊天 Pipeline 与模式边界](./chat-pipeline.md)
2. [RichChatViewport 实现](./rich-chat-viewport.md)
3. [兼容性与扩展指南](./compatibility-and-extension.md)

### 想维护服务端协议和多客户端兼容

1. [协议与服务端路由](./protocol-and-routing.md)
2. [媒体上传与资源加载](./media-and-upload.md)
3. [聊天 Pipeline 与模式边界](./chat-pipeline.md)

## 文档列表

| 文档 | 说明 |
| --- | --- |
| [architecture.md](./architecture.md) | 项目整体分层、客户端/服务端/协议/媒体模块关系。 |
| [chat-pipeline.md](./chat-pipeline.md) | `TAKEOVER`、`COMPAT_TEXT_VANILLA`、bracket fallback 的边界和消息流。 |
| [rich-chat-viewport.md](./rich-chat-viewport.md) | TAKEOVER 下完整聊天 surface 的状态、布局、渲染、交互模型。 |
| [protocol-and-routing.md](./protocol-and-routing.md) | 结构化聊天协议、媒体 metadata、服务端按客户端能力分发。 |
| [media-and-upload.md](./media-and-upload.md) | 附件草稿、上传路由、服务端媒体、图片/音频/视频加载。 |
| [config-commands-and-runtime.md](./config-commands-and-runtime.md) | 配置文件、常用命令、构建运行、smoke 验证矩阵。 |
| [compatibility-and-extension.md](./compatibility-and-extension.md) | 兼容模式设计、旧 phantom/HUD 位置、扩展新节点/协议的方式。 |

## 当前架构一句话

`TAKEOVER` 模式下，原版 `ChatScreen` 只保留打开/关闭、焦点、命令建议、历史和发送所需的底层桥接；可见聊天 surface（面板、timeline、composer、菜单、弹层、滚动条与调整手柄）由统一状态、场景、渲染和交互管线完整接管。

`COMPAT_TEXT_VANILLA` 模式下，普通纯文本尽量回到原版链路，附件和 bracket fallback 继续保留富媒体能力。

工程层面，公共逻辑集中在 `src/common`，通过 `com.chat.upgrade.platform` 与各加载器绑定层同时支持 **Fabric / NeoForge** 以及 **26.1 / 26.2** 目标。