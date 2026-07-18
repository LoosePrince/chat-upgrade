package com.chat.upgrade.client.ui.chat.scene;

import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceFrame;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportMetrics;

public record ChatScene(
        ChatSurfaceFrame surface,
        RichChatViewportMetrics viewport,
        RichChatLayout timeline) {
    public ChatScene {
        if (surface == null || viewport == null || timeline == null) {
            throw new IllegalArgumentException("scene branches must not be null");
        }
    }
}