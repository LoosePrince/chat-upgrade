package com.chat.upgrade.client.ui.chat.viewport;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Style;

public record RichChatHitBox(
        RichChatHitBoxKind kind,
        String messageId,
        RichChatBounds bounds,
        @Nullable RichAttachment attachment,
        @Nullable Style style,
        String actionKey) {
    public RichChatHitBox {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (messageId == null) {
            throw new IllegalArgumentException("messageId must not be null");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        actionKey = actionKey == null ? "" : actionKey;
    }

    public boolean contains(int x, int y) {
        return bounds.contains(x, y);
    }
}