# 项目总架构

这篇文档说明 Chat Upgrade 当前的整体结构。它不按源码逐段解释，而是按模块职责、数据流和边界组织。

## 项目定位

Chat Upgrade 是 Minecraft 富媒体聊天模组，支持 **Fabric** 与 **NeoForge**，目标版本 **26.1 / 26.2**。用于把聊天消息升级为富媒体聊天：

- 普通文本仍可显示。
- 图片、音频、视频可以作为聊天附件展示。
- `[:token]` 行内表情可以解析并渲染为图片。
- 客户端可通过文件选择、剪贴板、命令等方式发送附件。
- 服务端安装模组后可以承担媒体上传、metadata 存储和多客户端分发。
- 未安装新客户端的接收方仍可以收到安全降级文本或 `[[ChatUpgrade,...]]` / `[[CICode,...]]` bracket 载荷。

## 运行模式

| 模式 | 目标 | 纯文本 | 附件 | 渲染事实来源 |
| --- | --- | --- | --- | --- |
| `TAKEOVER` | 默认完整接管聊天内容区 | 进入统一协议/状态层 | 进入统一协议/状态层 | `RichChatStateStore` |
| `COMPAT_TEXT_VANILLA` | 降低与其它聊天类模组冲突 | 尽量走原版链路 | 保留富媒体路径 | 原版文本链路 + 附件兼容投影 |

默认模式是 `TAKEOVER`。配置文件没有 `chatInputMode` 字段时，运行时等价于 `TAKEOVER`。只有显式配置为 `COMPAT_TEXT_VANILLA` 才进入兼容文本模式。

## 总体层级

```mermaid
flowchart TB
    A[Vanilla Chat Shell\nChatScreen / ChatComponent] --> B[ChatUpgrade Pipeline Gate\nTAKEOVER / COMPAT]
    B --> C[输入与发送层\n文本 / 附件草稿 / 上传]
    B --> D[接收与协议层\n结构化包 / bracket 协议]
    C --> E[统一聊天协议\nStructuredChatMessage / StructuredAttachment]
    D --> F[统一状态层\nRichChatStateStore]
    E --> G[服务端路由\nServerChatRouteService]
    G --> D
    F --> H[Viewport 布局层\nRichChatLayoutEngine]
    H --> I[渲染投影\nRichChatRenderNode / RichChatHitBox]
    I --> J[渲染与交互\nRichChatMediaRenderer / InteractionRouter]
```

## 保留的原版外壳

项目没有完全替换整个聊天 Screen，而是保留 Vanilla Chat Shell。

保留内容包括：

- 聊天栏位置、宽度、高度和缩放。
- 聊天透明度、背景透明度、打开/关闭、focused 状态。
- 输入框、命令输入、最近聊天记录入口。
- 原版聊天设置联动。
- Minecraft GUI 渲染生命周期、鼠标坐标和 tooltip/cursor 桥接。

这部分可以理解为“红色外壳”。

## TAKEOVER 接管的内容区

`TAKEOVER` 接管的是聊天栏内部内容区，也就是“黄色内容”。

接管后，聊天内容不再以原版 `GuiMessage.Line` 为事实来源，而是以统一状态层为事实来源：

```text
RichChatMessage
  -> RichChatMessageLayout
  -> RichChatRenderNode[]
  -> RichChatHitBox[]
  -> RichChatMediaRenderer / RichChatInteractionRouter
```

因此，在聊天栏范围内可以自定义渲染文本、表情、图片、音频、视频、按钮、进度条、卡片等内容。仍需尊重的是外层聊天栏矩形和原版 shell 生命周期。

## 工程与加载器分层

项目采用 **Stonecutter 多版本编排** + **各加载器原生工具链** + **自写平台抽象**，单一 `src/common` 源码树编译到多个目标：

```text
src/
  common/     # 加载器无关逻辑 + mixin + platform 抽象
  fabric/     # Fabric 入口与平台实现
  neoforge/   # NeoForge 入口与平台实现
```

| 层级 | 包/路径 | 职责 |
| --- | --- | --- |
| 公共入口 | `src/common/.../ChatUpgrade.java` | 服务端公共初始化（配置、媒体存储） |
| 平台抽象 | `src/common/.../platform` | `Platform`、`Net`、`CommandSink` 等接口 |
| Fabric 绑定 | `src/fabric/.../fabric/*` | `ModInitializer`、Fabric 网络/命令/事件实现 |
| NeoForge 绑定 | `src/neoforge/.../neoforge/*` | `@Mod`、NeoForge payload/事件实现 |

技术 mod id 为 `chatupgrade`（NeoForge 不允许连字符）；配置目录仍为 `config/chat-upgrade/`。

## 客户端模块（common）
| 初始化与命令 | `src/common/.../client/ChatUpgradeClientBootstrap.java`、`ChatUpgradeCommands.java`；加载器入口见 `src/fabric`、`src/neoforge` | 客户端公共初始化、命令树、插件预热、资源清理。 |
| 配置 | `src/common/.../client/ChatUpgradeConfig.java` | 客户端配置、默认值、范围归一化、保存/重载。 |
| 输入草稿 | `src/common/.../client/ui/chat/input` | 单附件草稿、剪贴板/文件来源、发送控制。 |
| 聊天状态 | `src/common/.../client/ui/chat/state` | 统一消息事实层、投影兼容层。 |
| 富媒体 viewport | `src/common/.../client/ui/chat/viewport` | TAKEOVER 布局、渲染节点、命中框、滚动状态。 |
| 媒体加载 | `src/common/.../client/media` | 图片、音频、视频加载和缓存。 |
| 服务端媒体客户端 | `src/common/.../client/net/servermedia` | 服务端能力、上传/查询 future、结构化消息接收。 |
| Mixin 接入 | `src/common/.../client/mixin` | ChatScreen/ChatComponent 输入、渲染、滚动、点击入口。 |

## 服务端模块（common）

| 模块 | 主要路径 | 职责 |
| --- | --- | --- |
| 服务端入口 | `src/common/.../ChatUpgrade.java` | 公共服务端初始化；各加载器负责 payload/事件注册。 |
| 统一协议 | `src/common/.../net` | payload、结构化聊天消息、附件、wire codec。 |
| 聊天路由 | `src/common/.../server/ServerChatRouteService.java` | 根据接收端能力和模式分发结构化/bracket/vanilla 消息。 |
| 媒体服务 | `src/common/.../server/ServerMediaService.java` | 媒体上传、请求、分块下发。 |
| 附件 metadata | `src/common/.../server/ServerAttachmentService.java` | 附件 metadata 提交、查询和缓存。 |
| 存储 | `src/common/.../server/store` | 内存/磁盘媒体存储。 |
| 服务端 Mixin | `src/common/.../mixin/ServerGamePacketListenerImplMixin.java` | 拦截旧聊天载荷并交给统一服务端路由。 |

## 关键数据流

### TAKEOVER 纯文本发送

```mermaid
sequenceDiagram
    participant Input as ChatScreen 输入框
    participant Send as AttachmentSendController
    participant Net as C2SStructuredChatMessage
    participant Server as ServerChatRouteService
    participant Client as 接收端 RichChatIngress
    participant Viewport as RichChatViewport

    Input->>Send: 回车提交纯文本
    Send->>Net: 优先发送结构化文本
    Net->>Server: C2SStructuredChatMessage
    Server->>Client: 按接收端能力分发
    Client->>Viewport: 写入 RichChatStateStore
```

### TAKEOVER 附件发送

```mermaid
sequenceDiagram
    participant Draft as AttachmentDraft
    participant Upload as UploadRouter
    participant Send as AttachmentSendController
    participant Server as ServerChatRouteService
    participant Store as RichChatStateStore

    Draft->>Upload: 上传图片/音频/视频
    Upload-->>Send: 返回 URL 或 chatupgrade://media
    Send->>Server: 结构化消息 + bracket fallback
    Server->>Store: 接收端写入统一状态
```

### 兼容与 bracket 协议

`[[ChatUpgrade,...]]` 与 `[[CICode,...]]` bracket 文本仍然保留并受支持：

- 新 TAKEOVER 客户端：解析后写入统一状态层。
- 新兼容客户端：走附件兼容投影或普通原版文本链路。
- bracket 兼容客户端：收到 bracket 载荷。
- vanilla 客户端：收到安全可读文本和链接提示。

## 架构原则

- `TAKEOVER` 的事实来源是 `RichChatStateStore`，不是 `GuiMessage.Line`。
- `GuiMessage.Line`、phantom 行和旧 HUD 叠加只作为兼容模式或 bracket fallback 路径。
- 新的聊天内容形态应该扩展 viewport 状态、布局、节点、渲染和交互层。
- 兼容性优先放在 `COMPAT_TEXT_VANILLA`，不要让 TAKEOVER 为其它聊天显示类模组牺牲主架构。