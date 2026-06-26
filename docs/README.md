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
| [rich-chat-viewport.md](./rich-chat-viewport.md) | TAKEOVER 下聊天栏内容区的状态、布局、渲染、交互模型。 |
| [protocol-and-routing.md](./protocol-and-routing.md) | 结构化聊天协议、媒体 metadata、服务端按客户端能力分发。 |
| [media-and-upload.md](./media-and-upload.md) | 附件草稿、上传路由、服务端媒体、图片/音频/视频加载。 |
| [config-commands-and-runtime.md](./config-commands-and-runtime.md) | 配置文件、常用命令、构建运行、smoke 验证矩阵。 |
| [compatibility-and-extension.md](./compatibility-and-extension.md) | 兼容模式设计、旧 phantom/HUD 位置、扩展新节点/协议的方式。 |

## 当前架构一句话

`TAKEOVER` 模式下，项目保留原版聊天栏外壳，但聊天栏内容区已经由 `RichChatViewport` 接管：统一消息状态进入布局层，再投影成文本、表情、图片、音频、视频等自定义节点，由自定义渲染与交互层处理。

`COMPAT_TEXT_VANILLA` 模式下，普通纯文本尽量回到原版链路，附件和 bracket fallback 继续保留富媒体能力。

工程层面，公共逻辑集中在 `src/common`，通过 `com.chat.upgrade.platform` 与各加载器绑定层同时支持 **Fabric / NeoForge** 以及 **26.1 / 26.2** 目标。