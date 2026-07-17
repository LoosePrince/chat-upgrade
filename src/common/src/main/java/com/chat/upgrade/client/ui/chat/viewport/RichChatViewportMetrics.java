package com.chat.upgrade.client.ui.chat.viewport;

public record RichChatViewportMetrics(
        int screenHeight,
        double scale,
        int maxWidth,
        int visibleHeight,
        int chatBottom,
        int messageHeight,
        int entryHeight,
        int entryBottomToMessageY,
        float textOpacity,
        float backgroundOpacity,
        boolean focused) {
    public RichChatViewportMetrics {
        scale = scale <= 0.0 ? 1.0 : scale;
        maxWidth = Math.max(1, maxWidth);
        visibleHeight = Math.max(0, visibleHeight);
        messageHeight = Math.max(1, messageHeight);
        entryHeight = Math.max(1, entryHeight);
        entryBottomToMessageY = Math.max(1, entryBottomToMessageY);
        textOpacity = clamp01(textOpacity);
        backgroundOpacity = clamp01(backgroundOpacity);
    }

    public static RichChatViewportMetrics fromVanilla(
            int screenHeight,
            double scale,
            int chatWidth,
            int chatHeight,
            double chatLineSpacing,
            float textOpacity,
            float backgroundOpacity,
            boolean focused) {
        double safeScale = scale <= 0.0 ? 1.0 : scale;
        int maxWidth = (int) Math.ceil(chatWidth / safeScale);
        int visibleHeight = (int) Math.floor(chatHeight / safeScale);
        int chatBottom = (int) Math.floor((screenHeight - 40) / safeScale);
        int messageHeight = 9;
        int entryHeight = (int) (messageHeight * (chatLineSpacing + 1.0));
        int entryBottomToMessageY = (int) Math.round(8.0 * (chatLineSpacing + 1.0) - 4.0 * chatLineSpacing);
        return new RichChatViewportMetrics(
                screenHeight,
                safeScale,
                maxWidth,
                visibleHeight,
                chatBottom,
                messageHeight,
                entryHeight,
                entryBottomToMessageY,
                textOpacity,
                backgroundOpacity,
                focused);
    }

    public static RichChatViewportMetrics forSurface(
            int screenHeight,
            int maxWidth,
            int visibleHeight,
            int chatBottom,
            double chatLineSpacing,
            float textOpacity,
            float backgroundOpacity,
            boolean focused) {
        int messageHeight = 9;
        int entryHeight = (int) (messageHeight * (chatLineSpacing + 1.0));
        int entryBottomToMessageY = (int) Math.round(8.0 * (chatLineSpacing + 1.0) - 4.0 * chatLineSpacing);
        return new RichChatViewportMetrics(
                screenHeight,
                1.0D,
                maxWidth,
                visibleHeight,
                chatBottom,
                messageHeight,
                entryHeight,
                entryBottomToMessageY,
                textOpacity,
                backgroundOpacity,
                focused);
    }

    public int backgroundLeft() {
        return -4;
    }

    public int backgroundRight() {
        return maxWidth + 8;
    }

    public int scrollbarX() {
        return maxWidth + 4;
    }

    public int textLeft() {
        return 0;
    }

    public int textWidth() {
        return maxWidth;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}