package com.chat.upgrade.server;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.net.ExternalMediaUrlPolicy;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.net.StructuredChatProtocolLimits;
import com.chat.upgrade.server.store.StoredAttachment;
import com.chat.upgrade.server.store.StoredMedia;

public final class ServerAttachmentService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final ConcurrentHashMap<String, OwnedAttachment> ATTACHMENTS = new ConcurrentHashMap<>();

    private ServerAttachmentService() {
    }

    public static void clearAll() {
        ATTACHMENTS.clear();
    }

    public static Optional<StoredAttachment> createForMedia(
            UUID ownerId,
            String mediaId,
            @Nullable String typeWire,
            @Nullable String displayName) {
        String safeMediaId = normalizeOptional(mediaId);
        if (ownerId == null || safeMediaId == null || !ServerMediaService.isOwner(ownerId, safeMediaId)) {
            return Optional.empty();
        }
        Optional<StoredMedia> mediaOpt = ServerMediaService.get(safeMediaId);
        if (mediaOpt.isEmpty()) {
            return Optional.empty();
        }
        String safeType = normalizeOptional(typeWire);
        if (safeType == null) {
            safeType = mediaOpt.get().typeWire();
        }
        return put(ownerId, StructuredAttachment.serverMedia(null, safeMediaId, safeType, displayName));
    }

    public static Optional<StoredAttachment> createExternal(
            UUID ownerId,
            @Nullable String typeWire,
            @Nullable String displayName,
            String fallbackUrl) {
        if (ownerId == null || !ServerMediaServerConfig.get().allowExternalAttachmentUrls) {
            return Optional.empty();
        }
        try {
            return put(ownerId, StructuredAttachment.externalUrl(null, typeWire, displayName, fallbackUrl));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static Optional<StoredAttachment> put(UUID ownerId, StructuredAttachment descriptor) {
        if (ownerId == null || descriptor == null || !descriptorAllowed(ownerId, descriptor)) {
            return Optional.empty();
        }
        StructuredAttachment assigned = descriptor.hasAttachmentId()
                ? descriptor
                : descriptor.withAttachmentId(unusedAttachmentId());
        long now = System.currentTimeMillis();
        StoredAttachment stored = new StoredAttachment(
                assigned.requireAttachmentId(),
                assigned.mediaId(),
                assigned.typeWire(),
                assigned.displayName(),
                assigned.fallbackUrl(),
                assigned.schemaVersion(),
                now,
                expiresAtMs(now));
        OwnedAttachment candidate = new OwnedAttachment(ownerId, stored);
        OwnedAttachment existing = ATTACHMENTS.putIfAbsent(stored.attachmentId(), candidate);
        if (existing == null) {
            return Optional.of(stored);
        }
        if (existing.ownerId().equals(ownerId)
                && existing.attachment().descriptor().equals(stored.descriptor())) {
            return Optional.of(existing.attachment());
        }
        return Optional.empty();
    }

    public static Optional<StoredAttachment> get(String attachmentId) {
        String safeId = normalizeOptional(attachmentId);
        if (safeId == null) {
            return Optional.empty();
        }
        OwnedAttachment owned = ATTACHMENTS.get(safeId);
        if (owned == null) {
            return Optional.empty();
        }
        StoredAttachment stored = owned.attachment();
        if (stored.isExpired(System.currentTimeMillis())) {
            ATTACHMENTS.remove(safeId, owned);
            return Optional.empty();
        }
        return Optional.of(stored);
    }

    public static Optional<StoredAttachment> getForPlayer(UUID playerId, String attachmentId) {
        String safeId = normalizeOptional(attachmentId);
        if (playerId == null || safeId == null) {
            return Optional.empty();
        }
        OwnedAttachment owned = ATTACHMENTS.get(safeId);
        if (owned == null) {
            return Optional.empty();
        }
        Optional<StoredAttachment> stored = get(safeId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (owned.ownerId().equals(playerId)) {
            return stored;
        }
        String mediaId = stored.get().mediaId();
        return mediaId != null && ServerMediaService.getForPlayer(playerId, mediaId).isPresent()
                ? stored
                : Optional.empty();
    }

    public static Optional<StoredAttachment> findByMediaId(String mediaId) {
        String safeMediaId = normalizeOptional(mediaId);
        if (safeMediaId == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        for (OwnedAttachment owned : new ArrayList<>(ATTACHMENTS.values())) {
            if (owned == null) {
                continue;
            }
            StoredAttachment stored = owned.attachment();
            if (stored.isExpired(now)) {
                ATTACHMENTS.remove(stored.attachmentId(), owned);
                continue;
            }
            if (safeMediaId.equals(stored.mediaId())) {
                return Optional.of(stored);
            }
        }
        return Optional.empty();
    }

    public static Optional<StoredAttachment> findByMediaIdForPlayer(UUID playerId, String mediaId) {
        if (playerId == null || ServerMediaService.getForPlayer(playerId, mediaId).isEmpty()) {
            return Optional.empty();
        }
        return findByMediaId(mediaId);
    }

    public static Optional<StructuredAttachment> descriptor(String attachmentId) {
        return get(attachmentId).map(StoredAttachment::descriptor);
    }

    public static void delete(String attachmentId) {
        String safeId = normalizeOptional(attachmentId);
        if (safeId != null) {
            ATTACHMENTS.remove(safeId);
        }
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        for (OwnedAttachment owned : new ArrayList<>(ATTACHMENTS.values())) {
            if (owned != null && owned.attachment().isExpired(now)) {
                ATTACHMENTS.remove(owned.attachment().attachmentId(), owned);
            }
        }
    }

    private static boolean descriptorAllowed(UUID ownerId, StructuredAttachment descriptor) {
        if (!StructuredChatProtocolLimits.acceptsAttachment(descriptor)) {
            return false;
        }
        if (!descriptor.hasMedia()) {
            return descriptor.fallbackUrl() != null
                    && ExternalMediaUrlPolicy.isAllowed(descriptor.fallbackUrl())
                    && ServerMediaServerConfig.get().allowExternalAttachmentUrls;
        }
        Optional<StoredMedia> media = ServerMediaService.get(descriptor.mediaId());
        if (media.isEmpty()
                || !ServerMediaService.isOwner(ownerId, descriptor.mediaId())
                || !media.get().typeWire().equals(descriptor.typeWire())) {
            return false;
        }
        if (descriptor.fallbackUrl() == null) {
            return true;
        }
        Optional<ServerMediaUrl.Parsed> internal = ServerMediaUrl.parse(descriptor.fallbackUrl());
        if (internal.isPresent()) {
            return internal.get().mediaId().equals(descriptor.mediaId())
                    && internal.get().typeWire().equals(descriptor.typeWire());
        }
        return ServerMediaServerConfig.get().allowExternalAttachmentUrls
                && ExternalMediaUrlPolicy.isAllowed(descriptor.fallbackUrl());
    }

    private static long expiresAtMs(long nowMs) {
        long ttlSeconds = ServerMediaServerConfig.get().ttlSeconds;
        if (ttlSeconds <= 0L) {
            return 0L;
        }
        long ttlMs = ttlSeconds >= Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : ttlSeconds * 1000L;
        if (nowMs >= Long.MAX_VALUE - ttlMs) {
            return Long.MAX_VALUE;
        }
        return nowMs + ttlMs;
    }

    private static String unusedAttachmentId() {
        String id;
        do {
            id = randomAttachmentIdHex();
        } while (ATTACHMENTS.containsKey(id));
        return id;
    }

    private static String randomAttachmentIdHex() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >>> 4) & 0xF, 16));
            out.append(Character.forDigit(value & 0xF, 16));
        }
        return out.toString();
    }

    private record OwnedAttachment(UUID ownerId, StoredAttachment attachment) {
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}