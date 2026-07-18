package com.chat.upgrade.client.ui.chat.interaction;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.viewport.RichChatHitBox;

public record ChatGestureTarget(
        ChatGesture gesture,
        RichChatMessage message,
        @Nullable RichChatHitBox hitBox,
        float localX,
        float localY) {
    public ChatGestureTarget {
        if (gesture == null || message == null) {
            throw new IllegalArgumentException("gesture and message must not be null");
        }
    }
}