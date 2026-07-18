package com.chat.upgrade.client.ui.chat.interaction;

import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportState;

/**
 * Single pointer-capture arbiter for chat gestures. A gesture owner must be
 * claimed before it mutates state; unrelated release/cancel paths cannot steal
 * or complete another owner's capture.
 */
public final class ChatGestureArena {
    public enum Owner {
        NONE,
        PANEL,
        SCROLLBAR,
        FLOATING_AUDIO,
        MEDIA,
        TEXT_SELECTION,
        TIMELINE_SCROLL,
        ATTACHMENT_TRAY
    }

    private static final Runnable NOOP_CANCELLATION = () -> {
    };

    private static Owner owner = Owner.NONE;
    private static Runnable cancellation = NOOP_CANCELLATION;
    private static boolean releasePending;
    private static int lastX;
    private static int lastY;

    private ChatGestureArena() {
    }

    public static boolean tryCapture(Owner nextOwner) {
        return tryCapture(nextOwner, NOOP_CANCELLATION);
    }

    public static boolean tryCapture(Owner nextOwner, Runnable onCancel) {
        if (nextOwner == null || nextOwner == Owner.NONE) {
            return false;
        }
        synchronized (ChatGestureArena.class) {
            if (owner != Owner.NONE) {
                return false;
            }
            owner = nextOwner;
            cancellation = onCancel == null ? NOOP_CANCELLATION : onCancel;
            releasePending = false;
            return true;
        }
    }

    public static synchronized boolean isCapturedBy(Owner expectedOwner) {
        return expectedOwner != null && owner == expectedOwner;
    }

    public static synchronized Owner owner() {
        return owner;
    }

    public static synchronized void release(Owner expectedOwner) {
        if (expectedOwner != null && owner == expectedOwner) {
            clearCapture();
        }
    }

    public static void cancel(Owner expectedOwner) {
        Runnable handler;
        synchronized (ChatGestureArena.class) {
            if (expectedOwner == null || owner != expectedOwner) {
                return;
            }
            handler = cancellation;
            clearCapture();
            releasePending = true;
        }
        handler.run();
    }

    public static void cancel() {
        Runnable handler;
        synchronized (ChatGestureArena.class) {
            if (owner == Owner.NONE) {
                return;
            }
            handler = cancellation;
            clearCapture();
            releasePending = true;
        }
        handler.run();
    }

    public static boolean cancelOnRelease(Owner expectedOwner) {
        Runnable handler;
        synchronized (ChatGestureArena.class) {
            if (expectedOwner == null || owner != expectedOwner) {
                return false;
            }
            handler = cancellation;
            clearCapture();
            releasePending = false;
        }
        handler.run();
        return true;
    }

    /** Clears capture and release arbitration at a hard pointer boundary. */
    public static void resetPointerState() {
        Runnable handler;
        synchronized (ChatGestureArena.class) {
            handler = owner == Owner.NONE ? NOOP_CANCELLATION : cancellation;
            clearCapture();
            releasePending = false;
        }
        handler.run();
    }

    public static synchronized boolean consumePendingRelease() {
        if (!releasePending) {
            return false;
        }
        releasePending = false;
        return true;
    }

    private static void clearCapture() {
        owner = Owner.NONE;
        cancellation = NOOP_CANCELLATION;
        lastX = 0;
        lastY = 0;
    }

    public static boolean beginBlankScroll(int x, int y) {
        if (!tryCapture(Owner.TIMELINE_SCROLL)) {
            return false;
        }
        lastX = x;
        lastY = y;
        return true;
    }

    public static boolean update(int x, int y) {
        if (!isCapturedBy(Owner.TIMELINE_SCROLL)) {
            return false;
        }
        int deltaY = y - lastY;
        lastX = x;
        lastY = y;
        if (deltaY != 0) {
            RichChatViewportState state = RichChatViewport.state();
            state.scrollByPixels(deltaY);
            state.setSmoothOffsetPx(0.0D);
            ChatUpgradeChatRenderState.cancelWheelOverscroll();
        }
        return true;
    }

    public static boolean finish() {
        if (!isCapturedBy(Owner.TIMELINE_SCROLL)) {
            return false;
        }
        release(Owner.TIMELINE_SCROLL);
        return true;
    }

    public static synchronized boolean hasCapture() {
        return owner != Owner.NONE;
    }
}