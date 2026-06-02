package com.chat.upgrade.client.ui.chat.viewport;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public record RichChatRenderNode(
        RichChatRenderNodeKind kind,
        String messageId,
        RichChatBounds bounds,
        int order,
        @Nullable FormattedCharSequence text,
        @Nullable Component component,
        @Nullable RichAttachment attachment) {
    public RichChatRenderNode {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (messageId == null) {
            throw new IllegalArgumentException("messageId must not be null");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
    }

    public static RichChatRenderNode text(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component) {
        return new RichChatRenderNode(RichChatRenderNodeKind.TEXT, messageId, bounds, order, text, component, null);
    }

    public static RichChatRenderNode system(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component) {
        return new RichChatRenderNode(RichChatRenderNodeKind.SYSTEM, messageId, bounds, order, text, component, null);
    }

    public static RichChatRenderNode attachment(
            RichChatRenderNodeKind kind,
            String messageId,
            RichChatBounds bounds,
            int order,
            RichAttachment attachment) {
        return new RichChatRenderNode(kind, messageId, bounds, order, null, null, attachment);
    }
}