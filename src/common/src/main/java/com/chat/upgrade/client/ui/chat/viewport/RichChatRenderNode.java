package com.chat.upgrade.client.ui.chat.viewport;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public record RichChatRenderNode(
        RichChatRenderNodeKind kind,
        String messageId,
        RichChatBounds bounds,
        int order,
        @Nullable FormattedCharSequence text,
        @Nullable Component component,
        @Nullable RichAttachment attachment,
        List<InlineEmojiSlot> inlineEmojiSlots) {
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
        inlineEmojiSlots = List.copyOf(inlineEmojiSlots == null ? List.of() : inlineEmojiSlots);
    }

    public static RichChatRenderNode deleted(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component) {
        return new RichChatRenderNode(
                RichChatRenderNodeKind.DELETED,
                messageId,
                bounds,
                order,
                text,
                component,
                null,
                List.of());
    }

    public static RichChatRenderNode reply(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component) {
        return new RichChatRenderNode(
                RichChatRenderNodeKind.REPLY,
                messageId,
                bounds,
                order,
                text,
                component,
                null,
                List.of());
    }

    public static RichChatRenderNode text(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component) {
        return text(messageId, bounds, order, text, component, List.of());
    }

    public static RichChatRenderNode text(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component,
            List<InlineEmojiSlot> inlineEmojiSlots) {
        return new RichChatRenderNode(
                RichChatRenderNodeKind.TEXT,
                messageId,
                bounds,
                order,
                text,
                component,
                null,
                inlineEmojiSlots);
    }

    public static RichChatRenderNode system(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component) {
        return system(messageId, bounds, order, text, component, List.of());
    }

    public static RichChatRenderNode system(
            String messageId,
            RichChatBounds bounds,
            int order,
            FormattedCharSequence text,
            Component component,
            List<InlineEmojiSlot> inlineEmojiSlots) {
        return new RichChatRenderNode(
                RichChatRenderNodeKind.SYSTEM,
                messageId,
                bounds,
                order,
                text,
                component,
                null,
                inlineEmojiSlots);
    }

    public static RichChatRenderNode attachment(
            RichChatRenderNodeKind kind,
            String messageId,
            RichChatBounds bounds,
            int order,
            RichAttachment attachment) {
        return new RichChatRenderNode(kind, messageId, bounds, order, null, null, attachment, List.of());
    }
}