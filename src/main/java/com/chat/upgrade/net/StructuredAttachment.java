package com.chat.upgrade.net;

import org.jetbrains.annotations.Nullable;

public record StructuredAttachment(
        int schemaVersion,
        @Nullable String attachmentId,
        @Nullable String mediaId,
        String typeWire,
        String displayName,
        @Nullable String fallbackUrl) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public StructuredAttachment {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        attachmentId = normalizeOptional(attachmentId);
        mediaId = normalizeOptional(mediaId);
        typeWire = normalizeType(typeWire);
        displayName = normalizeDisplayName(displayName, typeWire);
        fallbackUrl = normalizeOptional(fallbackUrl);
        if (fallbackUrl == null && mediaId != null) {
            fallbackUrl = ServerMediaUrl.format(mediaId, typeWire);
        }
        if (mediaId == null && fallbackUrl == null) {
            throw new IllegalArgumentException("structured attachment requires mediaId or fallbackUrl");
        }
    }

    public static StructuredAttachment serverMedia(
            @Nullable String attachmentId,
            String mediaId,
            String typeWire,
            @Nullable String displayName) {
        return new StructuredAttachment(
                CURRENT_SCHEMA_VERSION,
                attachmentId,
                requireText(mediaId, "mediaId"),
                typeWire,
                displayName,
                null);
    }

    public static StructuredAttachment externalUrl(
            @Nullable String attachmentId,
            String typeWire,
            @Nullable String displayName,
            String fallbackUrl) {
        return new StructuredAttachment(
                CURRENT_SCHEMA_VERSION,
                attachmentId,
                null,
                typeWire,
                displayName,
                requireText(fallbackUrl, "fallbackUrl"));
    }

    public StructuredAttachment withAttachmentId(String value) {
        return new StructuredAttachment(
                schemaVersion,
                requireText(value, "attachmentId"),
                mediaId,
                typeWire,
                displayName,
                fallbackUrl);
    }

    public boolean hasAttachmentId() {
        return attachmentId != null;
    }

    public String requireAttachmentId() {
        if (attachmentId == null) {
            throw new IllegalStateException("structured attachment has no attachmentId");
        }
        return attachmentId;
    }

    public boolean hasMedia() {
        return mediaId != null;
    }

    public boolean hasRenderableFallback() {
        return fallbackUrl != null;
    }

    public String requireRenderableFallback() {
        if (fallbackUrl == null) {
            throw new IllegalStateException("structured attachment has no fallbackUrl");
        }
        return fallbackUrl;
    }

    private static String normalizeType(@Nullable String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return "image";
        }
        if ("audio".equalsIgnoreCase(normalized)) {
            return "audio";
        }
        if ("video".equalsIgnoreCase(normalized)) {
            return "video";
        }
        return "image";
    }

    private static String normalizeDisplayName(@Nullable String value, String typeWire) {
        String normalized = normalizeOptional(value);
        return normalized == null ? typeWire : normalized;
    }

    private static String requireText(@Nullable String value, String label) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}