package com.chat.upgrade.client.ui.chat.viewport;

import com.chat.upgrade.client.ChatClientConfigRuntime;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;

public final class RichChatMediaSizing {
    private RichChatMediaSizing() {
    }

    public static RichChatMediaBox measure(int maxWidth, int fallbackLineHeight, RichAttachment attachment) {
        int safeMaxWidth = Math.max(1, maxWidth);
        int safeLineHeight = Math.max(1, fallbackLineHeight);
        boolean compact = ChatClientConfigRuntime.uiPreferences().compactMediaCards();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return new RichChatMediaBox(
                    RichChatRenderNodeKind.ATTACHMENT_PENDING,
                    RichChatMediaLayout.imageCardWidth(safeMaxWidth),
                    RichChatMediaLayout.STATUS_HEIGHT);
        }
        String url = attachment.requireRenderableUrl();
        return switch (attachment.type()) {
            case IMAGE -> measureImage(safeMaxWidth, safeLineHeight, url);
            case AUDIO -> measureAudio(safeMaxWidth, safeLineHeight, url, compact);
            case VIDEO -> measureVideo(safeMaxWidth, safeLineHeight, url, compact);
        };
    }

    private static RichChatMediaBox measureImage(int maxWidth, int fallbackLineHeight, String url) {
        ImageEntry entry = ImageLoader.getIfPresent(url);
        if (entry != null && entry.getState() == ImageEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight, false);
        }
        if (entry != null && entry.isLoaded() && entry.getWidth() > 0 && entry.getHeight() > 0) {
            RichChatMediaLayout.ImageSize size = RichChatMediaLayout.fitImage(
                    entry.getWidth(),
                    entry.getHeight(),
                    maxWidth);
            return new RichChatMediaBox(
                    RichChatRenderNodeKind.IMAGE,
                    size.width(),
                    size.height());
        }
        return new RichChatMediaBox(
                RichChatRenderNodeKind.IMAGE,
                RichChatMediaLayout.imageCardWidth(maxWidth),
                RichChatMediaLayout.IMAGE_PLACEHOLDER_HEIGHT);
    }

    private static RichChatMediaBox measureAudio(
            int maxWidth,
            int fallbackLineHeight,
            String url,
            boolean compact) {
        AudioEntry entry = AudioLoader.getIfPresent(url);
        if (entry != null && entry.getState() == AudioEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight, true);
        }
        return new RichChatMediaBox(
                RichChatRenderNodeKind.AUDIO,
                RichChatMediaLayout.playerCardWidth(maxWidth),
                compact ? RichChatMediaLayout.COMPACT_AUDIO_HEIGHT : RichChatMediaLayout.AUDIO_HEIGHT);
    }

    private static RichChatMediaBox measureVideo(
            int maxWidth,
            int fallbackLineHeight,
            String url,
            boolean compact) {
        VideoEntry entry = VideoLoader.getIfPresent(url);
        if (entry != null && entry.getState() == VideoEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight, true);
        }
        int width = RichChatMediaLayout.playerCardWidth(maxWidth);
        return new RichChatMediaBox(
                RichChatRenderNodeKind.VIDEO,
                width,
                RichChatMediaLayout.videoHeight(width, compact));
    }

    private static RichChatMediaBox failed(int maxWidth, int fallbackLineHeight, boolean playerCard) {
        return new RichChatMediaBox(
                RichChatRenderNodeKind.ATTACHMENT_FAILED,
                playerCard
                        ? RichChatMediaLayout.playerCardWidth(maxWidth)
                        : RichChatMediaLayout.imageCardWidth(maxWidth),
                Math.max(RichChatMediaLayout.STATUS_HEIGHT, fallbackLineHeight));
    }
}