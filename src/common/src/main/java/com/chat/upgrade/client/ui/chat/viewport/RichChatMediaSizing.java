package com.chat.upgrade.client.ui.chat.viewport;

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
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return new RichChatMediaBox(
                    RichChatRenderNodeKind.ATTACHMENT_PENDING,
                    RichChatMediaLayout.cardWidth(safeMaxWidth),
                    RichChatMediaLayout.STATUS_HEIGHT);
        }
        String url = attachment.requireRenderableUrl();
        return switch (attachment.type()) {
            case IMAGE -> measureImage(safeMaxWidth, safeLineHeight, url);
            case AUDIO -> measureAudio(safeMaxWidth, safeLineHeight, url);
            case VIDEO -> measureVideo(safeMaxWidth, safeLineHeight, url);
        };
    }

    private static RichChatMediaBox measureImage(int maxWidth, int fallbackLineHeight, String url) {
        ImageEntry entry = ImageLoader.getIfPresent(url);
        if (entry != null && entry.getState() == ImageEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight);
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
                RichChatMediaLayout.cardWidth(maxWidth),
                RichChatMediaLayout.IMAGE_PLACEHOLDER_HEIGHT);
    }

    private static RichChatMediaBox measureAudio(int maxWidth, int fallbackLineHeight, String url) {
        AudioEntry entry = AudioLoader.getIfPresent(url);
        if (entry != null && entry.getState() == AudioEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight);
        }
        return new RichChatMediaBox(
                RichChatRenderNodeKind.AUDIO,
                RichChatMediaLayout.cardWidth(maxWidth),
                RichChatMediaLayout.AUDIO_HEIGHT);
    }

    private static RichChatMediaBox measureVideo(int maxWidth, int fallbackLineHeight, String url) {
        VideoEntry entry = VideoLoader.getIfPresent(url);
        if (entry != null && entry.getState() == VideoEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight);
        }
        int width = RichChatMediaLayout.cardWidth(maxWidth);
        return new RichChatMediaBox(
                RichChatRenderNodeKind.VIDEO,
                width,
                RichChatMediaLayout.videoHeight(width));
    }

    private static RichChatMediaBox failed(int maxWidth, int fallbackLineHeight) {
        return new RichChatMediaBox(
                RichChatRenderNodeKind.ATTACHMENT_FAILED,
                RichChatMediaLayout.cardWidth(maxWidth),
                Math.max(RichChatMediaLayout.STATUS_HEIGHT, fallbackLineHeight));
    }
}