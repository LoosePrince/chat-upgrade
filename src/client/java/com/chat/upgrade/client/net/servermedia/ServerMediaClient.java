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
        return ServerMediaNetworking.submitAttachment(attachment);
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
        return ServerMediaNetworking.requestAttachment(attachmentId, null);
    }

    public static CompletableFuture<Optional<StructuredAttachment>> requestAttachmentByMediaId(String mediaId) {
        return ServerMediaNetworking.requestAttachment(null, mediaId);
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
}

