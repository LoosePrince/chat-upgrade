package com.chat.upgrade.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

final class ServerUploadRegistry {
    private static final int MIN_CHUNK_BYTES = 1_024;
    private static final int MAX_CONTENT_TYPE_CHARS = 128;
    private static final Object LOCK = new Object();
    private static final Map<UploadKey, PendingUpload> UPLOADS = new HashMap<>();
    private static long pendingDeclaredBytes;

    private ServerUploadRegistry() {
    }

    static Optional<String> begin(
            UUID playerId,
            long uploadId,
            String typeWire,
            String contentType,
            int totalLen,
            int totalChunks,
            long nowMs) {
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        String validationError = validateInit(
                playerId,
                uploadId,
                typeWire,
                contentType,
                totalLen,
                totalChunks,
                config);
        if (validationError != null) {
            return Optional.of(validationError);
        }

        synchronized (LOCK) {
            cleanupExpiredLocked(nowMs, config.uploadTimeoutSeconds);
            UploadKey key = new UploadKey(playerId, uploadId);
            if (UPLOADS.containsKey(key)) {
                return Optional.of("duplicate_upload_id");
            }
            long playerUploads = UPLOADS.keySet().stream()
                    .filter(existing -> existing.playerId().equals(playerId))
                    .count();
            if (playerUploads >= config.maxPendingUploadsPerPlayer) {
                return Optional.of("too_many_pending_uploads");
            }
            if (UPLOADS.size() >= config.maxPendingUploadsGlobal) {
                return Optional.of("server_upload_capacity_reached");
            }
            long playerBytes = UPLOADS.entrySet().stream()
                    .filter(entry -> entry.getKey().playerId().equals(playerId))
                    .mapToLong(entry -> entry.getValue().totalLen)
                    .sum();
            if (playerBytes + totalLen > config.maxPendingBytesPerPlayer) {
                return Optional.of("player_pending_bytes_exceeded");
            }
            if (pendingDeclaredBytes + totalLen > config.maxPendingBytesGlobal) {
                return Optional.of("server_pending_bytes_exceeded");
            }

            PendingUpload upload = new PendingUpload(
                    normalizeType(typeWire),
                    normalizeContentType(contentType),
                    totalLen,
                    totalChunks,
                    nowMs);
            UPLOADS.put(key, upload);
            pendingDeclaredBytes += totalLen;
            return Optional.empty();
        }
    }

    static AcceptResult accept(UUID playerId, long uploadId, int index, byte[] chunk, long nowMs) {
        synchronized (LOCK) {
            ServerMediaServerConfig config = ServerMediaServerConfig.get();
            cleanupExpiredLocked(nowMs, config.uploadTimeoutSeconds);
            UploadKey key = new UploadKey(playerId, uploadId);
            PendingUpload upload = UPLOADS.get(key);
            if (upload == null) {
                return AcceptResult.rejected("upload_not_found");
            }

            String error = upload.accept(index, chunk, config.maxChunkBytes, nowMs);
            if (error != null) {
                removeLocked(key, upload);
                return AcceptResult.rejected(error);
            }
            if (!upload.complete()) {
                return AcceptResult.pending();
            }

            removeLocked(key, upload);
            if (upload.receivedBytes != upload.totalLen) {
                return AcceptResult.rejected("declared_length_mismatch");
            }
            return AcceptResult.completed(
                    upload.typeWire,
                    upload.contentType,
                    upload.build());
        }
    }

    static void discard(UUID playerId, long uploadId) {
        if (playerId == null) {
            return;
        }
        synchronized (LOCK) {
            UploadKey key = new UploadKey(playerId, uploadId);
            PendingUpload upload = UPLOADS.get(key);
            if (upload != null) {
                removeLocked(key, upload);
            }
        }
    }

    static void discardPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (LOCK) {
            UPLOADS.entrySet().removeIf(entry -> {
                if (!entry.getKey().playerId().equals(playerId)) {
                    return false;
                }
                pendingDeclaredBytes -= entry.getValue().totalLen;
                return true;
            });
            pendingDeclaredBytes = Math.max(0L, pendingDeclaredBytes);
        }
    }

    static void cleanup(long nowMs) {
        synchronized (LOCK) {
            cleanupExpiredLocked(nowMs, ServerMediaServerConfig.get().uploadTimeoutSeconds);
        }
    }

    static void clear() {
        synchronized (LOCK) {
            UPLOADS.clear();
            pendingDeclaredBytes = 0L;
        }
    }

    private static @Nullable String validateInit(
            @Nullable UUID playerId,
            long uploadId,
            @Nullable String typeWire,
            @Nullable String contentType,
            int totalLen,
            int totalChunks,
            ServerMediaServerConfig config) {
        if (playerId == null || uploadId == 0L) {
            return "invalid_upload_id";
        }
        if (!isSupportedType(typeWire)) {
            return "invalid_media_type";
        }
        if (!validContentType(contentType)) {
            return "invalid_content_type";
        }
        if (totalLen <= 0 || totalLen > config.maxSingleBytes) {
            return "invalid_total_length";
        }
        int minimumChunks = divideCeil(totalLen, config.maxChunkBytes);
        int maximumChunks = divideCeil(totalLen, MIN_CHUNK_BYTES);
        if (totalChunks < minimumChunks || totalChunks > maximumChunks) {
            return "invalid_chunk_count";
        }
        return null;
    }

    private static void cleanupExpiredLocked(long nowMs, int timeoutSeconds) {
        long timeoutMs = Math.max(1L, timeoutSeconds) * 1_000L;
        UPLOADS.entrySet().removeIf(entry -> {
            PendingUpload upload = entry.getValue();
            long idleMs = nowMs >= upload.lastActivityMs ? nowMs - upload.lastActivityMs : 0L;
            if (idleMs < timeoutMs) {
                return false;
            }
            pendingDeclaredBytes -= upload.totalLen;
            return true;
        });
        pendingDeclaredBytes = Math.max(0L, pendingDeclaredBytes);
    }

    private static void removeLocked(UploadKey key, PendingUpload upload) {
        if (UPLOADS.remove(key, upload)) {
            pendingDeclaredBytes = Math.max(0L, pendingDeclaredBytes - upload.totalLen);
        }
    }

    private static int divideCeil(int value, int divisor) {
        return (int) ((value + (long) divisor - 1L) / divisor);
    }

    private static boolean isSupportedType(@Nullable String value) {
        return "image".equalsIgnoreCase(value)
                || "audio".equalsIgnoreCase(value)
                || "video".equalsIgnoreCase(value);
    }

    private static String normalizeType(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean validContentType(@Nullable String value) {
        if (value == null || value.isBlank() || value.length() > MAX_CONTENT_TYPE_CHARS) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isISOControl(current) || Character.isWhitespace(current)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeContentType(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private record UploadKey(UUID playerId, long uploadId) {
    }

    record AcceptResult(Status status, @Nullable String typeWire, @Nullable String contentType,
            @Nullable byte[] body, @Nullable String error) {
        static AcceptResult pending() {
            return new AcceptResult(Status.PENDING, null, null, null, null);
        }

        static AcceptResult completed(String typeWire, String contentType, byte[] body) {
            return new AcceptResult(Status.COMPLETED, typeWire, contentType, body, null);
        }

        static AcceptResult rejected(String error) {
            return new AcceptResult(Status.REJECTED, null, null, null, error);
        }
    }

    enum Status {
        PENDING,
        COMPLETED,
        REJECTED
    }

    private static final class PendingUpload {
        private final String typeWire;
        private final String contentType;
        private final int totalLen;
        private final byte[][] chunks;
        private int receivedChunks;
        private int receivedBytes;
        private long lastActivityMs;

        private PendingUpload(
                String typeWire,
                String contentType,
                int totalLen,
                int totalChunks,
                long nowMs) {
            this.typeWire = typeWire;
            this.contentType = contentType;
            this.totalLen = totalLen;
            this.chunks = new byte[totalChunks][];
            this.lastActivityMs = nowMs;
        }

        private @Nullable String accept(int index, @Nullable byte[] chunk, int maxChunkBytes, long nowMs) {
            if (index < 0 || index >= chunks.length) {
                return "invalid_chunk_index";
            }
            if (chunk == null || chunk.length == 0 || chunk.length > maxChunkBytes) {
                return "invalid_chunk_size";
            }
            if (chunks[index] != null) {
                return "duplicate_chunk";
            }
            if (receivedBytes + chunk.length > totalLen) {
                return "declared_length_exceeded";
            }
            chunks[index] = chunk.clone();
            receivedChunks++;
            receivedBytes += chunk.length;
            lastActivityMs = Math.max(lastActivityMs, nowMs);
            return null;
        }

        private boolean complete() {
            return receivedChunks == chunks.length;
        }

        private byte[] build() {
            byte[] body = new byte[totalLen];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, body, offset, chunk.length);
                offset += chunk.length;
            }
            return body;
        }
    }
}