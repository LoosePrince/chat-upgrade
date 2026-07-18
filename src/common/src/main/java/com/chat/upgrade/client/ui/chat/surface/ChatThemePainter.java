package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ChatThemePainter {
    private ChatThemePainter() {
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
        if (safeBorder > 0 && visible(borderColor)) {
            fillRounded(graphics, bounds, radius, borderColor);
            RichChatBounds inner = inset(bounds, safeBorder);
            if (visible(fillColor) && inner.width() > 0 && inner.height() > 0) {
                fillRounded(graphics, inner, Math.max(0, radius - safeBorder), fillColor);
            }
            return;
        }
        if (visible(fillColor)) {
            fillRounded(graphics, bounds, radius, fillColor);
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

    private static RichChatBounds inset(RichChatBounds bounds, int amount) {
        return new RichChatBounds(
                Math.min(bounds.right(), bounds.left() + amount),
                Math.min(bounds.bottom(), bounds.top() + amount),
                Math.max(bounds.left(), bounds.right() - amount),
                Math.max(bounds.top(), bounds.bottom() - amount));
    }

    private static int cornerInset(int radius, int row) {
        double centerDistance = radius - row - 0.5D;
        double horizontal = Math.sqrt(Math.max(0.0D, radius * radius - centerDistance * centerDistance));
        return Math.max(0, radius - (int) Math.floor(horizontal));
    }
}