package com.chat.upgrade.server.store;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.net.StructuredAttachment;

public record StoredAttachment(
        String attachmentId,
        @Nullable String mediaId,
        String typeWire,
        String displayName,
        @Nullable String fallbackUrl,
        int schemaVersion,
        long createdAtMs,
        long expiresAtMs) {
    public StoredAttachment {
        StructuredAttachment descriptor = new StructuredAttachment(
                schemaVersion,
                attachmentId,
                mediaId,
                typeWire,
                displayName,
                fallbackUrl);
        attachmentId = descriptor.requireAttachmentId();
        mediaId = descriptor.mediaId();
        typeWire = descriptor.typeWire();
        displayName = descriptor.displayName();
        fallbackUrl = descriptor.fallbackUrl();
        schemaVersion = descriptor.schemaVersion();
        createdAtMs = Math.max(0L, createdAtMs);
        expiresAtMs = Math.max(0L, expiresAtMs);
    }

    public StructuredAttachment descriptor() {
        return new StructuredAttachment(
                schemaVersion,
                attachmentId,
                mediaId,
                typeWire,
                displayName,
                fallbackUrl);
    }

    public boolean isExpired(long nowMs) {
        return expiresAtMs > 0L && nowMs >= expiresAtMs;
    }
}