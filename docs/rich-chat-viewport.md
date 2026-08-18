# RichChatViewport 实现

本文只描述 `TAKEOVER` 的统一聊天 surface。`COMPAT_TEXT_VANILLA` 不读取这条管线的 metrics、坐标或外观快照。

## 核心数据流

```text
RichChatMessage
  -> ChatTimelineProjection
  -> RichChatMessageLayout
  -> ChatScene
  -> ChatSceneRenderer
```

`ChatSurfaceController` 先根据打开状态生成 `ChatSurfaceFrame`，再由 scene 合并 surface、timeline、节点和滚动条。单帧 scene 是渲染和交互共同消费的结果，避免绘制坐标与命中坐标分离。

## 事实层

核心类：`RichChatMessage`、`RichChatStateStore`、`RichChatIngress`。

事实层保存：消息 ID、作者和消息类型、服务端时间、正文、fallback、附件、行内表情 slot、来源以及删除状态。它不保存消息行的屏幕坐标。服务端确认的作者、回复摘要和撤回状态优先于客户端推断。

状态快照通常按 newest-first 保存，timeline projector 转换为 oldest-first 后交给布局层。

## 布局层

核心类：`RichChatLayoutEngine`、`RichChatLayout`、`RichChatMessageLayout`、`RichChatMediaSizing`。

布局层一次生成：

- 文本和 metadata 的行位置。
- 头像、消息背景、气泡和附件区域。
- 节点 bounds、visual bounds、identity bounds 和 metadata bounds。
- 内容可见区域和 `RichChatHitBox`。
- 媒体尺寸、组间距和滚动总高度。

布局使用内容局部坐标；surface 几何和 scissor 使用 GUI 绝对坐标。renderer 不得再次修改节点水平偏移，否则会造成视觉和点击错位。

## 节点与命中框

`RichChatRenderNodeKind` 当前包括：

| 类型 | 用途 |
| --- | --- |
| `TEXT`、`SYSTEM` | 普通和系统文本。 |
| `REPLY`、`DELETED` | 回复摘要和撤回占位。 |
| `IMAGE`、`AUDIO`、`VIDEO` | 三类媒体节点。 |
| `ATTACHMENT_PENDING`、`ATTACHMENT_FAILED` | 异步附件状态。 |

`RichChatHitBox` 只描述当前可见布局中的可交互区域，可覆盖图片预览、播放器控制、进度条、seek 和表情。消息级右键菜单先命中消息 layout，再由 action catalog 根据事实生成动作，不把消息动作伪装成媒体叶子节点。

## 渲染顺序

```text
TAKEOVER gate
  -> ChatSurfaceController.synchronize()
  -> TimelineProjector + LayoutEngine
  -> 创建 ChatScene
  -> 绘制 surface chrome
  -> 开启内容 scissor
  -> 绘制消息背景、身份、文本和媒体
  -> 恢复 pose、关闭 scissor
  -> 绘制不受内容裁切的 chrome 和 scrollbar
```

`ChatComponentRichViewportMixin` 只负责生命周期、pose、裁切和 scene 调用。颜色、尺寸、消息装饰和媒体控制应位于外观快照、布局或 renderer 中。

## 交互与 composer

`RichChatInteractionRouter` 接收主键、次键、滚轮和拖拽，转换为 `ChatGestureTarget` 与 `ChatAction`。右键菜单支持由消息事实决定的回复、复制、提及、隐藏、屏蔽、撤回和调试动作。

composer 由 `ChatComposerState` 管理正文桥接、最多 8 个附件、发送批次和回复目标。发送时正文与附件共享同一个 `replyToMessageId`；schema 2 不可用时不得静默发送成无回复消息。

设置 overlay 的输入优先级高于 timeline、composer、Emoji Picker 和菜单。它使用 baseline/draft 状态，取消或异常退出时恢复基线。

## 外观快照

`ChatAppearanceRuntime` 将可变配置转换为不可变 `ChatAppearanceSnapshot`。布局所需的头像 gutter、双行、本人消息分栏、非玩家对齐、padding、圆角和间距在布局阶段读取；renderer 只消费快照和布局结果。

旧配置中的 `chatTheme` 仅用于识别废弃字段并在加载时重写，不是当前样式 API。新增外观字段必须同时更新默认值、normalize、设置界面、快照和测试。

## 表情与媒体

`InlineEmojiCodec` 将支持的文本标记解码为 `InlineEmojiSlot`；`InlineEmojiLayout` 用 `assets/chatupgrade/font/inline_emoji_slot.json` 提供占位字形，使文本换行和图片叠加共享同一 advance。媒体图片由 `RichChatMediaRenderer` 叠加绘制，命中区域来自同一次布局。

图片、音频和视频均使用异步加载状态。加载完成或失败会使 viewport 失效刷新；断线和客户端停止时由公共清理入口释放缓存、播放器、pending 状态和浮窗。

## 滚动模型

`RichChatViewportState` 使用像素滚动：

| 字段 | 含义 |
| --- | --- |
| `scrollPx` | 目标滚动位置。 |
| `visualScrollPx` | 平滑动画后的绘制位置。 |
| `totalHeight` | 内容总高度。 |
| `visibleHeight` | 内容可视高度。 |
| `bottomPinned` | 是否贴底。 |

滚轮可产生平滑过渡；滚动条拖拽直接定位像素位置。内容裁切和命中计算必须使用同一 visual scroll 值。

## 修改原则

- 新 UI 先进入事实、布局、节点、命中框和 renderer 的职责链。
- 不在 renderer 内重新推断消息位置。
- 不把 phantom/HUD 作为 TAKEOVER 主状态。
- 不让 COMPAT 读取 TAKEOVER 的布局或外观运行时。
- 新异步资源必须定义成功、失败和清理路径，并增加滚动、裁切和缩放测试。