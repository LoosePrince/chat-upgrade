package com.chat.upgrade.client.ui.chat.viewport;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.interaction.ChatHitTarget;

import net.minecraft.network.chat.Style;

public record RichChatHitBox(
        RichChatHitBoxKind kind,
        String messageId,
        RichChatBounds bounds,
        ChatHitTarget target) {
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
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
    }

    public @Nullable RichAttachment attachment() {
        if (target instanceof ChatHitTarget.Attachment attachmentTarget) {
            return attachmentTarget.attachment();
        }
        if (target instanceof ChatHitTarget.Emoji emojiTarget) {
            return emojiTarget.attachment();
        }
        return null;
    }

    public @Nullable Style style() {
        return target instanceof ChatHitTarget.StyledText styledText ? styledText.style() : null;
    }

    public boolean contains(int x, int y) {
        return bounds.contains(x, y);
    }
}