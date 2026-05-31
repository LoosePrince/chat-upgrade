package com.chat.upgrade.net;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public record StructuredChatMessage(
        int schemaVersion,
        String clientNonce,
        String senderName,
        String plainText,
        List<StructuredChatSegment> segments,
        List<StructuredAttachment> attachments,
        String fallbackText,
        int compatFlags) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int COMPAT_LEGACY_BRACKET = 1;
    public static final int COMPAT_VANILLA_SAFE_TEXT = 1 << 1;

    public StructuredChatMessage {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        clientNonce = safeWire(clientNonce);
        senderName = safeWire(senderName);
        plainText = safeWire(plainText);
        segments = List.copyOf(Objects.requireNonNullElse(segments, List.of()));
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
        fallbackText = normalizeFallback(fallbackText, plainText);
    }

    public static StructuredChatMessage textOnly(@Nullable String clientNonce, @Nullable String plainText) {
        String text = safeWire(plainText);
        return new StructuredChatMessage(
                CURRENT_SCHEMA_VERSION,
                clientNonce,
                "",
                text,
                text.isBlank() ? List.of() : List.of(StructuredChatSegment.text(text)),
                List.of(),
                text,
                COMPAT_VANILLA_SAFE_TEXT);
    }

    public static StructuredChatMessage withSingleAttachment(
            @Nullable String clientNonce,
            @Nullable String plainText,
            StructuredAttachment attachment,
            @Nullable String fallbackText) {
        Objects.requireNonNull(attachment, "attachment");
        String text = safeWire(plainText);
        String attachmentId = attachment.attachmentId() == null ? "" : attachment.attachmentId();
        List<StructuredChatSegment> outSegments = text.isBlank()
                ? List.of(StructuredChatSegment.attachment(attachmentId))
                : List.of(StructuredChatSegment.text(text), StructuredChatSegment.attachment(attachmentId));
        return new StructuredChatMessage(
                CURRENT_SCHEMA_VERSION,
                clientNonce,
                "",
                text,
                outSegments,
                List.of(attachment),
                fallbackText,
                COMPAT_LEGACY_BRACKET | COMPAT_VANILLA_SAFE_TEXT);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public StructuredChatMessage withSenderName(String value) {
        return new StructuredChatMessage(
                schemaVersion,
                clientNonce,
                value,
                plainText,
                segments,
                attachments,
                fallbackText,
                compatFlags);
    }

    private static String normalizeFallback(@Nullable String fallbackText, String plainText) {
        String fallback = safeWire(fallbackText);
        return fallback.isBlank() ? plainText : fallback;
    }

    private static String safeWire(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}