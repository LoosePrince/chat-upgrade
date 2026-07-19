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
        boolean showPlayerAvatars,
        boolean doubleLineLayout,
        boolean messageBubbles,
        boolean splitOwnMessages,
        ChatUpgradeConfig.NonPlayerAlignment nonPlayerAlignment,
        int cornerRadius,
        int nodeGap,
        int groupGap,
        int messageGap,
        int identityGutter,
        int avatarSize,
        int bubblePaddingX,
        int contentWidthPercent,
        ContextMenu contextMenu) {

    private static final int TEXT = 0xFFF2F5FA;
    private static final int MUTED = 0xFF9AA6B7;

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
                        withAlpha(appearance.panelBackgroundColor, 240),
                        withAlpha(appearance.panelBackgroundColor, 200),
                        withAlpha(appearance.panelBackgroundColor, 237),
                        0xFF343D4D,
                        TEXT,
                        MUTED,
                        0xFFFFC76B,
                        0xD8181510,
                        0xFF8F6A2C,
                        0xFF718097,
                        appearance.panelBorderEnabled ? appearance.panelBorderWidth : 0),
                new Message(
                        bubbleBackground,
                        bubbleBorder,
                        0xA51D2530,
                        0xC04C5B70,
                        0xB83A3220,
                        0xD29B7A35,
                        0xB83B2024,
                        0xD29D4C55,
                        0xB8233042,
                        0xD05D82B0,
                        0xA525272D,
                        0xB05F6570,
                        TEXT,
                        0xFFD7DEE9,
                        0xFFC8D9F0,
                        0xFFA3A8B0,
                        appearance.bubbleBorderEnabled ? appearance.bubbleBorderWidth : 0),
                new Identity(0xFFDDE7F5, 0xB3FFFFFF),
                new Media(
                        0xD21C1C20,
                        0x80181A1F,
                        0x80281212,
                        0xFFD7DCE6,
                        0xFFD2D2D7,
                        0xFFFF7878,
                        0xFF3A3E48,
                        0xFF4C6284,
                        0xFF444852,
                        0xFF64C8FF,
                        0xD91C1C20),
                new Scrollbar(0xAA526176, 0x60343D4D, 0xCCCB3A33),
                appearance.vanillaStyleInput,
                appearance.showPlayerAvatars,
                appearance.doubleLineLayout,
                appearance.messageBubbles,
                appearance.splitOwnMessages,
                appearance.nonPlayerAlignment,
                appearance.cornerRadius,
                3,
                6,
                2,
                identityGutter,
                avatarSize,
                appearance.messageBubbles ? 4 : 0,
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
                && timeline.groupPosition().startsGroup();
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
            int loadingBackground,
            int pendingBackground,
            int failureBackground,
            int text,
            int muted,
            int failureText,
            int controlBackground,
            int controlActiveBackground,
            int progressTrack,
            int progressFill,
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