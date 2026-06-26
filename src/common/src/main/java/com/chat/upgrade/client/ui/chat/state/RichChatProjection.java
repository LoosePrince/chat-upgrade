package com.chat.upgrade.client.ui.chat.state;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Component;

public record RichChatProjection(
        RichChatMessage message,
        ChatProjectionMode mode,
        Component textProjection,
        @Nullable RichAttachment mediaAttachment) {
    public RichChatProjection {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        mode = mode == null ? ChatProjectionMode.VANILLA_COMPAT : mode;
        textProjection = textProjection == null ? Component.empty() : textProjection;
    }

    public boolean hasMediaBlock() {
        return mediaAttachment != null && mediaAttachment.hasRenderableUrl();
    }
}