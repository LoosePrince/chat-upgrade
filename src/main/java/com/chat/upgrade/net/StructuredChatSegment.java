package com.chat.upgrade.net;

import org.jetbrains.annotations.Nullable;

public record StructuredChatSegment(
        String kind,
        String text,
        @Nullable String attachmentId) {
    public static final String KIND_TEXT = "TEXT";
    public static final String KIND_ATTACHMENT = "ATTACHMENT";

    public StructuredChatSegment {
        kind = normalizeKind(kind);
        text = text == null ? "" : text;
        attachmentId = normalizeOptional(attachmentId);
    }

    public static StructuredChatSegment text(String value) {
        return new StructuredChatSegment(KIND_TEXT, value, null);
    }

    public static StructuredChatSegment attachment(String attachmentId) {
        return new StructuredChatSegment(KIND_ATTACHMENT, "", attachmentId);
    }

    public boolean isText() {
        return KIND_TEXT.equals(kind);
    }

    public boolean isAttachment() {
        return KIND_ATTACHMENT.equals(kind);
    }

    private static String normalizeKind(@Nullable String value) {
        if (value == null) {
            return KIND_TEXT;
        }
        String normalized = value.trim().toUpperCase();
        if (KIND_ATTACHMENT.equals(normalized)) {
            return KIND_ATTACHMENT;
        }
        return KIND_TEXT;
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}