package com.chat.upgrade.client.ui.screen;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.Font;

/** Shared preview-screen geometry; rendering and pointer routing consume the same bounds. */
final class MediaPreviewLayout {
    private static final int OUTER_MARGIN = 12;
    private static final int PANEL_PADDING = 8;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 40;
    private static final int CONTROL_HEIGHT = 24;
    private static final int CONTROL_GAP = 5;

    private MediaPreviewLayout() {
    }

    static Frame frame(int screenWidth, int screenHeight) {
        RichChatBounds panel = new RichChatBounds(
                OUTER_MARGIN,
                OUTER_MARGIN,
                Math.max(OUTER_MARGIN, screenWidth - OUTER_MARGIN),
                Math.max(OUTER_MARGIN, screenHeight - OUTER_MARGIN));
        RichChatBounds header = new RichChatBounds(
                panel.left(),
                panel.top(),
                panel.right(),
                Math.min(panel.bottom(), panel.top() + HEADER_HEIGHT));
        RichChatBounds footer = new RichChatBounds(
                panel.left(),
                Math.max(header.bottom(), panel.bottom() - FOOTER_HEIGHT),
                panel.right(),
                panel.bottom());
        RichChatBounds media = new RichChatBounds(
                panel.left() + PANEL_PADDING,
                header.bottom(),
                Math.max(panel.left() + PANEL_PADDING, panel.right() - PANEL_PADDING),
                Math.max(header.bottom(), footer.top()));
        RichChatBounds close = RichChatBounds.ofSize(
                Math.max(panel.left(), panel.right() - PANEL_PADDING - 22),
                panel.top() + PANEL_PADDING,
                22,
                22);
        RichChatBounds title = new RichChatBounds(
                panel.left() + PANEL_PADDING,
                panel.top() + 6,
                Math.max(panel.left() + PANEL_PADDING, close.left() - 8),
                panel.top() + 17);
        RichChatBounds metadata = new RichChatBounds(
                panel.left() + PANEL_PADDING,
                panel.top() + 21,
                Math.max(panel.left() + PANEL_PADDING, close.left() - 8),
                panel.top() + 33);
        return new Frame(panel, header, media, footer, title, metadata, close);
    }

    static ImageControls imageControls(Frame frame) {
        int top = frame.footer().top() + (frame.footer().height() - CONTROL_HEIGHT) / 2;
        int left = frame.footer().left() + PANEL_PADDING;
        RichChatBounds zoomIn = RichChatBounds.ofSize(left, top, 48, CONTROL_HEIGHT);
        RichChatBounds zoomOut = RichChatBounds.ofSize(zoomIn.right() + CONTROL_GAP, top, 48, CONTROL_HEIGHT);
        RichChatBounds rotate = RichChatBounds.ofSize(zoomOut.right() + CONTROL_GAP, top, 48, CONTROL_HEIGHT);
        RichChatBounds reset = RichChatBounds.ofSize(rotate.right() + CONTROL_GAP, top, 48, CONTROL_HEIGHT);
        RichChatBounds hint = new RichChatBounds(
                reset.right() + 8,
                top,
                Math.max(reset.right() + 8, frame.footer().right() - PANEL_PADDING),
                top + CONTROL_HEIGHT);
        return new ImageControls(zoomIn, zoomOut, rotate, reset, hint);
    }

    static VideoControls videoControls(Frame frame, Font font, long positionMs, long durationMs) {
        int top = frame.footer().top() + (frame.footer().height() - CONTROL_HEIGHT) / 2;
        RichChatBounds play = RichChatBounds.ofSize(frame.footer().left() + PANEL_PADDING, top, 28, CONTROL_HEIGHT);
        String leftLabel = ChatUpgradeFormatters.formatMs(Math.max(0L, positionMs));
        String rightLabel = ChatUpgradeFormatters.formatMs(Math.max(0L, durationMs));
        int leftWidth = font == null ? 28 : font.width(leftLabel);
        int rightWidth = font == null ? 28 : font.width(rightLabel);
        RichChatBounds leftTime = RichChatBounds.ofSize(play.right() + 8, top + 8, leftWidth, 10);
        RichChatBounds rightTime = RichChatBounds.ofSize(
                Math.max(leftTime.right(), frame.footer().right() - PANEL_PADDING - rightWidth),
                top + 8,
                rightWidth,
                10);
        RichChatBounds progress = new RichChatBounds(
                leftTime.right() + 7,
                top + 10,
                Math.max(leftTime.right() + 8, rightTime.left() - 7),
                top + 14);
        return new VideoControls(play, progress, leftTime, rightTime);
    }

    static RichChatBounds fitMedia(RichChatBounds box, int rawWidth, int rawHeight) {
        if (box == null || rawWidth <= 0 || rawHeight <= 0 || box.width() <= 0 || box.height() <= 0) {
            return box;
        }
        double scale = Math.min(box.width() / (double) rawWidth, box.height() / (double) rawHeight);
        int width = Math.max(1, (int) Math.round(rawWidth * scale));
        int height = Math.max(1, (int) Math.round(rawHeight * scale));
        return RichChatBounds.ofSize(
                box.left() + (box.width() - width) / 2,
                box.top() + (box.height() - height) / 2,
                width,
                height);
    }

    record Frame(
            RichChatBounds panel,
            RichChatBounds header,
            RichChatBounds media,
            RichChatBounds footer,
            RichChatBounds title,
            RichChatBounds metadata,
            RichChatBounds close) {
    }

    record ImageControls(
            RichChatBounds zoomIn,
            RichChatBounds zoomOut,
            RichChatBounds rotate,
            RichChatBounds reset,
            RichChatBounds hint) {
    }

    record VideoControls(
            RichChatBounds play,
            RichChatBounds progress,
            RichChatBounds leftTime,
            RichChatBounds rightTime) {
    }
}