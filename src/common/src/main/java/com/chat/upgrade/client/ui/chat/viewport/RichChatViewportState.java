package com.chat.upgrade.client.ui.chat.viewport;

public final class RichChatViewportState {
    private static final double SMOOTH_DECAY = 0.72D;
    private static final double SMOOTH_EPSILON = 0.08D;
    private static final double MAX_SMOOTH_OFFSET = 160.0D;

    private int scrollPx;
    private double smoothOffsetPx;
    private int totalHeight;
    private int visibleHeight;
    private boolean bottomPinned = true;

    public int scrollPx() {
        return scrollPx;
    }

    public int visualScrollPx() {
        return clampScroll((int) Math.round(scrollPx + smoothOffsetPx));
    }

    public double smoothOffsetPx() {
        return smoothOffsetPx;
    }

    public int totalHeight() {
        return totalHeight;
    }

    public int visibleHeight() {
        return visibleHeight;
    }

    public boolean bottomPinned() {
        return bottomPinned;
    }

    public int maxScrollPx() {
        return Math.max(0, totalHeight - visibleHeight);
    }

    public int visibleTop() {
        return Math.max(0, totalHeight - visibleHeight - visualScrollPx());
    }

    public int visibleBottom() {
        return Math.min(totalHeight, visibleTop() + visibleHeight);
    }

    public void updateContentBounds(int nextTotalHeight, int nextVisibleHeight) {
        boolean shouldStayPinned = bottomPinned || scrollPx <= 0;
        int before = scrollPx;
        totalHeight = Math.max(0, nextTotalHeight);
        visibleHeight = Math.max(0, nextVisibleHeight);
        if (shouldStayPinned) {
            scrollPx = 0;
            smoothOffsetPx = 0.0D;
        } else {
            scrollPx = clampScroll(scrollPx);
            if (scrollPx != before) {
                smoothOffsetPx = 0.0D;
            }
        }
        bottomPinned = scrollPx == 0;
    }

    public void tickSmoothOffset() {
        smoothOffsetPx *= SMOOTH_DECAY;
        if (Math.abs(smoothOffsetPx) < SMOOTH_EPSILON) {
            smoothOffsetPx = 0.0D;
        }
    }

    public boolean canScroll() {
        return totalHeight > visibleHeight && visibleHeight > 0;
    }

    public boolean scrollByPixels(int deltaPx) {
        if (deltaPx == 0) {
            return false;
        }
        int before = scrollPx;
        scrollPx = clampScroll(scrollPx + deltaPx);
        int actualDelta = scrollPx - before;
        if (actualDelta != 0) {
            smoothOffsetPx = Math.clamp(smoothOffsetPx - actualDelta, -MAX_SMOOTH_OFFSET, MAX_SMOOTH_OFFSET);
        }
        bottomPinned = scrollPx == 0;
        return actualDelta != 0;
    }

    public boolean setScrollPx(int nextScrollPx) {
        int before = scrollPx;
        scrollPx = clampScroll(nextScrollPx);
        smoothOffsetPx = 0.0D;
        bottomPinned = scrollPx == 0;
        return scrollPx != before;
    }

    public void scrollToBottom() {
        scrollPx = 0;
        smoothOffsetPx = 0.0;
        bottomPinned = true;
    }

    public void setSmoothOffsetPx(double nextSmoothOffsetPx) {
        smoothOffsetPx = Math.clamp(nextSmoothOffsetPx, -MAX_SMOOTH_OFFSET, MAX_SMOOTH_OFFSET);
    }

    public void clear() {
        scrollPx = 0;
        smoothOffsetPx = 0.0;
        totalHeight = 0;
        visibleHeight = 0;
        bottomPinned = true;
    }

    private int clampScroll(int value) {
        return Math.max(0, Math.min(maxScrollPx(), value));
    }
}