package com.chat.upgrade.net;

import org.jetbrains.annotations.Nullable;

public record StructuredReplySummary(
        String messageId,
        String authorDisplayName,
        String excerpt) {
    public StructuredReplySummary {
        messageId = safe(messageId);
        authorDisplayName = safe(authorDisplayName);
        excerpt = safe(excerpt);
    }

    public static StructuredReplySummary target(@Nullable String messageId) {
        return new StructuredReplySummary(messageId, "", "");
    }

    public boolean resolved() {
        return !messageId.isBlank() && !authorDisplayName.isBlank();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}