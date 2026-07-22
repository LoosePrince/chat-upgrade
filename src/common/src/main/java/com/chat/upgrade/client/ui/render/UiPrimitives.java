package com.chat.upgrade.client.ui.render;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared geometry primitives. Textures and icon rendering are layered above this class. */
public final class UiPrimitives {
    private UiPrimitives() {
    }

    public static int withOpacity(int color, float opacity) {
        int sourceAlpha = color >>> 24;
        int alpha = Math.clamp(Math.round(sourceAlpha * Math.clamp(opacity, 0.0F, 1.0F)), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static boolean visible(int color) {
        return (color >>> 24) != 0;
    }

    public static void paintBox(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int radius,
            int borderWidth,
            int fillColor,
            int borderColor) {
        if (graphics == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int safeBorder = Math.clamp(borderWidth, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (visible(fillColor)) {
            fillRounded(graphics, bounds, radius, fillColor);
        }
        if (safeBorder > 0 && visible(borderColor)) {
            strokeRounded(graphics, bounds, radius, safeBorder, borderColor);
        }
    }

    public static void fillRounded(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int radius,
            int color) {
        if (graphics == null || bounds == null || !visible(color) || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeRadius == 0) {
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), color);
            return;
        }
        if (UiTextureAtlas.paintRounded(graphics, bounds, safeRadius, color)) {
            return;
        }
        for (int row = 0; row < bounds.height(); row++) {
            int edgeRow = Math.min(row, bounds.height() - 1 - row);
            int inset = edgeRow >= safeRadius ? 0 : cornerInset(safeRadius, edgeRow);
            graphics.fill(
                    bounds.left() + inset,
                    bounds.top() + row,
                    bounds.right() - inset,
                    bounds.top() + row + 1,
                    color);
        }
    }

    public static void withRoundedClip(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int radius,
            Runnable draw) {
        if (graphics == null || bounds == null || draw == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeRadius == 0) {
            graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            try {
                draw.run();
            } finally {
                graphics.disableScissor();
            }
            return;
        }
        int middleTop = bounds.top() + safeRadius;
        int middleBottom = bounds.bottom() - safeRadius;
        if (middleBottom > middleTop) {
            drawClippedBand(graphics, bounds.left(), middleTop, bounds.right(), middleBottom, draw);
        }
        for (int row = 0; row < safeRadius; row++) {
            int inset = cornerInset(safeRadius, row);
            int left = bounds.left() + inset;
            int right = bounds.right() - inset;
            drawClippedBand(graphics, left, bounds.top() + row, right, bounds.top() + row + 1, draw);
            drawClippedBand(graphics, left, bounds.bottom() - row - 1, right, bounds.bottom() - row, draw);
        }
    }

    public static void withBottomRoundedClip(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int radius,
            Runnable draw) {
        if (graphics == null || bounds == null || draw == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeRadius == 0) {
            graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            try {
                draw.run();
            } finally {
                graphics.disableScissor();
            }
            return;
        }
        int squareBottom = bounds.bottom() - safeRadius;
        if (squareBottom > bounds.top()) {
            drawClippedBand(graphics, bounds.left(), bounds.top(), bounds.right(), squareBottom, draw);
        }
        for (int row = 0; row < safeRadius; row++) {
            int inset = cornerInset(safeRadius, row);
            drawClippedBand(
                    graphics,
                    bounds.left() + inset,
                    bounds.bottom() - row - 1,
                    bounds.right() - inset,
                    bounds.bottom() - row,
                    draw);
        }
    }

    public static boolean containsRounded(RichChatBounds bounds, int x, int y, int radius) {
        if (bounds == null || !bounds.contains(x, y)) {
            return false;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeRadius == 0) {
            return true;
        }
        int row = y - bounds.top();
        int edgeRow = Math.min(row, bounds.height() - 1 - row);
        int edgeInset = edgeRow >= safeRadius ? 0 : cornerInset(safeRadius, edgeRow);
        return x >= bounds.left() + edgeInset && x < bounds.right() - edgeInset;
    }

    public static boolean containsBottomRounded(RichChatBounds bounds, int x, int y, int radius) {
        if (bounds == null || !bounds.contains(x, y)) {
            return false;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        int edgeRow = bounds.bottom() - 1 - y;
        if (safeRadius == 0 || edgeRow >= safeRadius) {
            return true;
        }
        int edgeInset = cornerInset(safeRadius, edgeRow);
        return x >= bounds.left() + edgeInset && x < bounds.right() - edgeInset;
    }

    public static void strokeRounded(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int radius,
            int width,
            int color) {
        if (graphics == null || bounds == null || !visible(color) || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int safeWidth = Math.clamp(width, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeWidth == 0) {
            return;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeRadius > 0
                && safeWidth <= 4
                && UiTextureAtlas.paintRoundedBorder(graphics, bounds, safeRadius, safeWidth, color)) {
            return;
        }
        for (int row = 0; row < bounds.height(); row++) {
            int outerInset = roundedInset(bounds.height(), safeRadius, row);
            int outerLeft = bounds.left() + outerInset;
            int outerRight = bounds.right() - outerInset;
            int innerRow = row - safeWidth;
            int innerHeight = bounds.height() - safeWidth * 2;
            if (innerRow < 0 || innerRow >= innerHeight || bounds.width() <= safeWidth * 2) {
                graphics.fill(outerLeft, bounds.top() + row, outerRight, bounds.top() + row + 1, color);
                continue;
            }
            int innerRadius = Math.max(0, safeRadius - safeWidth);
            int innerInset = roundedInset(innerHeight, innerRadius, innerRow);
            int innerLeft = bounds.left() + safeWidth + innerInset;
            int innerRight = bounds.right() - safeWidth - innerInset;
            graphics.fill(outerLeft, bounds.top() + row, Math.min(outerRight, innerLeft), bounds.top() + row + 1, color);
            graphics.fill(Math.max(outerLeft, innerRight), bounds.top() + row, outerRight, bounds.top() + row + 1, color);
        }
    }

    private static void drawClippedBand(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int right,
            int bottom,
            Runnable draw) {
        if (right <= left || bottom <= top) {
            return;
        }
        graphics.enableScissor(left, top, right, bottom);
        try {
            draw.run();
        } finally {
            graphics.disableScissor();
        }
    }

    private static int roundedInset(int height, int radius, int row) {
        if (radius <= 0 || row >= radius && row < height - radius) {
            return 0;
        }
        int edgeRow = Math.min(row, height - 1 - row);
        return cornerInset(radius, edgeRow);
    }

    private static int cornerInset(int radius, int row) {
        double centerDistance = radius - row - 0.5D;
        double horizontal = Math.sqrt(Math.max(0.0D, radius * radius - centerDistance * centerDistance));
        return Math.max(0, radius - (int) Math.floor(horizontal));
    }
}