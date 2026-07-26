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
    public static final int MERGED_BOTTOM_OFFSET = SCREEN_MARGIN;
    public static final int HEADER_HEIGHT = 18;
    public static final int COMPOSER_HEIGHT = 62;
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
            int height,
            boolean automaticHeight,
            int bottomInset,
            boolean screenMarginsEnabled) {
        int screenMargin = screenMarginsEnabled ? SCREEN_MARGIN : 0;
        int minimumBottomInset = screenMarginsEnabled ? MERGED_BOTTOM_OFFSET : 0;
        int reservedBottom = Math.max(minimumBottomInset, bottomInset);
        if (automaticHeight || !screenMarginsEnabled) {
            int safeWidth = width <= 0 ? DEFAULT_WIDTH : width;
            int top = Math.min(screenMargin, Math.max(0, screenHeight - 1));
            int bottom = Math.max(top + 1, screenHeight - reservedBottom);
            return new ChatPanelGeometry(screenMarginsEnabled ? Math.max(0, left) : 0, top, safeWidth, bottom - top)
                    .normalized(screenWidth, screenHeight, reservedBottom, screenMarginsEnabled);
        }
        int safeWidth = width <= 0 ? DEFAULT_WIDTH : width;
        int safeHeight = height <= 0 ? DEFAULT_HEIGHT : height;
        int safeLeft = Math.max(0, left);
        int safeBottomOffset = Math.max(reservedBottom, bottomOffset);
        int top = Math.max(0, screenHeight - safeBottomOffset - safeHeight);
        return new ChatPanelGeometry(safeLeft, top, safeWidth, safeHeight)
                .normalized(screenWidth, screenHeight, reservedBottom, true);
    }

    public static ChatPanelGeometry restore(
            int screenWidth,
            int screenHeight,
            int left,
            int bottomOffset,
            int width,
            int height,
            boolean automaticHeight,
            int bottomInset) {
        return restore(
                screenWidth,
                screenHeight,
                left,
                bottomOffset,
                width,
                height,
                automaticHeight,
                bottomInset,
                true);
    }

    public static ChatPanelGeometry restore(
            int screenWidth,
            int screenHeight,
            int left,
            int bottomOffset,
            int width,
            int height) {
        return restore(screenWidth, screenHeight, left, bottomOffset, width, height, false, MERGED_BOTTOM_OFFSET);
    }

    public ChatPanelGeometry normalized(int screenWidth, int screenHeight) {
        return normalized(screenWidth, screenHeight, SCREEN_MARGIN);
    }

    public ChatPanelGeometry normalized(int screenWidth, int screenHeight, int bottomInset) {
        return normalized(screenWidth, screenHeight, bottomInset, true);
    }

    public ChatPanelGeometry normalized(
            int screenWidth,
            int screenHeight,
            int bottomInset,
            boolean screenMarginsEnabled) {
        int screenMargin = screenMarginsEnabled ? SCREEN_MARGIN : 0;
        int minimumBottomInset = screenMarginsEnabled ? SCREEN_MARGIN : 0;
        int safeBottomInset = Math.clamp(bottomInset, minimumBottomInset, Math.max(minimumBottomInset, screenHeight - 1));
        int availableWidth = Math.max(1, screenWidth - screenMargin * 2);
        int availableHeight = Math.max(1, screenHeight - screenMargin - safeBottomInset);
        int minWidth = Math.min(MIN_WIDTH, availableWidth);
        int minHeight = Math.min(MIN_HEIGHT, availableHeight);
        int nextWidth = Math.clamp(width, minWidth, availableWidth);
        int nextHeight = Math.clamp(height, minHeight, availableHeight);
        int maxX = Math.max(screenMargin, screenWidth - screenMargin - nextWidth);
        int maxY = Math.max(screenMargin, screenHeight - safeBottomInset - nextHeight);
        int nextX = screenMarginsEnabled ? Math.clamp(x, screenMargin, maxX) : 0;
        int nextY = screenMarginsEnabled ? Math.clamp(y, screenMargin, maxY) : 0;
        return new ChatPanelGeometry(nextX, nextY, nextWidth, nextHeight);
    }

    public ChatPanelGeometry moveBy(
            int deltaX,
            int deltaY,
            int screenWidth,
            int screenHeight,
            int bottomInset) {
        return new ChatPanelGeometry(x + deltaX, y + deltaY, width, height)
                .normalized(screenWidth, screenHeight, bottomInset);
    }

    public ChatPanelGeometry resizeFrom(
            int edges,
            int deltaX,
            int deltaY,
            int screenWidth,
            int screenHeight,
            int bottomInset) {
        return resizeFrom(edges, deltaX, deltaY, screenWidth, screenHeight, bottomInset, true);
    }

    public ChatPanelGeometry resizeFrom(
            int edges,
            int deltaX,
            int deltaY,
            int screenWidth,
            int screenHeight,
            int bottomInset,
            boolean screenMarginsEnabled) {
        int screenMargin = screenMarginsEnabled ? SCREEN_MARGIN : 0;
        int minimumBottomInset = screenMarginsEnabled ? SCREEN_MARGIN : 0;
        int safeBottomInset = Math.clamp(
                bottomInset,
                minimumBottomInset,
                Math.max(minimumBottomInset, screenHeight - 1));
        int left = x;
        int top = y;
        int right = right();
        int bottom = bottom();
        int availableWidth = Math.max(1, screenWidth - screenMargin * 2);
        int availableHeight = Math.max(1, screenHeight - screenMargin - safeBottomInset);
        int minWidth = Math.min(MIN_WIDTH, availableWidth);
        int minHeight = Math.min(MIN_HEIGHT, availableHeight);

        if ((edges & EDGE_LEFT) != 0) {
            left = Math.clamp(x + deltaX, screenMargin, right - minWidth);
        }
        if ((edges & EDGE_RIGHT) != 0) {
            right = Math.clamp(right + deltaX, left + minWidth, screenWidth - screenMargin);
        }
        if ((edges & EDGE_TOP) != 0) {
            top = Math.clamp(y + deltaY, screenMargin, bottom - minHeight);
        }
        if ((edges & EDGE_BOTTOM) != 0) {
            bottom = Math.clamp(bottom + deltaY, top + minHeight, screenHeight - safeBottomInset);
        }
        return new ChatPanelGeometry(left, top, right - left, bottom - top)
                .normalized(screenWidth, screenHeight, safeBottomInset, screenMarginsEnabled);
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