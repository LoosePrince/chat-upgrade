package com.chat.upgrade.server;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.server.store.DiskMediaStore;
import com.chat.upgrade.server.store.InMemoryMediaStore;
import com.chat.upgrade.server.store.MediaStore;
import com.chat.upgrade.server.store.StoredMedia;

import net.fabricmc.loader.api.FabricLoader;

public final class ServerMediaService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final ConcurrentHashMap<Long, PendingUpload> UPLOADS = new ConcurrentHashMap<>();
    private static volatile MediaStore store = new InMemoryMediaStore();

    private ServerMediaService() {
    }

    public static void initFromConfig() {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        if (cfg.storageMode == ServerMediaServerConfig.StorageMode.DISK) {
            try {
                var root = FabricLoader.getInstance().getConfigDir()
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
        UPLOADS.clear();
        store = new InMemoryMediaStore();
    }

    public static void beginUpload(long uploadId, String typeWire, String contentType,
            int totalLen, int totalChunks) {
        if (uploadId == 0L) {
            return;
        }
        UPLOADS.put(uploadId, new PendingUpload(uploadId, typeWire, contentType, totalLen, totalChunks));
    }

    public static Optional<UploadCompleted> acceptUploadChunk(long uploadId, int idx, byte[] chunk,
            int maxSingleBytes) {
        PendingUpload upload = UPLOADS.get(uploadId);
        if (upload == null) {
            return Optional.empty();
        }
        boolean done = upload.acceptChunk(idx, chunk);
        if (!done) {
            return Optional.empty();
        }
        UPLOADS.remove(uploadId, upload);
        byte[] body = upload.build();
        if (body.length > maxSingleBytes) {
            return Optional.of(new UploadCompleted(uploadId, null, "too_large"));
        }
        String fingerprint = fingerprint(upload.typeWire(), body);
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
            store.put(new StoredMedia(mediaId, upload.typeWire(), upload.contentType(), fingerprint, body, now, expiresAt));
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to store upload {}: {}", mediaId, e.getMessage());
            return Optional.of(new UploadCompleted(uploadId, null, "store_failed"));
        }
        return Optional.of(new UploadCompleted(uploadId, mediaId, null));
    }

    public static Optional<StoredMedia> get(String mediaId) {
        Optional<StoredMedia> m = store.get(mediaId);
        if (m.isEmpty()) {
            return Optional.empty();
        }
        StoredMedia media = m.get();
        if (media.isExpired(System.currentTimeMillis())) {
            store.delete(mediaId);
            return Optional.empty();
        }
        return Optional.of(media);
    }

    public static void cleanup() {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        store.cleanup(System.currentTimeMillis(), cfg.maxTotalBytes);
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

    private static String fingerprint(String typeWire, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
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
            return "md5_error_" + System.nanoTime();
        }
    }

    public record UploadCompleted(long uploadId, @Nullable String mediaId, @Nullable String error) {
    }

    private static final class PendingUpload {
        private final long uploadId;
        private final String typeWire;
        private final String contentType;
        private final int totalLen;
        private final int totalChunks;
        private final byte[][] chunks;
        private int receivedChunks = 0;
        private int receivedBytes = 0;

        PendingUpload(long uploadId, String typeWire, String contentType, int totalLen,
                int totalChunks) {
            this.uploadId = uploadId;
            this.typeWire = typeWire == null ? "image" : typeWire;
            this.contentType = contentType == null ? "application/octet-stream" : contentType;
            this.totalLen = Math.max(0, totalLen);
            this.totalChunks = Math.max(0, totalChunks);
            this.chunks = new byte[this.totalChunks][];
        }

        String typeWire() {
            return typeWire;
        }

        String contentType() {
            return contentType;
        }

        synchronized boolean acceptChunk(int idx, byte[] chunk) {
            if (idx < 0 || idx >= totalChunks) {
                return false;
            }
            if (chunks[idx] != null) {
                return false;
            }
            chunks[idx] = chunk;
            receivedChunks++;
            receivedBytes += (chunk == null ? 0 : chunk.length);
            return receivedChunks == totalChunks;
        }

        synchronized byte[] build() {
            if (totalChunks == 0) {
                return new byte[0];
            }
            int len = totalLen > 0 ? totalLen : receivedBytes;
            byte[] out = new byte[len];
            int offset = 0;
            for (byte[] c : chunks) {
                if (c == null || c.length == 0) {
                    continue;
                }
                int copy = Math.min(c.length, out.length - offset);
                if (copy <= 0) {
                    break;
                }
                System.arraycopy(c, 0, out, offset, copy);
                offset += copy;
            }
            if (offset != out.length) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: upload {} assembled {} bytes (declared={})",
                        uploadId, offset, out.length);
                byte[] shrink = new byte[offset];
                System.arraycopy(out, 0, shrink, 0, offset);
                return shrink;
            }
            return out;
        }
    }
}

