package com.chat.upgrade.client.ui.chat.viewport;

import com.chat.upgrade.client.ChatUpgradeFormatters;

import net.minecraft.client.gui.Font;

/** Shared media-card geometry consumed by sizing, rendering, and hit testing. */
public final class RichChatMediaLayout {
    public static final int IMAGE_PREFERRED_WIDTH = 240;
    public static final int PLAYER_PREFERRED_WIDTH = 132;
    public static final int AUDIO_HEIGHT = 42;
    public static final int COMPACT_AUDIO_HEIGHT = 30;
    public static final int STATUS_HEIGHT = 28;
    public static final int VIDEO_FOOTER_HEIGHT = 24;
    public static final int IMAGE_MAX_HEIGHT = 180;
    public static final int IMAGE_PLACEHOLDER_HEIGHT = 88;

    private static final int PAD = 6;
    private static final int SMALL_CONTROL = 14;
    private static final int PRIMARY_CONTROL = 18;
    private static final int CONTROL_GAP = 2;

    private RichChatMediaLayout() {
    }

    public static int imageCardWidth(int maxWidth) {
        return Math.max(1, Math.min(IMAGE_PREFERRED_WIDTH, maxWidth));
    }

    public static int playerCardWidth(int maxWidth) {
        return Math.max(1, Math.min(PLAYER_PREFERRED_WIDTH, maxWidth));
    }

    public static int videoHeight(int width, boolean compact) {
        int previewHeight = Math.clamp(Math.round(Math.max(1, width) * 9.0F / 16.0F), 54, 96);
        return previewHeight + (compact ? 0 : VIDEO_FOOTER_HEIGHT);
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

    public static AudioGeometry audio(RichChatBounds bounds, boolean compact) {
        int top = bounds.top();
        int right = bounds.right();
        if (compact) {
            RichChatBounds play = RichChatBounds.ofSize(
                    bounds.left() + PAD,
                    top + 2,
                    26,
                    26);
            RichChatBounds menu = RichChatBounds.ofSize(
                    right - PAD - 10,
                    top + 2,
                    10,
                    10);
            RichChatBounds title = new RichChatBounds(
                    play.right() + 5,
                    top + 2,
                    Math.max(play.right() + 6, menu.left() - 3),
                    top + 12);
            RichChatBounds durationTime = RichChatBounds.ofSize(
                    right - PAD - 28,
                    top + 17,
                    28,
                    10);
            RichChatBounds progress = new RichChatBounds(
                    play.right() + 5,
                    top + 20,
                    Math.max(play.right() + 6, durationTime.left() - 4),
                    top + 24);
            RichChatBounds empty = RichChatBounds.ofSize(progress.left(), top + 17, 0, 0);
            return new AudioGeometry(
                    bounds,
                    title,
                    play,
                    empty,
                    empty,
                    menu,
                    progress,
                    empty,
                    durationTime);
        }
        RichChatBounds popout = RichChatBounds.ofSize(
                right - PAD - SMALL_CONTROL,
                top + 5,
                SMALL_CONTROL,
                SMALL_CONTROL);
        RichChatBounds open = RichChatBounds.ofSize(
                popout.left() - CONTROL_GAP - SMALL_CONTROL,
                top + 5,
                SMALL_CONTROL,
                SMALL_CONTROL);
        RichChatBounds loop = RichChatBounds.ofSize(
                open.left() - CONTROL_GAP - SMALL_CONTROL,
                top + 5,
                SMALL_CONTROL,
                SMALL_CONTROL);
        RichChatBounds title = new RichChatBounds(
                bounds.left() + PAD,
                top + 5,
                Math.max(bounds.left() + PAD, loop.left() - 4),
                top + 15);
        RichChatBounds play = RichChatBounds.ofSize(
                bounds.left() + PAD,
                top + 20,
                PRIMARY_CONTROL,
                PRIMARY_CONTROL);
        RichChatBounds progress = new RichChatBounds(
                play.right() + 6,
                top + 25,
                Math.max(play.right() + 7, right - PAD),
                top + 29);
        RichChatBounds currentTime = new RichChatBounds(
                progress.left(),
                top + 31,
                progress.right(),
                top + 41);
        RichChatBounds empty = RichChatBounds.ofSize(progress.right(), top + 31, 0, 0);
        return new AudioGeometry(bounds, title, play, loop, open, popout, progress, currentTime, empty);
    }

    public static VideoGeometry video(
            RichChatBounds bounds,
            Font font,
            long positionMs,
            long durationMs,
            int rawWidth,
            int rawHeight,
            boolean compact) {
        int footerTop = compact
                ? bounds.bottom()
                : Math.max(bounds.top(), bounds.bottom() - VIDEO_FOOTER_HEIGHT);
        RichChatBounds preview = new RichChatBounds(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                footerTop);
        RichChatBounds frame = fitRect(preview, rawWidth, rawHeight);
        RichChatBounds play = compact
                ? RichChatBounds.ofSize(
                        bounds.left() + Math.max(0, (bounds.width() - 26) / 2),
                        bounds.top() + Math.max(0, (bounds.height() - 26) / 2),
                        26,
                        26)
                : RichChatBounds.ofSize(
                        bounds.left() + PAD,
                        footerTop + 3,
                        PRIMARY_CONTROL,
                        PRIMARY_CONTROL);
        String leftLabel = ChatUpgradeFormatters.formatMs(Math.max(0L, positionMs));
        String rightLabel = ChatUpgradeFormatters.formatMs(Math.max(0L, durationMs));
        int leftWidth = font == null ? 24 : font.width(leftLabel);
        int rightWidth = font == null ? 24 : font.width(rightLabel);
        RichChatBounds leftTime = compact
                ? RichChatBounds.ofSize(
                        bounds.left() + PAD,
                        bounds.bottom() - 11,
                        leftWidth,
                        10)
                : RichChatBounds.ofSize(
                        play.right() + 4,
                        footerTop + 8,
                        leftWidth,
                        10);
        RichChatBounds rightTime = compact
                ? RichChatBounds.ofSize(
                        bounds.right() - PAD - rightWidth,
                        bounds.bottom() - 11,
                        rightWidth,
                        10)
                : RichChatBounds.ofSize(
                        bounds.right() - PAD - rightWidth,
                        footerTop + 8,
                        rightWidth,
                        10);
        RichChatBounds progress = compact
                ? new RichChatBounds(
                        leftTime.right() + 4,
                        bounds.bottom() - 9,
                        Math.max(leftTime.right() + 5, rightTime.left() - 4),
                        bounds.bottom() - 5)
                : new RichChatBounds(
                        leftTime.right() + 4,
                        footerTop + 10,
                        Math.max(leftTime.right() + 5, rightTime.left() - 4),
                        footerTop + 14);
        RichChatBounds title = new RichChatBounds(
                bounds.left() + PAD,
                bounds.top() + 5,
                Math.max(bounds.left() + PAD, bounds.right() - 25),
                bounds.top() + (compact ? 14 : 15));
        RichChatBounds open = RichChatBounds.ofSize(
                bounds.right() - PAD - SMALL_CONTROL,
                bounds.top() + 5,
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
            RichChatBounds currentTime,
            RichChatBounds durationTime) {
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