package com.chat.upgrade.net;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public record StructuredChatEnvelope(
        int schemaVersion,
        String messageId,
        String clientNonce,
        long serverTimestampMs,
        StructuredChatAuthor author,
        String kind,
        String plainText,
        List<StructuredChatSegment> segments,
        List<StructuredAttachment> attachments,
        String fallbackText,
        int compatFlags,
        @Nullable StructuredReplySummary replyTo) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public StructuredChatEnvelope {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        messageId = safe(messageId);
        clientNonce = safe(clientNonce);
        serverTimestampMs = Math.max(0L, serverTimestampMs);
        author = author == null ? StructuredChatAuthor.legacy("") : author;
        kind = normalizeKind(kind);
        plainText = safeBody(plainText);
        segments = List.copyOf(Objects.requireNonNullElse(segments, List.of()));
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
        fallbackText = safeBody(fallbackText);
        compatFlags &= StructuredChatMessage.COMPAT_BRACKET_PROTOCOL
                | StructuredChatMessage.COMPAT_VANILLA_SAFE_TEXT;
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public StructuredChatMessage toLegacyMessage() {
        return new StructuredChatMessage(
                StructuredChatMessage.CURRENT_SCHEMA_VERSION,
                clientNonce,
                author.displayName(),
                plainText,
                segments,
                attachments,
                fallbackText,
                compatFlags);
    }

    private static String normalizeKind(@Nullable String value) {
        String normalized = safe(value).toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "system", "game", "announcement", "error" -> normalized;
            default -> "player";
        };
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeBody(@Nullable String value) {
        return value == null ? "" : value;
    }
}