package com.chat.upgrade.client.ui.chat.state;

import org.jetbrains.annotations.Nullable;

public record ChatReplySummary(
        String messageId,
        ChatAuthor author,
        String excerpt) {
    public ChatReplySummary {
        messageId = safe(messageId);
        author = author == null ? ChatAuthor.system() : author;
        excerpt = safe(excerpt);
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}