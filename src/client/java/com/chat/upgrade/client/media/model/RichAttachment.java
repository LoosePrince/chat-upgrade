package com.chat.upgrade.client.media.model;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public final class RichAttachment {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public enum Source {
        LEGACY_BRACKET,
        STRUCTURED_PACKET,
        LOCAL_DRAFT
    }

    private final int schemaVersion;
    private final InlineResourceType type;
    private final String displayName;
    private final Source source;
    private final @Nullable String url;
    private final @Nullable String mediaId;
    private final @Nullable String attachmentId;

    private RichAttachment(
            int schemaVersion,
            InlineResourceType type,
            String displayName,
            Source source,
            @Nullable String url,
            @Nullable String mediaId,
            @Nullable String attachmentId) {
        this.schemaVersion = schemaVersion;
        this.type = Objects.requireNonNull(type, "type");
        this.displayName = normalizeDisplayName(displayName, this.type);
        this.source = Objects.requireNonNull(source, "source");
        this.url = normalizeOptional(url);
        this.mediaId = normalizeOptional(mediaId);
        this.attachmentId = normalizeOptional(attachmentId);
    }

    public static RichAttachment legacyBracket(String url, String displayName, InlineResourceType type) {
        return new RichAttachment(
                CURRENT_SCHEMA_VERSION,
                type,
                displayName,
                Source.LEGACY_BRACKET,
                requireText(url, "url"),
                null,
                null);
    }

    public static RichAttachment localDraft(String url, String displayName, InlineResourceType type) {
        return new RichAttachment(
                CURRENT_SCHEMA_VERSION,
                type,
                displayName,
                Source.LOCAL_DRAFT,
                requireText(url, "url"),
                null,
                null);
    }

    public static RichAttachment structured(
            InlineResourceType type,
            String displayName,
            @Nullable String url,
            @Nullable String mediaId,
            @Nullable String attachmentId) {
        return new RichAttachment(
                CURRENT_SCHEMA_VERSION,
                type,
                displayName,
                Source.STRUCTURED_PACKET,
                url,
                mediaId,
                attachmentId);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public InlineResourceType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public Source source() {
        return source;
    }

    public Optional<String> url() {
        return Optional.ofNullable(url);
    }

    public @Nullable String urlOrNull() {
        return url;
    }

    public Optional<String> mediaId() {
        return Optional.ofNullable(mediaId);
    }

    public Optional<String> attachmentId() {
        return Optional.ofNullable(attachmentId);
    }

    public boolean hasRenderableUrl() {
        return url != null;
    }

    public String requireRenderableUrl() {
        if (url == null) {
            throw new IllegalStateException("attachment has no renderable url");
        }
        return url;
    }

    private static String normalizeDisplayName(String value, InlineResourceType type) {
        String normalized = normalizeOptional(value);
        return normalized == null ? type.toWire() : normalized;
    }

    private static String requireText(String value, String label) {
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