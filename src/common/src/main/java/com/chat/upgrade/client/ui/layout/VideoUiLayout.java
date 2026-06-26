package com.chat.upgrade.client.ui.layout;
public final class VideoUiLayout {
    public static final int HEIGHT = 63;
    public static final int WIDTH = 220;
    public static final int PAD_X = 6;
    // Single control row: button + times + progress share one row.
    public static final int CONTROL_H = 10;
    public static final int PROGRESS_H = 3;
    public static final int CONTROL_TOP = HEIGHT - CONTROL_H - 1;
    public static final int PROGRESS_TOP = CONTROL_TOP + 4;
    public static final int VIDEO_BOTTOM = CONTROL_TOP - 1;
    public static final int BTN_W = 16;
    public static final int BTN_H = 9;

    private VideoUiLayout() {}

    public static int clampWidth(int width) {
        return Math.max(48, Math.min(320, width));
    }

    public static Rect fitVideoRect(int x0, int y0, int drawW, int rawW, int rawH) {
        int boxW = Math.max(1, drawW);
        int boxH = Math.max(1, VIDEO_BOTTOM);
        if (rawW <= 0 || rawH <= 0) {
            return new Rect(x0, y0, x0 + boxW, y0 + boxH);
        }
        double sx = (double) boxW / rawW;
        double sy = (double) boxH / rawH;
        double scale = Math.min(sx, sy);
        int w = Math.max(1, (int) Math.round(rawW * scale));
        int h = Math.max(1, (int) Math.round(rawH * scale));
        int left = x0;
        int top = y0 + (boxH - h) / 2;
        return new Rect(left, top, left + w, top + h);
    }

    public record Rect(int left, int top, int right, int bottom) {}
}
