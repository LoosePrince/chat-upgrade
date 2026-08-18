package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ui.chat.state.ChatMessageKind;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjection;

/** Immutable visual and layout values consumed by one rendered frame. */
public record ChatAppearanceSnapshot(
        Surface surface,
        Message message,
        Identity identity,
        Media media,
        Scrollbar scrollbar,
        boolean vanillaStyleInput,
        boolean animationsEnabled,
        boolean showPlayerAvatars,
        boolean avatarFirstLineOnly,
        boolean doubleLineLayout,
        boolean messageBubbles,
        boolean splitOwnMessages,
        ChatUpgradeConfig.NonPlayerAlignment nonPlayerAlignment,
        boolean screenMarginsEnabled,
        int cornerRadius,
        int nodeGap,
        int groupGap,
        int messageGap,
        int identityGutter,
        int avatarSize,
        int bubblePadding,
        int contentWidthPercent,
        ContextMenu contextMenu) {

    public ChatAppearanceSnapshot {
        if (surface == null || message == null || identity == null || media == null
                || scrollbar == null || contextMenu == null) {
            throw new IllegalArgumentException("appearance groups must not be null");
        }
        nonPlayerAlignment = nonPlayerAlignment == null
                ? ChatUpgradeConfig.NonPlayerAlignment.LEFT
                : nonPlayerAlignment;
    }

    public static ChatAppearanceSnapshot from(ChatUpgradeConfig config) {
        ChatUpgradeConfig source = config == null ? ChatUpgradeConfig.get() : config;
        ChatUpgradeConfig.AppearanceConfig appearance = source.appearance == null
                ? ChatUpgradeConfig.defaultAppearance()
                : source.appearance;

        int panelBackground = argb(
                appearance.panelBackgroundColor,
                appearance.panelBackgroundOpacityPercent);
        int panelBorder = appearance.panelBorderEnabled
                ? opaque(appearance.panelBorderColor)
                : 0;
        boolean panelChromeVisible = (panelBackground >>> 24) != 0 || (panelBorder >>> 24) != 0;
        int messageBackground = argb(
                appearance.messageBackgroundColor,
                appearance.messageBackgroundOpacityPercent);
        int bubbleBackground = appearance.messageBubbles
                ? withAlpha(appearance.bubbleColor, 184)
                : 0;
        int bubbleBorder = appearance.messageBubbles && appearance.bubbleBorderEnabled
                ? withAlpha(appearance.bubbleBorderColor, 208)
                : 0;
        int avatarSize = appearance.showPlayerAvatars ? 18 : 0;
        int identityGutter = appearance.showPlayerAvatars ? 24 : 0;

        return new ChatAppearanceSnapshot(
                new Surface(
                        panelBackground,
                        panelBorder,
                        panelBackground,
                        panelBackground,
                        panelBackground,
                        panelChromeVisible ? opaque(appearance.surfaceSeparatorColor) : 0,
                        opaque(appearance.surfaceTitleColor),
                        opaque(appearance.surfaceMutedColor),
                        opaque(appearance.surfaceRestrictedColor),
                        withAlpha(appearance.surfaceRestrictedHudBackgroundColor, 216),
                        opaque(appearance.surfaceRestrictedHudBorderColor),
                        opaque(appearance.surfaceResizeGripColor),
                        appearance.panelBorderEnabled ? appearance.panelBorderWidth : 0),
                new Message(
                        messageBackground,
                        bubbleBackground,
                        bubbleBorder,
                        withAlpha(appearance.messageSystemBackgroundColor, 165),
                        withAlpha(appearance.messageSystemBorderColor, 192),
                        withAlpha(appearance.messageAnnouncementBackgroundColor, 184),
                        withAlpha(appearance.messageAnnouncementBorderColor, 210),
                        withAlpha(appearance.messageErrorBackgroundColor, 184),
                        withAlpha(appearance.messageErrorBorderColor, 210),
                        withAlpha(appearance.messageReplyBackgroundColor, 184),
                        withAlpha(appearance.messageReplyBorderColor, 208),
                        withAlpha(appearance.messageDeletedBackgroundColor, 165),
                        withAlpha(appearance.messageDeletedBorderColor, 176),
                        opaque(appearance.messageTextColor),
                        opaque(appearance.messageSystemTextColor),
                        opaque(appearance.messageReplyTextColor),
                        opaque(appearance.messageDeletedTextColor),
                        appearance.bubbleBorderEnabled ? appearance.bubbleBorderWidth : 0),
                new Identity(
                        opaque(appearance.identityNameColor),
                        withAlpha(appearance.identityAvatarBorderColor, 179)),
                new Media(
                        withAlpha(appearance.mediaCardBackgroundColor, 229),
                        opaque(appearance.mediaCardBorderColor),
                        opaque(appearance.mediaBackgroundColor),
                        withAlpha(appearance.mediaLoadingBackgroundColor, 210),
                        withAlpha(appearance.mediaPendingBackgroundColor, 128),
                        withAlpha(appearance.mediaFailureBackgroundColor, 128),
                        opaque(appearance.mediaTextColor),
                        opaque(appearance.mediaMutedColor),
                        opaque(appearance.mediaFailureTextColor),
                        opaque(appearance.mediaControlBackgroundColor),
                        opaque(appearance.mediaControlHoverBackgroundColor),
                        opaque(appearance.mediaControlActiveBackgroundColor),
                        opaque(appearance.mediaProgressTrackColor),
                        opaque(appearance.mediaProgressFillColor),
                        withAlpha(appearance.mediaScrimColor, 168),
                        withAlpha(appearance.mediaEmojiLoadingBackgroundColor, 217)),
                new Scrollbar(
                        withAlpha(appearance.scrollbarThumbColor, 170),
                        withAlpha(appearance.scrollbarTrackColor, 96),
                        withAlpha(appearance.scrollbarNewMessageThumbColor, 204)),
                appearance.vanillaStyleInput,
                appearance.animationsEnabled,
                appearance.showPlayerAvatars,
                appearance.avatarFirstLineOnly,
                appearance.doubleLineLayout,
                appearance.messageBubbles,
                appearance.splitOwnMessages,
                appearance.nonPlayerAlignment,
                source.chatPanel == null || source.chatPanel.usesScreenMargins(),
                appearance.cornerRadius,
                3,
                appearance.groupGap,
                appearance.messageGap,
                identityGutter,
                avatarSize,
                appearance.messageBubbles ? appearance.bubblePadding : 0,
                appearance.splitOwnMessages ? 90 : 100,
                new ContextMenu(
                        appearance.contextMenuScalePercent,
                        opaque(appearance.contextMenuBackgroundColor),
                        appearance.contextMenuBorderEnabled
                                ? opaque(appearance.contextMenuBorderColor)
                                : 0,
                        appearance.contextMenuBorderEnabled ? appearance.contextMenuBorderWidth : 0,
                        appearance.contextMenuCornerRadius));
    }

    public boolean showIdentity(ChatTimelineProjection timeline) {
        return showPlayerAvatars
                && timeline != null
                && timeline.kind().playerAuthored()
                && (!avatarFirstLineOnly || timeline.startsIdentityGroup());
    }

    public int avatarSize(int messageLineHeight) {
        if (!showPlayerAvatars) {
            return 0;
        }
        return doubleLineLayout ? avatarSize : Math.max(1, messageLineHeight);
    }

    public int identityGutter(int messageLineHeight) {
        int resolvedAvatarSize = avatarSize(messageLineHeight);
        return resolvedAvatarSize <= 0 ? 0 : resolvedAvatarSize + Math.max(0, identityGutter - avatarSize);
    }

    public int avatarCornerRadius(int resolvedAvatarSize) {
        return doubleLineLayout
                ? Math.clamp(cornerRadius, 0, Math.max(0, resolvedAvatarSize / 2))
                : 0;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    private static int argb(int rgb, int opacityPercent) {
        return withAlpha(rgb, Math.round(Math.clamp(opacityPercent, 0, 100) * 2.55F));
    }

    public record Surface(
            int panelBackground,
            int panelBorder,
            int headerBackground,
            int timelineBackground,
            int composerBackground,
            int separator,
            int title,
            int muted,
            int restricted,
            int restrictedHudBackground,
            int restrictedHudBorder,
            int resizeGrip,
            int panelBorderWidth) {
    }

    public record Message(
            int lineBackground,
            int playerBackground,
            int playerBorder,
            int systemBackground,
            int systemBorder,
            int announcementBackground,
            int announcementBorder,
            int errorBackground,
            int errorBorder,
            int replyBackground,
            int replyBorder,
            int deletedBackground,
            int deletedBorder,
            int text,
            int systemText,
            int replyText,
            int deletedText,
            int bubbleBorderWidth) {
        public int background(ChatMessageKind kind) {
            return switch (safeKind(kind)) {
                case PLAYER -> playerBackground;
                case ANNOUNCEMENT -> announcementBackground;
                case ERROR -> errorBackground;
                case SYSTEM, GAME -> systemBackground;
            };
        }

        public int border(ChatMessageKind kind) {
            return switch (safeKind(kind)) {
                case PLAYER -> playerBorder;
                case ANNOUNCEMENT -> announcementBorder;
                case ERROR -> errorBorder;
                case SYSTEM, GAME -> systemBorder;
            };
        }

        public int text(ChatMessageKind kind) {
            return safeKind(kind).playerAuthored() ? text : systemText;
        }

        private static ChatMessageKind safeKind(ChatMessageKind kind) {
            return kind == null ? ChatMessageKind.SYSTEM : kind;
        }
    }

    public record Identity(int fallbackName, int avatarBorder) {
    }

    public record Media(
            int cardBackground,
            int cardBorder,
            int mediaBackground,
            int loadingBackground,
            int pendingBackground,
            int failureBackground,
            int text,
            int muted,
            int failureText,
            int controlBackground,
            int controlHoverBackground,
            int controlActiveBackground,
            int progressTrack,
            int progressFill,
            int scrim,
            int emojiLoadingBackground) {
    }

    public record Scrollbar(int thumb, int track, int newMessageThumb) {
    }

    public record ContextMenu(
            int scalePercent,
            int background,
            int border,
            int borderWidth,
            int cornerRadius) {
    }
}