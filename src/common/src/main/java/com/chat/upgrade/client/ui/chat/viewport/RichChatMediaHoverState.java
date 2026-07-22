package com.chat.upgrade.client.ui.chat.viewport;

/** Frame-local pointer state shared by media rendering and hit testing. */
public final class RichChatMediaHoverState {
    private static float localX = Float.NaN;
    private static float localY = Float.NaN;

    private RichChatMediaHoverState() {
    }

    public static void update(float x, float y) {
        localX = x;
        localY = y;
    }

    public static void clear() {
        localX = Float.NaN;
        localY = Float.NaN;
    }

    public static boolean contains(RichChatBounds bounds) {
        return bounds != null
                && Float.isFinite(localX)
                && Float.isFinite(localY)
                && localX >= bounds.left()
                && localX < bounds.right()
                && localY >= bounds.top()
                && localY < bounds.bottom();
    }
}