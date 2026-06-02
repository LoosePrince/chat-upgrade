package com.chat.upgrade.client.ui.chat.viewport;

public record RichChatBounds(int left, int top, int right, int bottom) {
    public RichChatBounds {
        if (right < left) {
            right = left;
        }
        if (bottom < top) {
            bottom = top;
        }
    }

    public static RichChatBounds ofSize(int left, int top, int width, int height) {
        return new RichChatBounds(left, top, left + Math.max(0, width), top + Math.max(0, height));
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public boolean contains(int x, int y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public boolean intersectsVerticalRange(int topInclusive, int bottomExclusive) {
        return bottom > topInclusive && top < bottomExclusive;
    }

    public RichChatBounds translateY(int delta) {
        if (delta == 0) {
            return this;
        }
        return new RichChatBounds(left, top + delta, right, bottom + delta);
    }
}