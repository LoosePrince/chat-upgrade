package com.chat.upgrade.client.ui.chat.state;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Component;

public record RichChatMessage(
        String messageId,
        String senderName,
        Component component,
        String fallbackText,
        List<RichAttachment> attachments,
        RichChatMessageSource source) {
    public RichChatMessage {
        messageId = normalizeId(messageId);
        senderName = safeText(senderName);
        component = component == null ? Component.empty() : component;
        fallbackText = safeText(fallbackText);
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
        source = source == null ? RichChatMessageSource.VANILLA_TEXT : source;
    }

    public boolean hasRenderableAttachment() {
        return attachments.stream().anyMatch(RichAttachment::hasRenderableUrl);
    }

    public @Nullable RichAttachment firstRenderableAttachment() {
        return attachments.stream()
                .filter(RichAttachment::hasRenderableUrl)
                .findFirst()
                .orElse(null);
    }

    private static String normalizeId(@Nullable String value) {
        String normalized = safeText(value);
        return normalized.isBlank()
                ? "local-" + Long.toUnsignedString(System.nanoTime(), 36)
                : normalized;
    }

    private static String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}