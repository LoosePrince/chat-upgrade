package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.state.ChatMessageKind;

public record ChatThemeTokens(
        Surface surface,
        Message message,
        Identity identity,
        Media media,
        Scrollbar scrollbar) {
    public ChatThemeTokens {
        if (surface == null || message == null || identity == null || media == null || scrollbar == null) {
            throw new IllegalArgumentException("theme token groups must not be null");
        }
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
            int resizeGrip) {
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
            int deletedText) {
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

    public record Identity(
            int fallbackName,
            int avatarBorder) {
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

    public record Scrollbar(
            int thumb,
            int track,
            int newMessageThumb) {
    }
}