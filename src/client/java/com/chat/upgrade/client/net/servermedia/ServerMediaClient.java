package com.chat.upgrade.client.net.servermedia;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;

/**
 * Client-side bridge for resolving {@link ServerMediaUrl} references via server packets.
 */
public final class ServerMediaClient {
    private static final ConcurrentHashMap<String, Boolean> REQUESTED_MEDIA_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, StructuredAttachment> ATTACHMENTS_BY_MEDIA_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, StructuredAttachment> ATTACHMENTS_BY_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> PENDING_ATTACHMENT_MEDIA_IDS = new ConcurrentHashMap<>();
    private static volatile ServerMediaCapability capability = ServerMediaCapability.unavailable();

    private ServerMediaClient() {
    }

    public static void setCapability(ServerMediaCapability cap) {
        ServerMediaCapability previous = capability;
        if (cap == null) {
            capability = ServerMediaCapability.unavailable();
            return;
        }
        capability = cap.withAttachmentMetadata(
                previous.attachmentMetadataEnabled(),
                previous.attachmentSchemaVersion());
    }

    public static void setAttachmentCapability(boolean enabled, int schemaVersion) {
        capability = capability.withAttachmentMetadata(enabled, schemaVersion);
    }

    public static ServerMediaCapability capability() {
        return capability;
    }

    public static void clearRuntimeState() {
        REQUESTED_MEDIA_IDS.clear();
        ATTACHMENTS_BY_MEDIA_ID.clear();
        ATTACHMENTS_BY_ID.clear();
        PENDING_ATTACHMENT_MEDIA_IDS.clear();
        capability = ServerMediaCapability.unavailable();
    }

    public static boolean isServerMediaUrl(String url) {
        return ServerMediaUrl.isServerMediaUrl(url);
    }

    public static void requestIfNeeded(String url) {
        Optional<ServerMediaUrl.Parsed> parsed = ServerMediaUrl.parse(url);
        if (parsed.isEmpty()) {
            return;
        }
        String mediaId = parsed.get().mediaId();
        if (!capability.enabled()) {
            return;
        }
        boolean first = REQUESTED_MEDIA_IDS.putIfAbsent(mediaId, Boolean.TRUE) == null;
        if (!first) {
            return;
        }
        try {
            ServerMediaNetworking.sendRequest(mediaId);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to request server media {}: {}", mediaId, e.getMessage());
        }
    }

    public static CompletableFuture<Optional<StructuredAttachment>> submitAttachment(StructuredAttachment attachment) {
        if (attachment == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<StructuredAttachment>> future = ServerMediaNetworking.submitAttachment(attachment);
        future.thenAccept(result -> result.ifPresent(ServerMediaClient::rememberAttachment));
        return future;
    }

    public static CompletableFuture<Optional<StructuredAttachment>> submitAttachment(RichAttachment attachment) {
        if (attachment == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            return submitAttachment(attachment.toStructured());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot submit attachment metadata: {}", ex.getMessage());
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public static CompletableFuture<Optional<StructuredAttachment>> requestAttachmentById(String attachmentId) {
        Optional<StructuredAttachment> cached = cachedAttachmentById(attachmentId);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Optional<StructuredAttachment>> future = ServerMediaNetworking.requestAttachment(attachmentId, null);
        future.thenAccept(result -> result.ifPresent(ServerMediaClient::rememberAttachment));
        return future;
    }

    public static CompletableFuture<Optional<StructuredAttachment>> requestAttachmentByMediaId(String mediaId) {
        Optional<StructuredAttachment> cached = cachedAttachmentByMediaId(mediaId);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Optional<StructuredAttachment>> future = ServerMediaNetworking.requestAttachment(null, mediaId);
        future.thenAccept(result -> result.ifPresent(ServerMediaClient::rememberAttachment));
        return future;
    }

    public static Optional<StructuredAttachment> cachedAttachmentById(String attachmentId) {
        String safeId = normalizeOptional(attachmentId);
        return safeId == null ? Optional.empty() : Optional.ofNullable(ATTACHMENTS_BY_ID.get(safeId));
    }

    public static Optional<StructuredAttachment> cachedAttachmentByMediaId(String mediaId) {
        String safeMediaId = normalizeOptional(mediaId);
        return safeMediaId == null ? Optional.empty() : Optional.ofNullable(ATTACHMENTS_BY_MEDIA_ID.get(safeMediaId));
    }

    public static Optional<StructuredAttachment> cachedAttachmentForUrl(String url) {
        Optional<ServerMediaUrl.Parsed> parsed = ServerMediaUrl.parse(url);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        return cachedAttachmentByMediaId(parsed.get().mediaId());
    }

    public static void requestAttachmentForUrlIfNeeded(String url) {
        Optional<ServerMediaUrl.Parsed> parsed = ServerMediaUrl.parse(url);
        if (parsed.isEmpty() || !capability.attachmentMetadataEnabled()) {
            return;
        }
        String mediaId = parsed.get().mediaId();
        if (ATTACHMENTS_BY_MEDIA_ID.containsKey(mediaId)) {
            return;
        }
        boolean first = PENDING_ATTACHMENT_MEDIA_IDS.putIfAbsent(mediaId, Boolean.TRUE) == null;
        if (!first) {
            return;
        }
        requestAttachmentByMediaId(mediaId).whenComplete((result, error) -> PENDING_ATTACHMENT_MEDIA_IDS.remove(mediaId));
    }

    public static void forgetRequestForUrl(String url) {
        Optional<ServerMediaUrl.Parsed> parsed = ServerMediaUrl.parse(url);
        if (parsed.isEmpty()) {
            return;
        }
        REQUESTED_MEDIA_IDS.remove(parsed.get().mediaId());
    }

    public static void acceptMediaBytes(
            String mediaId,
            InlineResourceType type,
            @Nullable String contentType,
            @Nullable String md5Hex,
            byte[] body) {
        if (mediaId == null || mediaId.isBlank() || type == null || body == null) {
            return;
        }
        String url = ServerMediaUrl.format(mediaId, type.toWire());
        String ct = contentType == null ? "unknown" : contentType;
        switch (type) {
            case IMAGE -> ImageLoader.loadFromBytes(url, body, ct, md5Hex);
            case AUDIO -> AudioLoader.loadFromBytes(url, body, ct, md5Hex);
            case VIDEO -> VideoLoader.loadFromBytes(url, body, ct, md5Hex);
        }
    }

    static void rememberAttachment(StructuredAttachment attachment) {
        String attachmentId = normalizeOptional(attachment.attachmentId());
        if (attachmentId != null) {
            ATTACHMENTS_BY_ID.put(attachmentId, attachment);
        }
        String mediaId = normalizeOptional(attachment.mediaId());
        if (mediaId != null) {
            ATTACHMENTS_BY_MEDIA_ID.put(mediaId, attachment);
        }
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

