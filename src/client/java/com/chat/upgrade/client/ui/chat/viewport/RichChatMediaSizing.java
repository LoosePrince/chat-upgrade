package com.chat.upgrade.client.ui.chat.viewport;

import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.ui.chat.UpgradeHudInlinePaint;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;

public final class RichChatMediaSizing {
    private RichChatMediaSizing() {
    }

    public static RichChatMediaBox measure(int maxWidth, int fallbackLineHeight, RichAttachment attachment) {
        int safeMaxWidth = Math.max(1, maxWidth);
        int safeLineHeight = Math.max(1, fallbackLineHeight);
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return new RichChatMediaBox(
                    RichChatRenderNodeKind.ATTACHMENT_PENDING,
                    Math.min(UpgradeHudInlinePaint.AUDIO_WIDTH, safeMaxWidth),
                    safeLineHeight);
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
            return new RichChatMediaBox(
                    RichChatRenderNodeKind.IMAGE,
                    clampWidth(entry.getWidth(), maxWidth),
                    Math.max(1, entry.getHeight()));
        }
        return new RichChatMediaBox(
                RichChatRenderNodeKind.IMAGE,
                Math.min(ImageLoader.PREVIEW_HEIGHT, maxWidth),
                ImageLoader.PREVIEW_HEIGHT);
    }

    private static RichChatMediaBox measureAudio(int maxWidth, int fallbackLineHeight, String url) {
        AudioEntry entry = AudioLoader.getIfPresent(url);
        if (entry != null && entry.getState() == AudioEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight);
        }
        return new RichChatMediaBox(
                RichChatRenderNodeKind.AUDIO,
                Math.min(UpgradeHudInlinePaint.AUDIO_WIDTH, maxWidth),
                UpgradeHudInlinePaint.AUDIO_HEIGHT);
    }

    private static RichChatMediaBox measureVideo(int maxWidth, int fallbackLineHeight, String url) {
        VideoEntry entry = VideoLoader.getIfPresent(url);
        if (entry != null && entry.getState() == VideoEntry.State.FAILED) {
            return failed(maxWidth, fallbackLineHeight);
        }
        return new RichChatMediaBox(
                RichChatRenderNodeKind.VIDEO,
                Math.min(VideoUiLayout.WIDTH, maxWidth),
                VideoUiLayout.HEIGHT);
    }

    private static RichChatMediaBox failed(int maxWidth, int fallbackLineHeight) {
        return new RichChatMediaBox(
                RichChatRenderNodeKind.ATTACHMENT_FAILED,
                Math.min(UpgradeHudInlinePaint.AUDIO_WIDTH, maxWidth),
                fallbackLineHeight);
    }

    private static int clampWidth(int preferredWidth, int maxWidth) {
        return Math.max(1, Math.min(Math.max(1, preferredWidth), Math.max(1, maxWidth)));
    }
}