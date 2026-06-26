package com.chat.upgrade.client.ui.chat.state;

import org.jetbrains.annotations.Nullable;

public final class RichChatProjectionCoordinator {
    private static @Nullable RichChatProjection pendingProjection;

    private RichChatProjectionCoordinator() {
    }

    public static void prepareNext(RichChatProjection projection) {
        pendingProjection = projection;
    }

    public static @Nullable RichChatProjection consumeNext() {
        RichChatProjection projection = pendingProjection;
        pendingProjection = null;
        return projection;
    }

    public static boolean hasPending() {
        return pendingProjection != null;
    }

    public static void clear() {
        pendingProjection = null;
    }
}