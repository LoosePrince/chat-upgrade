package com.chat.upgrade.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;

/**
 * Runtime render state for chat smooth scrolling and strict clip bounds.
 */
public final class ChatUpgradeChatRenderState {
    private static final double WHEEL_LINES_FACTOR = 0.2D;
    private static float smoothOffsetPx = 0.0F;
    private static float wheelResidualOffsetPx = 0.0F;
    private static double wheelResidualLines = 0.0D;

    private static boolean clipActive;
    private static int clipLeft;
    private static int clipTop;
    private static int clipRight;
    private static int clipBottom;

    private ChatUpgradeChatRenderState() {
    }

    public static void onScrollDelta(int lineDelta, int lineHeight) {
        if (lineDelta == 0 || lineHeight <= 0) {
            return;
        }
        smoothOffsetPx += (float) (-lineDelta * lineHeight);
        smoothOffsetPx = Mth.clamp(smoothOffsetPx, -96.0F, 96.0F);
    }

    public static void resetScrollAnimation() {
        smoothOffsetPx = 0.0F;
        wheelResidualOffsetPx = 0.0F;
        wheelResidualLines = 0.0D;
    }

    public static void cancelWheelOverscroll() {
        // Hard-stop at boundaries: no visual overshoot and no rebound.
        smoothOffsetPx = 0.0F;
        wheelResidualOffsetPx = 0.0F;
        wheelResidualLines = 0.0D;
    }

    public static int consumeWheelScrollLines(double scaledScrollY, int lineHeight) {
        if (lineHeight <= 0) {
            return 0;
        }
        // Keep fractional wheel delta so scroll doesn't hard-snap to line boundaries.
        wheelResidualLines += scaledScrollY * WHEEL_LINES_FACTOR;
        int whole = (int) wheelResidualLines;
        wheelResidualLines -= whole;
        wheelResidualOffsetPx = (float) (wheelResidualLines * lineHeight);
        return whole;
    }

    public static void setScrollResidualLines(double residualLines, int lineHeight) {
        if (lineHeight <= 0) {
            return;
        }
        wheelResidualLines = Mth.clamp(residualLines, -0.999D, 0.999D);
        wheelResidualOffsetPx = (float) (wheelResidualLines * lineHeight);
        // When explicitly setting residual (e.g. drag), don't also animate toward a line boundary.
        smoothOffsetPx = 0.0F;
    }

    public static void beginRenderPass(
            GuiGraphicsExtractor graphics,
            int screenHeight,
            double scale,
            int chatHeight,
            int maxWidth) {
        smoothOffsetPx *= 0.72F;
        if (Math.abs(smoothOffsetPx) < 0.08F) {
            smoothOffsetPx = 0.0F;
        }

        int chatBottom = Mth.floor((screenHeight - 40.0D) / scale);
        int localTop = chatBottom - chatHeight;
        int localBottom = chatBottom;
        int localLeft = -4;
        int localRight = maxWidth + 8;
        // Strict clip: inward rounding avoids 1px leakage at boundaries.
        int screenLeft = Mth.ceil((localLeft + 4.0D) * scale);
        int screenRight = Mth.floor((localRight + 4.0D) * scale);
        int screenTop = Mth.ceil(localTop * scale);
        int screenBottom = Mth.floor(localBottom * scale);
        if (screenRight <= screenLeft || screenBottom <= screenTop) {
            clipActive = false;
            return;
        }

        graphics.enableScissor(screenLeft, screenTop, screenRight, screenBottom);
        clipActive = true;
        clipLeft = screenLeft;
        clipTop = screenTop;
        clipRight = screenRight;
        clipBottom = screenBottom;
    }

    public static void endRenderPass(GuiGraphicsExtractor graphics) {
        if (clipActive) {
            graphics.disableScissor();
        }
        clipActive = false;
    }

    public static boolean isInClipBounds(int screenX, int screenY) {
        if (!clipActive) {
            return true;
        }
        return screenX >= clipLeft && screenX < clipRight && screenY >= clipTop && screenY < clipBottom;
    }

    public static float smoothOffsetPx() {
        return smoothOffsetPx + wheelResidualOffsetPx;
    }
}
