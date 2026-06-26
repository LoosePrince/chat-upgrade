# 兼容性与扩展指南

这篇文档说明哪些部分是兼容层，哪些部分是 TAKEOVER 主架构，以及后续扩展应该改哪些层。

## 兼容性目标

项目现在有两个明确目标：

1. `TAKEOVER` 提供完整富媒体聊天体验，不为其它聊天显示类模组牺牲主架构。
2. `COMPAT_TEXT_VANILLA` 尽量保留原版纯文本链路，降低与其它聊天类模组冲突。

这两个目标不要混在一起。

## TAKEOVER 的边界

`TAKEOVER` 下：

- 聊天栏外壳仍来自原版。
- 聊天栏内容区由 `RichChatViewport` 接管。
- 事实来源是 `RichChatStateStore`。
- 文本、表情、图片、音频、视频都是 render node。
- 命中和交互由 hit box 决定。
- phantom 行和旧 HUD 不作为主渲染链路。

在聊天栏内容区内，可以自由定义 UI 结构。仍建议遵守：

- 不画出 viewport 裁切区域。
- 不破坏命令输入原版链路。
- 不绕过服务端能力和 fallback 协议。
- 不把 `GuiMessage.Line` 重新作为 TAKEOVER 事实来源。

## COMPAT_TEXT_VANILLA 的边界

兼容模式下：

- 无附件纯文本应尽量回到原版输入/显示。
- 普通文本不应触发 TAKEOVER 的 viewport、emoji、phantom、滚动增强等高风险路径。
- 有附件草稿时仍接管发送。
- 收到结构化附件或 bracket 协议文本时仍保留富媒体显示。

兼容模式是“原版纯文本 + 富媒体兜底”，不是“简化版 TAKEOVER”。

## 旧 phantom / HUD 的位置

旧 phantom/HUD 路径仍存在，但定位已经改变：

| 路径 | 当前定位 |
| --- | --- |
| `UpgradePhantomCoordinator` | 兼容投影或 bracket 协议辅助。 |
| `UpgradePhantomHudLayout` | 兼容路径下的旧 HUD 布局同步。 |
| `GuiMessageLineMixin` | 旧 line 增强和兼容承载。 |
| `RichChatProjectionCoordinator` | 统一状态到兼容投影的过渡层。 |

TAKEOVER 下不要新增依赖这些路径的主功能。

## 添加新渲染节点

例如要添加“回复引用块”“消息操作按钮”“头像”“文件卡片”：

1. 扩展事实层：在 `RichChatMessage` 或附件模型里保存必要数据。
2. 扩展布局层：在 `RichChatLayoutEngine` 里生成新的布局节点。
3. 扩展节点类型：增加或复用 `RichChatRenderNodeKind`。
4. 扩展命中框：需要交互时生成 `RichChatHitBox`。
5. 扩展渲染层：在 `RichChatMediaRenderer` 或新的 renderer 中绘制。
6. 扩展交互层：在 `RichChatInteractionRouter` 中解析点击/hover。
7. 增加验证：滚动、裁切、hover、click、focused/unfocused 都要测。

不要直接在 `ChatComponentMixin` 里画新 UI。Mixin 只负责接入和桥接。

## 添加新附件类型

例如添加文件、位置、贴纸、富文本卡片：

1. 扩展 `InlineResourceType` 或建立新的资源类型模型。
2. 扩展 `RichAttachment` 与 `StructuredAttachment` 的互转。
3. 扩展上传校验和 MIME/扩展名支持。
4. 扩展服务端 metadata 存储和 route descriptor。
5. 扩展 `RichChatMediaSizing` 计算尺寸。
6. 扩展 `RichChatRenderNodeKind` 和渲染器。
7. 扩展 hit box 和点击事件。
8. 更新 bracket fallback 策略，保证不支持结构化的客户端/vanilla 可读。

## 添加协议字段

结构化协议扩展原则：

- 优先增加 schemaVersion 能兼容的字段。
- 新字段必须有安全默认值。
- 服务端不应信任客户端 fallback 文本作为唯一事实。
- 对旧客户端必须有降级路径。
- 发送前继续使用 `canSend(...)` gate。

涉及文件（均在 `src/common`）：

- `src/common/.../net/StructuredChatMessage.java`
- `src/common/.../net/StructuredAttachment.java`
- `src/common/.../net/StructuredChatWireCodec.java`
- `src/common/.../net/ServerMediaPayloads.java`
- `src/common/.../server/ServerChatRouteService.java`
- `src/common/.../client/net/servermedia/ServerMediaNetworking.java`

## 添加新加载器或版本

- **新版本**：在 `gradle/targets/` 增加 properties，在 `settings.gradle.kts` 注册 Stonecutter 目标；若 API 有差异，在 `src/common` 用 `//? if >=26.2` 等预处理指令分支。
- **新加载器**：在 `src/<loader>/` 实现 `PlatformServices`、`NetworkRegistrar`、`NetworkSender`、`CommandAdapter` 并在入口注册；公共逻辑不要直接引用加载器 API。
- **Forge**：`platform` 包已预留抽象，当前未实装。

## 添加配置或命令

配置扩展路径：

1. 在 `ChatUpgradeConfig` 或 `ServerMediaServerConfig` 增加字段和默认值。
2. 在 normalize 阶段修正非法值。
3. 在命令注册中增加设置/查询入口。
4. 如果影响服务端路由或客户端模式，需要在 reload 后刷新上报。
5. 更新 README 和 `docs/config-commands-and-runtime.md`。

## 验证清单

### TAKEOVER

- 纯文本显示。
- 图片显示、点击、预览。
- 音频显示、播放、循环、进度。
- 视频显示、预览、播放、进度。
- 表情显示、换行、滚动、裁切。
- 鼠标滚轮平滑。
- 滚动条拖拽像素定位。
- hover、tooltip、cursor 正确。
- 断开连接后状态清理。

### COMPAT_TEXT_VANILLA

- 无附件纯文本尽量原版。
- 普通文本不触发 TAKEOVER 渲染和滚动增强。
- 有附件草稿仍能发送。
- bracket 文本仍能显示富媒体。
- 结构化附件仍能显示富媒体。

### 多客户端

- TAKEOVER -> TAKEOVER。
- TAKEOVER -> COMPAT。
- TAKEOVER -> bracket 兼容客户端。
- TAKEOVER -> vanilla。
- 服务端结构化 payload 不可用时降级。
- metadata 查询成功/失败。
- 服务端媒体请求成功/失败。

## 设计红线

- 不要把 helper/record/enum 放进 mixin 包，mixin 包只放 mixin 和 accessor。
- TAKEOVER 不要回退到 phantom 作为主数据结构。
- COMPAT 不要无意接管普通纯文本。
- 命令输入不要被富媒体发送接管。
- 不要让 metadata 失败阻断 bracket fallback。