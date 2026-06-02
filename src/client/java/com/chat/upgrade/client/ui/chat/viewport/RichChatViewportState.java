package com.chat.upgrade.client.ui.chat.viewport;

public final class RichChatViewportState {
    private int scrollPx;
    private double smoothOffsetPx;
    private int totalHeight;
    private int visibleHeight;
    private boolean bottomPinned = true;

    public int scrollPx() {
        return scrollPx;
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
        return Math.max(0, totalHeight - visibleHeight - scrollPx);
    }

    public int visibleBottom() {
        return Math.min(totalHeight, visibleTop() + visibleHeight);
    }

    public void updateContentBounds(int nextTotalHeight, int nextVisibleHeight) {
        boolean shouldStayPinned = bottomPinned || scrollPx <= 0;
        totalHeight = Math.max(0, nextTotalHeight);
        visibleHeight = Math.max(0, nextVisibleHeight);
        if (shouldStayPinned) {
            scrollPx = 0;
        } else {
            scrollPx = clampScroll(scrollPx);
        }
        bottomPinned = scrollPx == 0;
    }

    public void scrollByPixels(int deltaPx) {
        if (deltaPx == 0) {
            return;
        }
        scrollPx = clampScroll(scrollPx + deltaPx);
        bottomPinned = scrollPx == 0;
    }

    public void setScrollPx(int nextScrollPx) {
        scrollPx = clampScroll(nextScrollPx);
        bottomPinned = scrollPx == 0;
    }

    public void scrollToBottom() {
        scrollPx = 0;
        smoothOffsetPx = 0.0;
        bottomPinned = true;
    }

    public void setSmoothOffsetPx(double nextSmoothOffsetPx) {
        smoothOffsetPx = nextSmoothOffsetPx;
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