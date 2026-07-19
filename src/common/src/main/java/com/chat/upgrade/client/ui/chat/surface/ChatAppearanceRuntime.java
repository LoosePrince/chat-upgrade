package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ChatUpgradeConfig;

/** Owns the committed frame snapshot and the optional settings preview snapshot. */
public final class ChatAppearanceRuntime {
    private static volatile ChatAppearanceSnapshot committed = ChatAppearanceSnapshot.from(ChatUpgradeConfig.get());
    private static volatile ChatAppearanceSnapshot preview;

    private ChatAppearanceRuntime() {
    }

    public static ChatAppearanceSnapshot current() {
        ChatAppearanceSnapshot activePreview = preview;
        return activePreview == null ? committed : activePreview;
    }

    public static void commit(ChatUpgradeConfig config) {
        committed = ChatAppearanceSnapshot.from(config);
        preview = null;
    }

    public static void preview(ChatUpgradeConfig config) {
        preview = ChatAppearanceSnapshot.from(config);
    }

    public static boolean isPreviewing() {
        return preview != null;
    }
}