package com.chat.upgrade.server;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.server.store.StoredAttachment;
import com.chat.upgrade.server.store.StoredMedia;

public final class ServerAttachmentService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final ConcurrentHashMap<String, StoredAttachment> ATTACHMENTS = new ConcurrentHashMap<>();

    private ServerAttachmentService() {
    }

    public static void clearAll() {
        ATTACHMENTS.clear();
    }

    public static Optional<StoredAttachment> createForMedia(
            String mediaId,
            @Nullable String typeWire,
            @Nullable String displayName) {
        String safeMediaId = normalizeOptional(mediaId);
        if (safeMediaId == null) {
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
        return Optional.of(put(StructuredAttachment.serverMedia(null, safeMediaId, safeType, displayName)));
    }

    public static Optional<StoredAttachment> createExternal(
            @Nullable String typeWire,
            @Nullable String displayName,
            String fallbackUrl) {
        try {
            return Optional.of(put(StructuredAttachment.externalUrl(null, typeWire, displayName, fallbackUrl)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static StoredAttachment put(StructuredAttachment descriptor) {
        StructuredAttachment assigned = descriptor.hasAttachmentId()
                ? descriptor
                : descriptor.withAttachmentId(randomAttachmentIdHex());
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
        ATTACHMENTS.put(stored.attachmentId(), stored);
        return stored;
    }

    public static Optional<StoredAttachment> get(String attachmentId) {
        String safeId = normalizeOptional(attachmentId);
        if (safeId == null) {
            return Optional.empty();
        }
        StoredAttachment stored = ATTACHMENTS.get(safeId);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.isExpired(System.currentTimeMillis())) {
            ATTACHMENTS.remove(safeId, stored);
            return Optional.empty();
        }
        return Optional.of(stored);
    }

    public static Optional<StoredAttachment> findByMediaId(String mediaId) {
        String safeMediaId = normalizeOptional(mediaId);
        if (safeMediaId == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        for (StoredAttachment stored : new ArrayList<>(ATTACHMENTS.values())) {
            if (stored == null) {
                continue;
            }
            if (stored.isExpired(now)) {
                ATTACHMENTS.remove(stored.attachmentId(), stored);
                continue;
            }
            if (safeMediaId.equals(stored.mediaId())) {
                return Optional.of(stored);
            }
        }
        return Optional.empty();
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
        for (StoredAttachment attachment : new ArrayList<>(ATTACHMENTS.values())) {
            if (attachment != null && attachment.isExpired(now)) {
                ATTACHMENTS.remove(attachment.attachmentId(), attachment);
            }
        }
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

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}