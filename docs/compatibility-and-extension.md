# 兼容性与扩展

## 兼容目标

项目把兼容性分成两层：

1. `TAKEOVER` 提供完整富媒体聊天体验。
2. `COMPAT_TEXT_VANILLA` 保护无附件纯文本的原版输入和显示路径。

兼容模式不是简化版 TAKEOVER，也不应读取 TAKEOVER 的布局指标。

## 旧路径的定位

旧 phantom、HUD 和 `GuiMessage.Line` 增强仍可能出现在 bracket 或旧客户端兼容路径，但不属于 TAKEOVER 的主事实来源。新功能不应直接添加到这些路径；Mixin 只做生命周期、事件和平台桥接。

## 增加渲染节点

按以下顺序修改：

1. 在消息或附件事实模型中增加数据。
2. 在 `RichChatLayoutEngine` 生成尺寸、位置和可见边界。
3. 增加或复用 `RichChatRenderNodeKind`。
4. 需要交互时生成 `RichChatHitBox`。
5. 在 scene renderer 或媒体 renderer 中绘制。
6. 在 interaction router 和 action executor 中处理点击、悬停或菜单动作。
7. 为打开/关闭、滚动、裁切、缩放和不可见区域命中增加测试。

不要在 `ChatComponentMixin` 中直接实现新的 UI 或布局规则。

## 增加附件类型

至少需要同步修改：

- `InlineResourceType` 或新的附件模型。
- `StructuredAttachment` 与 `RichAttachment` 的转换。
- 上传校验、MIME 类型和显示名处理。
- 服务端 metadata、存储和 route descriptor。
- `RichChatMediaSizing`、render node 和 renderer。
- bracket fallback，确保旧客户端仍能读到安全文本。

如果附件需要异步加载，必须定义 pending、成功、失败和清理状态，并确保失败不会阻塞其它消息。

## 扩展协议

- schema 1 (`StructuredChatMessage`) 只用于旧结构化客户端。
- schema 2 submission/envelope 承载当前新增字段。
- 新字段必须有安全默认值，且通过 `StructuredChatProtocolLimits` 限制长度、数量和嵌套深度。
- 客户端提交中的作者、消息 ID、时间和撤回权限都不能作为可信事实。
- 需要旧客户端可读时，必须同时定义 envelope 到 bracket/vanilla 的投影。
- 修改 schema 后同步更新 payload codec、接收校验、测试和本目录协议文档。

## 增加版本或加载器

### 新 Minecraft 版本

1. 在 `gradle/targets/<version>.properties` 写入 Minecraft、Loader、API 和 Java 依赖。
2. 在 `settings.gradle.kts` 通过 `mc(...)` 或 `mcCompat(...)` 注册。
3. 用 `//? if ...` 处理 API 差异，并验证 Fabric、NeoForge 两条构建路径。
4. 更新 README 支持矩阵和构建验证记录。

### 新加载器

在 `src/<loader>` 实现入口、`PlatformServices`、`NetworkRegistrar`、`NetworkSender` 和 `CommandAdapter`。公共代码不得直接引用新加载器 API。

## 配置与命令扩展

增加字段时必须完成：默认值、normalize、JSON 读写、命令入口（若需要）、设置界面（若面向用户）、文档和测试。服务端配置变更还要检查权限、大小限制、速率限制和重载行为。

## 验证清单

- 构建：目标版本的 `build`、公共 `test`。
- 模式：TAKEOVER 纯文本/附件；COMPAT 纯文本/附件。
- 协议：V2、V1、bracket、vanilla；回复、多附件、撤回和能力缺失。
- 媒体：上传成功/失败、手动加载、大小上限、服务端分块、TTL 和断线清理。
- UI：滚动、裁切、缩放、hover、点击、右键菜单和配置取消回滚。
- 安全：非法 schema、超长 JSON、超限附件、错误 mediaId 和未授权撤回。