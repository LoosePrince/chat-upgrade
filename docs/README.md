# 技术文档

本目录只记录当前代码已经实现或明确暴露的行为。命令、配置默认值、协议版本和构建目标应以源码与构建配置为准；如果文档与实现不一致，应先修复文档，再决定是否修改实现。

## 阅读路径

- 了解项目：先读[项目架构](./architecture.md)。
- 修改聊天流程：阅读[聊天 Pipeline](./chat-pipeline.md)和[RichChatViewport](./rich-chat-viewport.md)。
- 修改网络或服务端：阅读[协议与服务端路由](./protocol-and-routing.md)和[媒体上传与资源加载](./media-and-upload.md)。
- 调整配置或验证构建：阅读[配置、命令与运行验证](./config-commands-and-runtime.md)。
- 增加功能或处理兼容问题：阅读[兼容性与扩展](./compatibility-and-extension.md)。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [architecture.md](./architecture.md) | 分层、职责、状态流和加载器边界。 |
| [chat-pipeline.md](./chat-pipeline.md) | 两种聊天模式的输入、路由和接收边界。 |
| [rich-chat-viewport.md](./rich-chat-viewport.md) | 状态、布局、场景、渲染和交互模型。 |
| [protocol-and-routing.md](./protocol-and-routing.md) | schema 1/2、payload、能力协商和降级。 |
| [media-and-upload.md](./media-and-upload.md) | 附件草稿、上传提供方、媒体存储和加载。 |
| [config-commands-and-runtime.md](./config-commands-and-runtime.md) | 配置、命令、构建、测试和排错。 |
| [compatibility-and-extension.md](./compatibility-and-extension.md) | 兼容层、扩展步骤和验证清单。 |

## 当前架构摘要

`TAKEOVER` 的主要事实来源是 `RichChatStateStore`。入站消息经过解析后进入状态层，再由 timeline projector、layout engine 和 scene renderer 生成可见结果。`COMPAT_TEXT_VANILLA` 只对无附件纯文本尽量保留原版链路，不能反向污染 TAKEOVER 的布局和外观状态。

公共代码位于 `src/common`，通过 `com.chat.upgrade.platform` 连接 Fabric 和 NeoForge。当前注册的发布目标是 `26.1`、`26.2` 两个版本，各自包含两个加载器；`26.1.1`、`26.1.2` 仅用于兼容性运行检查。

## 术语约定

- **TAKEOVER**：完整富媒体聊天模式，不表示替换原版命令执行能力。
- **COMPAT**：`COMPAT_TEXT_VANILLA` 的简称，仅表示纯文本兼容路径。
- **结构化消息**：schema 1 的 `StructuredChatMessage` 或 schema 2 的 submission/envelope。
- **bracket**：`[[ChatUpgrade,...]]` 和受支持的 `[[CICode,...]]` 文本协议。
- **媒体引用**：`chat-upgrade://media/<type>/<mediaId>`，只代表服务端媒体，不是外部 HTTP URL。