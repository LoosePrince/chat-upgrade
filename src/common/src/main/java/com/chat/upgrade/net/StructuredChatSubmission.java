package com.chat.upgrade.net;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public record StructuredChatSubmission(
        int schemaVersion,
        String clientNonce,
        String plainText,
        List<StructuredChatSegment> segments,
        List<StructuredAttachment> attachments,
        String replyToMessageId) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public StructuredChatSubmission {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        clientNonce = safe(clientNonce);
        plainText = safeBody(plainText);
        segments = List.copyOf(Objects.requireNonNullElse(segments, List.of()));
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
        replyToMessageId = safe(replyToMessageId);
    }

    public static StructuredChatSubmission fromLegacy(StructuredChatMessage message) {
        Objects.requireNonNull(message, "message");
        return new StructuredChatSubmission(
                CURRENT_SCHEMA_VERSION,
                message.clientNonce(),
                message.plainText(),
                message.segments(),
                message.attachments(),
                "");
    }

    public StructuredChatSubmission replyingTo(@Nullable String messageId) {
        return new StructuredChatSubmission(
                schemaVersion,
                clientNonce,
                plainText,
                segments,
                attachments,
                messageId);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeBody(@Nullable String value) {
        return value == null ? "" : value;
    }
}