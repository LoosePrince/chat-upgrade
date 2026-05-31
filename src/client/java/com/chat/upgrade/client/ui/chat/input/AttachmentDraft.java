package com.chat.upgrade.client.ui.chat.input;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.InlineResourceType;

public final class AttachmentDraft {
    public enum Source {
        CLIPBOARD,
        FILE_PICKER,
        LOCAL_PATH
    }

    public enum Status {
        READY,
        UPLOADING,
        UPLOADED,
        FAILED
    }

    private final InlineResourceType type;
    private final String fileName;
    private final String displayName;
    private final Source source;
    private final byte[] data;
    private final @Nullable Path file;
    private final @Nullable String contentType;
    private final @Nullable String uploadedUrl;
    private final @Nullable String failureMessage;
    private final Status status;

    private AttachmentDraft(
            InlineResourceType type,
            String fileName,
            String displayName,
            Source source,
            byte[] data,
            @Nullable Path file,
            @Nullable String contentType,
            @Nullable String uploadedUrl,
            @Nullable String failureMessage,
            Status status) {
        this.type = Objects.requireNonNull(type, "type");
        this.fileName = requireText(fileName, "fileName");
        this.displayName = normalizeDisplayName(displayName, this.fileName);
        this.source = Objects.requireNonNull(source, "source");
        this.data = Objects.requireNonNull(data, "data").clone();
        this.file = file;
        this.contentType = normalizeOptionalText(contentType);
        this.uploadedUrl = normalizeOptionalText(uploadedUrl);
        this.failureMessage = normalizeOptionalText(failureMessage);
        this.status = Objects.requireNonNull(status, "status");
    }

    public static AttachmentDraft fromBytes(
            InlineResourceType type,
            byte[] data,
            String fileName,
            String displayName,
            Source source,
            @Nullable String contentType) {
        return new AttachmentDraft(
                type,
                fileName,
                displayName,
                source,
                data,
                null,
                contentType,
                null,
                null,
                Status.READY);
    }

    public static AttachmentDraft fromFile(
            InlineResourceType type,
            Path file,
            byte[] data,
            String displayName,
            Source source,
            @Nullable String contentType) {
        Path namePath = Objects.requireNonNull(file, "file").getFileName();
        String fileName = namePath == null ? "attachment" : namePath.toString();
        return new AttachmentDraft(
                type,
                data,
                fileName,
                displayName,
                source,
                file,
                contentType,
                null,
                null,
                Status.READY);
    }

    private AttachmentDraft(
            InlineResourceType type,
            byte[] data,
            String fileName,
            String displayName,
            Source source,
            @Nullable Path file,
            @Nullable String contentType,
            @Nullable String uploadedUrl,
            @Nullable String failureMessage,
            Status status) {
        this(
                type,
                fileName,
                displayName,
                source,
                data,
                file,
                contentType,
                uploadedUrl,
                failureMessage,
                status);
    }

    public InlineResourceType type() {
        return type;
    }

    public String fileName() {
        return fileName;
    }

    public String displayName() {
        return displayName;
    }

    public Source source() {
        return source;
    }

    public byte[] data() {
        return data.clone();
    }

    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    public Optional<String> contentType() {
        return Optional.ofNullable(contentType);
    }

    public Optional<String> uploadedUrl() {
        return Optional.ofNullable(uploadedUrl);
    }

    public Optional<String> failureMessage() {
        return Optional.ofNullable(failureMessage);
    }

    public Status status() {
        return status;
    }

    public long sizeBytes() {
        return data.length;
    }

    public boolean isSendable() {
        return status == Status.READY || status == Status.UPLOADED;
    }

    public AttachmentDraft uploading() {
        return withStatus(Status.UPLOADING, null, null);
    }

    public AttachmentDraft uploaded(String url) {
        return withStatus(Status.UPLOADED, requireText(url, "url"), null);
    }

    public AttachmentDraft failed(String message) {
        return withStatus(Status.FAILED, uploadedUrl, requireText(message, "message"));
    }

    public AttachmentDraft ready() {
        return withStatus(Status.READY, null, null);
    }

    private AttachmentDraft withStatus(Status nextStatus, @Nullable String nextUploadedUrl, @Nullable String nextFailureMessage) {
        return new AttachmentDraft(
                type,
                fileName,
                displayName,
                source,
                data,
                file,
                contentType,
                nextUploadedUrl,
                nextFailureMessage,
                nextStatus);
    }

    private static String requireText(String value, String label) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeDisplayName(String displayName, String fallbackFileName) {
        String normalized = normalizeOptionalText(displayName);
        if (normalized != null) {
            return normalized;
        }
        int dot = fallbackFileName.lastIndexOf('.');
        if (dot > 0) {
            return fallbackFileName.substring(0, dot);
        }
        return fallbackFileName;
    }

    private static @Nullable String normalizeOptionalText(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}