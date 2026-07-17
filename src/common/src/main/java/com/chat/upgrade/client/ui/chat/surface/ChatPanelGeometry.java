package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

public record ChatPanelGeometry(int x, int y, int width, int height) {
    public static final int SCREEN_MARGIN = 4;
    public static final int MIN_WIDTH = 220;
    public static final int MIN_HEIGHT = 140;
    public static final int DEFAULT_WIDTH = 360;
    public static final int DEFAULT_HEIGHT = 220;
    public static final int DEFAULT_LEFT = 4;
    public static final int DEFAULT_BOTTOM_OFFSET = 40;
    public static final int HEADER_HEIGHT = 18;
    public static final int COMPOSER_HEIGHT = 44;
    public static final int RESIZE_HANDLE_SIZE = 5;

    public static final int EDGE_NONE = 0;
    public static final int EDGE_LEFT = 1;
    public static final int EDGE_TOP = 1 << 1;
    public static final int EDGE_RIGHT = 1 << 2;
    public static final int EDGE_BOTTOM = 1 << 3;

    public ChatPanelGeometry {
        width = Math.max(1, width);
        height = Math.max(1, height);
    }

    public static ChatPanelGeometry restore(
            int screenWidth,
            int screenHeight,
            int left,
            int bottomOffset,
            int width,
            int height) {
        int safeWidth = width <= 0 ? DEFAULT_WIDTH : width;
        int safeHeight = height <= 0 ? DEFAULT_HEIGHT : height;
        int safeLeft = Math.max(0, left);
        int safeBottomOffset = Math.max(0, bottomOffset);
        int top = Math.max(0, screenHeight - safeBottomOffset - safeHeight);
        return new ChatPanelGeometry(safeLeft, top, safeWidth, safeHeight)
                .normalized(screenWidth, screenHeight);
    }

    public ChatPanelGeometry normalized(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - SCREEN_MARGIN * 2);
        int minWidth = Math.min(MIN_WIDTH, availableWidth);
        int minHeight = Math.min(MIN_HEIGHT, availableHeight);
        int nextWidth = Math.clamp(width, minWidth, availableWidth);
        int nextHeight = Math.clamp(height, minHeight, availableHeight);
        int maxX = Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - nextWidth);
        int maxY = Math.max(SCREEN_MARGIN, screenHeight - SCREEN_MARGIN - nextHeight);
        int nextX = Math.clamp(x, SCREEN_MARGIN, maxX);
        int nextY = Math.clamp(y, SCREEN_MARGIN, maxY);
        return new ChatPanelGeometry(nextX, nextY, nextWidth, nextHeight);
    }

    public ChatPanelGeometry moveBy(int deltaX, int deltaY, int screenWidth, int screenHeight) {
        return new ChatPanelGeometry(x + deltaX, y + deltaY, width, height)
                .normalized(screenWidth, screenHeight);
    }

    public ChatPanelGeometry resizeFrom(
            int edges,
            int deltaX,
            int deltaY,
            int screenWidth,
            int screenHeight) {
        int left = x;
        int top = y;
        int right = right();
        int bottom = bottom();
        int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - SCREEN_MARGIN * 2);
        int minWidth = Math.min(MIN_WIDTH, availableWidth);
        int minHeight = Math.min(MIN_HEIGHT, availableHeight);

        if ((edges & EDGE_LEFT) != 0) {
            left = Math.clamp(x + deltaX, SCREEN_MARGIN, right - minWidth);
        }
        if ((edges & EDGE_RIGHT) != 0) {
            right = Math.clamp(right + deltaX, left + minWidth, screenWidth - SCREEN_MARGIN);
        }
        if ((edges & EDGE_TOP) != 0) {
            top = Math.clamp(y + deltaY, SCREEN_MARGIN, bottom - minHeight);
        }
        if ((edges & EDGE_BOTTOM) != 0) {
            bottom = Math.clamp(bottom + deltaY, top + minHeight, screenHeight - SCREEN_MARGIN);
        }
        return new ChatPanelGeometry(left, top, right - left, bottom - top)
                .normalized(screenWidth, screenHeight);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int bottomOffset(int screenHeight) {
        return Math.max(0, screenHeight - bottom());
    }

    public RichChatBounds panelBounds() {
        return RichChatBounds.ofSize(x, y, width, height);
    }

    public RichChatBounds headerBounds() {
        return RichChatBounds.ofSize(x, y, width, Math.min(HEADER_HEIGHT, height));
    }

    public RichChatBounds messageViewportBounds() {
        int top = Math.min(bottom(), y + HEADER_HEIGHT);
        int viewportHeight = Math.max(0, bottom() - COMPOSER_HEIGHT - top);
        return RichChatBounds.ofSize(x, top, width, viewportHeight);
    }

    public RichChatBounds composerBounds() {
        int composerHeight = Math.min(COMPOSER_HEIGHT, height);
        return RichChatBounds.ofSize(x, bottom() - composerHeight, width, composerHeight);
    }

    public int resizeEdgesAt(double pointerX, double pointerY) {
        if (!panelBounds().contains((int) Math.round(pointerX), (int) Math.round(pointerY))) {
            return EDGE_NONE;
        }
        int edges = EDGE_NONE;
        if (pointerX < x + RESIZE_HANDLE_SIZE) {
            edges |= EDGE_LEFT;
        } else if (pointerX >= right() - RESIZE_HANDLE_SIZE) {
            edges |= EDGE_RIGHT;
        }
        if (pointerY < y + RESIZE_HANDLE_SIZE) {
            edges |= EDGE_TOP;
        } else if (pointerY >= bottom() - RESIZE_HANDLE_SIZE) {
            edges |= EDGE_BOTTOM;
        }
        return edges;
    }
}