package com.chat.upgrade.net;

import org.jetbrains.annotations.Nullable;

public record StructuredChatMutation(
        int schemaVersion,
        String messageId,
        String mutation,
        long serverTimestampMs) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String RETRACTED = "retracted";

    public StructuredChatMutation {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        messageId = safe(messageId);
        mutation = normalizeMutation(mutation);
        serverTimestampMs = Math.max(0L, serverTimestampMs);
    }

    public static StructuredChatMutation retracted(String messageId, long timestampMs) {
        return new StructuredChatMutation(CURRENT_SCHEMA_VERSION, messageId, RETRACTED, timestampMs);
    }

    private static String normalizeMutation(@Nullable String value) {
        return RETRACTED.equalsIgnoreCase(safe(value)) ? RETRACTED : "unknown";
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}