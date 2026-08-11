package com.chat.upgrade.server;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaId;
import com.chat.upgrade.server.store.DiskMediaStore;
import com.chat.upgrade.server.store.InMemoryMediaStore;
import com.chat.upgrade.server.store.MediaStore;
import com.chat.upgrade.server.store.StoredMedia;

import com.chat.upgrade.platform.Platform;

public final class ServerMediaService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final ConcurrentHashMap<String, Set<UUID>> READ_GRANTS = new ConcurrentHashMap<>();
    private static volatile MediaStore store = new InMemoryMediaStore();

    public enum MediaReadFailure {
        NONE,
        NOT_FOUND,
        EXPIRED,
        ACCESS_DENIED
    }

    public record MediaReadResult(@Nullable StoredMedia media, MediaReadFailure failure) {
        boolean found() {
            return media != null;
        }
    }

    private ServerMediaService() {
    }

    public static void initFromConfig() {
        READ_GRANTS.clear();
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        if (cfg.storageMode == ServerMediaServerConfig.StorageMode.DISK) {
            try {
                var root = Platform.configDir()
                        .resolve("chat-upgrade")
                        .resolve(cfg.diskFolderName);
                store = new DiskMediaStore(root);
                ChatUpgrade.LOGGER.info("chat-upgrade: server media store: disk at {}", root);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to init disk media store, falling back to memory: {}",
                        e.getMessage());
                store = new InMemoryMediaStore();
            }
        } else {
            store = new InMemoryMediaStore();
            ChatUpgrade.LOGGER.info("chat-upgrade: server media store: memory");
        }
    }

    public static void clearAll() {
        ServerUploadRegistry.clear();
        READ_GRANTS.clear();
        store = new InMemoryMediaStore();
        ServerAttachmentService.clearAll();
    }

    public static Optional<String> beginUpload(
            UUID playerId,
            long uploadId,
            String typeWire,
            String contentType,
            int totalLen,
            int totalChunks) {
        return ServerUploadRegistry.begin(
                playerId,
                uploadId,
                typeWire,
                contentType,
                totalLen,
                totalChunks,
                System.currentTimeMillis());
    }

    public static Optional<UploadCompleted> acceptUploadChunk(
            UUID playerId,
            long uploadId,
            int index,
            byte[] chunk) {
        ServerUploadRegistry.AcceptResult result = ServerUploadRegistry.accept(
                playerId,
                uploadId,
                index,
                chunk,
                System.currentTimeMillis());
        if (result.status() == ServerUploadRegistry.Status.PENDING) {
            return Optional.empty();
        }
        if (result.status() == ServerUploadRegistry.Status.REJECTED) {
            return Optional.of(new UploadCompleted(uploadId, null, result.error()));
        }

        byte[] body = result.body();
        String typeWire = result.typeWire();
        String contentType = result.contentType();
        if (body == null || typeWire == null || contentType == null) {
            return Optional.of(new UploadCompleted(uploadId, null, "invalid_upload_state"));
        }
        if (!ServerMediaContentPolicy.accepts(typeWire, contentType, body)) {
            return Optional.of(new UploadCompleted(uploadId, null, "content_type_mismatch"));
        }
        String fingerprint = fingerprint(playerId, typeWire, body);
        Optional<String> existingId = store.findMediaIdByFingerprint(fingerprint);
        if (existingId.isPresent()) {
            Optional<StoredMedia> existing = get(existingId.get());
            if (existing.isPresent()) {
                return Optional.of(new UploadCompleted(uploadId, existing.get().mediaId(), null));
            }
        }
        String mediaId = randomMediaIdHex();
        long now = System.currentTimeMillis();
        long ttlSeconds = ServerMediaServerConfig.get().ttlSeconds;
        long expiresAt = ttlSeconds <= 0 ? 0L : (now + ttlSeconds * 1000L);
        try {
            store.put(new StoredMedia(
                    mediaId,
                    typeWire,
                    contentType,
                    fingerprint,
                    body,
                    now,
                    expiresAt,
                    playerId.toString()));
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to store upload {}: {}", mediaId, e.getMessage());
            return Optional.of(new UploadCompleted(uploadId, null, "store_failed"));
        }
        return Optional.of(new UploadCompleted(uploadId, mediaId, null));
    }

    public static void discardUpload(UUID playerId, long uploadId) {
        ServerUploadRegistry.discard(playerId, uploadId);
    }

    public static void discardUploads(UUID playerId) {
        ServerUploadRegistry.discardPlayer(playerId);
    }

    public static Optional<StoredMedia> get(String mediaId) {
        if (!ServerMediaId.isValid(mediaId)) {
            return Optional.empty();
        }
        mediaId = mediaId.toLowerCase(java.util.Locale.ROOT);
        Optional<StoredMedia> m = store.get(mediaId);
        if (m.isEmpty()) {
            return Optional.empty();
        }
        StoredMedia media = m.get();
        if (media.isExpired(System.currentTimeMillis())) {
            store.delete(mediaId);
            READ_GRANTS.remove(mediaId);
            return Optional.empty();
        }
        return Optional.of(media);
    }

    public static MediaReadResult readForPlayer(UUID playerId, String mediaId) {
        if (playerId == null || !ServerMediaId.isValid(mediaId)) {
            return new MediaReadResult(null, MediaReadFailure.NOT_FOUND);
        }
        String normalizedMediaId = mediaId.toLowerCase(java.util.Locale.ROOT);
        Optional<StoredMedia> stored = store.get(normalizedMediaId);
        if (stored.isEmpty()) {
            return new MediaReadResult(null, MediaReadFailure.NOT_FOUND);
        }
        StoredMedia media = stored.get();
        if (media.isExpired(System.currentTimeMillis())) {
            store.delete(normalizedMediaId);
            READ_GRANTS.remove(normalizedMediaId);
            return new MediaReadResult(null, MediaReadFailure.EXPIRED);
        }
        if (isOwner(playerId, media)
                || READ_GRANTS.getOrDefault(normalizedMediaId, Set.of()).contains(playerId)) {
            return new MediaReadResult(media, MediaReadFailure.NONE);
        }
        return new MediaReadResult(null, MediaReadFailure.ACCESS_DENIED);
    }

    public static Optional<StoredMedia> getForPlayer(UUID playerId, String mediaId) {
        return Optional.ofNullable(readForPlayer(playerId, mediaId).media());
    }

    public static boolean isOwner(UUID playerId, String mediaId) {
        return playerId != null && get(mediaId).map(media -> isOwner(playerId, media)).orElse(false);
    }

    public static void grantReadAccess(String mediaId, Collection<UUID> playerIds) {
        if (!ServerMediaId.isValid(mediaId) || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        String normalizedMediaId = mediaId.toLowerCase(java.util.Locale.ROOT);
        if (get(normalizedMediaId).isEmpty()) {
            return;
        }
        Set<UUID> grants = READ_GRANTS.computeIfAbsent(
                normalizedMediaId,
                ignored -> ConcurrentHashMap.newKeySet());
        playerIds.stream().filter(java.util.Objects::nonNull).forEach(grants::add);
    }

    private static boolean isOwner(UUID playerId, StoredMedia media) {
        return media.ownerId() != null && media.ownerId().equals(playerId.toString());
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        ServerUploadRegistry.cleanup(now);
        store.cleanup(now, cfg.maxTotalBytes);
        READ_GRANTS.keySet().removeIf(mediaId -> get(mediaId).isEmpty());
    }

    private static String randomMediaIdHex() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) {
            sb.append(Character.forDigit((x >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    private static String fingerprint(UUID playerId, String typeWire, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(playerId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((byte) 0);
            if (typeWire != null) {
                md.update(typeWire.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            md.update((byte) 0);
            md.update(body);
            byte[] sum = md.digest();
            StringBuilder sb = new StringBuilder(sum.length * 2);
            for (byte b : sum) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record UploadCompleted(long uploadId, @Nullable String mediaId, @Nullable String error) {
    }
}

