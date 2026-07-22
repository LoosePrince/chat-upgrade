package com.chat.upgrade.client.ui.chat.viewport;

import com.chat.upgrade.client.ChatUpgradeFormatters;

import net.minecraft.client.gui.Font;

/** Shared media-card geometry consumed by sizing, rendering, and hit testing. */
public final class RichChatMediaLayout {
    public static final int PREFERRED_WIDTH = 240;
    public static final int AUDIO_HEIGHT = 58;
    public static final int STATUS_HEIGHT = 38;
    public static final int VIDEO_FOOTER_HEIGHT = 30;
    public static final int IMAGE_MAX_HEIGHT = 180;
    public static final int IMAGE_PLACEHOLDER_HEIGHT = 88;

    private static final int PAD = 7;
    private static final int SMALL_CONTROL = 16;
    private static final int PRIMARY_CONTROL = 24;
    private static final int CONTROL_GAP = 3;

    private RichChatMediaLayout() {
    }

    public static int cardWidth(int maxWidth) {
        return Math.max(1, Math.min(PREFERRED_WIDTH, maxWidth));
    }

    public static int videoHeight(int width) {
        int previewHeight = Math.clamp(Math.round(Math.max(1, width) * 9.0F / 16.0F), 72, 150);
        return previewHeight + VIDEO_FOOTER_HEIGHT;
    }

    public static String displayName(String resourceName, String url) {
        if (resourceName != null && !resourceName.isBlank()) {
            return resourceName.trim();
        }
        String safeUrl = url == null ? "" : url;
        int slash = Math.max(safeUrl.lastIndexOf('/'), safeUrl.lastIndexOf('\\'));
        String base = slash >= 0 && slash + 1 < safeUrl.length()
                ? safeUrl.substring(slash + 1)
                : safeUrl;
        return base.isBlank() ? "—" : base;
    }

    public static ImageSize fitImage(int sourceWidth, int sourceHeight, int maxWidth) {
        int safeWidth = Math.max(1, sourceWidth);
        int safeHeight = Math.max(1, sourceHeight);
        double scale = Math.min(
                1.0D,
                Math.min(
                        Math.max(1, maxWidth) / (double) safeWidth,
                        IMAGE_MAX_HEIGHT / (double) safeHeight));
        return new ImageSize(
                Math.max(1, (int) Math.round(safeWidth * scale)),
                Math.max(1, (int) Math.round(safeHeight * scale)));
    }

    public static AudioGeometry audio(RichChatBounds bounds) {
        int top = bounds.top();
        int right = bounds.right();
        RichChatBounds popout = RichChatBounds.ofSize(
                right - PAD - SMALL_CONTROL,
                top + PAD,
                SMALL_CONTROL,
                SMALL_CONTROL);
        RichChatBounds open = RichChatBounds.ofSize(
                popout.left() - CONTROL_GAP - SMALL_CONTROL,
                top + PAD,
                SMALL_CONTROL,
                SMALL_CONTROL);
        RichChatBounds loop = RichChatBounds.ofSize(
                open.left() - CONTROL_GAP - SMALL_CONTROL,
                top + PAD,
                SMALL_CONTROL,
                SMALL_CONTROL);
        RichChatBounds title = new RichChatBounds(
                bounds.left() + PAD,
                top + PAD,
                Math.max(bounds.left() + PAD, loop.left() - 5),
                top + PAD + SMALL_CONTROL);
        RichChatBounds play = RichChatBounds.ofSize(
                bounds.left() + PAD,
                top + 27,
                PRIMARY_CONTROL,
                PRIMARY_CONTROL);
        RichChatBounds progress = new RichChatBounds(
                play.right() + 8,
                top + 32,
                Math.max(play.right() + 9, right - PAD),
                top + 36);
        RichChatBounds time = new RichChatBounds(
                progress.left(),
                top + 42,
                progress.right(),
                top + 54);
        return new AudioGeometry(bounds, title, play, loop, open, popout, progress, time);
    }

    public static VideoGeometry video(
            RichChatBounds bounds,
            Font font,
            long positionMs,
            long durationMs,
            int rawWidth,
            int rawHeight) {
        int footerTop = Math.max(bounds.top(), bounds.bottom() - VIDEO_FOOTER_HEIGHT);
        RichChatBounds preview = new RichChatBounds(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                footerTop);
        RichChatBounds frame = fitRect(preview, rawWidth, rawHeight);
        RichChatBounds play = RichChatBounds.ofSize(
                bounds.left() + PAD,
                footerTop + 4,
                22,
                22);
        String leftLabel = ChatUpgradeFormatters.formatMs(Math.max(0L, positionMs));
        String rightLabel = ChatUpgradeFormatters.formatMs(Math.max(0L, durationMs));
        int leftWidth = font == null ? 28 : font.width(leftLabel);
        int rightWidth = font == null ? 28 : font.width(rightLabel);
        RichChatBounds leftTime = RichChatBounds.ofSize(
                play.right() + 6,
                footerTop + 11,
                leftWidth,
                10);
        RichChatBounds rightTime = RichChatBounds.ofSize(
                bounds.right() - PAD - rightWidth,
                footerTop + 11,
                rightWidth,
                10);
        RichChatBounds progress = new RichChatBounds(
                leftTime.right() + 6,
                footerTop + 13,
                Math.max(leftTime.right() + 7, rightTime.left() - 6),
                footerTop + 17);
        RichChatBounds title = new RichChatBounds(
                bounds.left() + PAD,
                bounds.top() + PAD,
                Math.max(bounds.left() + PAD, bounds.right() - 30),
                bounds.top() + PAD + 14);
        RichChatBounds open = RichChatBounds.ofSize(
                bounds.right() - PAD - SMALL_CONTROL,
                bounds.top() + PAD,
                SMALL_CONTROL,
                SMALL_CONTROL);
        return new VideoGeometry(bounds, preview, frame, title, open, play, progress, leftTime, rightTime);
    }

    private static RichChatBounds fitRect(RichChatBounds box, int rawWidth, int rawHeight) {
        if (rawWidth <= 0 || rawHeight <= 0 || box.width() <= 0 || box.height() <= 0) {
            return box;
        }
        double scale = Math.min(box.width() / (double) rawWidth, box.height() / (double) rawHeight);
        int width = Math.max(1, (int) Math.round(rawWidth * scale));
        int height = Math.max(1, (int) Math.round(rawHeight * scale));
        int left = box.left() + (box.width() - width) / 2;
        int top = box.top() + (box.height() - height) / 2;
        return RichChatBounds.ofSize(left, top, width, height);
    }

    public record ImageSize(int width, int height) {
    }

    public record AudioGeometry(
            RichChatBounds card,
            RichChatBounds title,
            RichChatBounds play,
            RichChatBounds loop,
            RichChatBounds open,
            RichChatBounds popout,
            RichChatBounds progress,
            RichChatBounds time) {
    }

    public record VideoGeometry(
            RichChatBounds card,
            RichChatBounds preview,
            RichChatBounds frame,
            RichChatBounds title,
            RichChatBounds open,
            RichChatBounds play,
            RichChatBounds progress,
            RichChatBounds leftTime,
            RichChatBounds rightTime) {
    }
}