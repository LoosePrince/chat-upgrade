package com.chat.upgrade.client.net.servermedia;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.net.ServerMediaId;

final class IncomingMediaAssembly {
    private static final int MAX_CONTENT_TYPE_CHARS = 128;

    private final String mediaId;
    private final InlineResourceType type;
    private final String contentType;
    private final @Nullable String fingerprint;
    private final int chunkBytes;
    private final byte[] body;
    private final boolean[] received;
    private int receivedChunks;
    private long lastActivityMs;

    private IncomingMediaAssembly(
            String mediaId,
            InlineResourceType type,
            String contentType,
            @Nullable String fingerprint,
            int totalLen,
            int totalChunks,
            int chunkBytes,
            long nowMs) {
        this.mediaId = mediaId;
        this.type = type;
        this.contentType = contentType;
        this.fingerprint = fingerprint;
        this.chunkBytes = chunkBytes;
        this.body = new byte[totalLen];
        this.received = new boolean[totalChunks];
        this.lastActivityMs = nowMs;
    }

    static Optional<IncomingMediaAssembly> create(
            String mediaId,
            @Nullable InlineResourceType type,
            @Nullable String contentType,
            @Nullable String fingerprint,
            int totalLen,
            int totalChunks,
            ServerMediaCapability capability,
            int receiveLimitBytes,
            long nowMs) {
        if (!ServerMediaId.isValid(mediaId)
                || type == null
                || capability == null
                || !capability.enabled()
                || !validContentType(type, contentType)) {
            return Optional.empty();
        }
        int effectiveLimit = Math.min(
                Math.min(capability.maxSingleBytes(), ChatUpgradeConfig.ABSOLUTE_MAX_RECEIVE_BYTES),
                Math.max(0, receiveLimitBytes));
        int chunkBytes = capability.maxChunkBytes();
        if (totalLen <= 0
                || totalLen > effectiveLimit
                || chunkBytes < 1_024
                || chunkBytes > 256 * 1_024
                || totalChunks != divideCeil(totalLen, chunkBytes)) {
            return Optional.empty();
        }
        String normalizedFingerprint = normalizeFingerprint(fingerprint);
        if (fingerprint != null && !fingerprint.isBlank() && normalizedFingerprint == null) {
            return Optional.empty();
        }
        return Optional.of(new IncomingMediaAssembly(
                mediaId.toLowerCase(java.util.Locale.ROOT),
                type,
                contentType.trim().toLowerCase(java.util.Locale.ROOT),
                normalizedFingerprint,
                totalLen,
                totalChunks,
                chunkBytes,
                nowMs));
    }

    String mediaId() {
        return mediaId;
    }

    InlineResourceType type() {
        return type;
    }

    String contentType() {
        return contentType;
    }

    @Nullable String fingerprint() {
        return fingerprint;
    }

    int declaredBytes() {
        return body.length;
    }

    boolean isExpired(long nowMs, long timeoutMs) {
        long idleMs = nowMs >= lastActivityMs ? nowMs - lastActivityMs : 0L;
        return idleMs >= Math.max(0L, timeoutMs);
    }

    synchronized AcceptStatus acceptChunk(int index, @Nullable byte[] chunk, long nowMs) {
        if (index < 0 || index >= received.length || chunk == null || received[index]) {
            return AcceptStatus.REJECTED;
        }
        int offset = index * chunkBytes;
        int expectedLength = Math.min(chunkBytes, body.length - offset);
        if (chunk.length != expectedLength) {
            return AcceptStatus.REJECTED;
        }
        System.arraycopy(chunk, 0, body, offset, chunk.length);
        received[index] = true;
        receivedChunks++;
        lastActivityMs = Math.max(lastActivityMs, nowMs);
        return receivedChunks == received.length ? AcceptStatus.COMPLETED : AcceptStatus.PENDING;
    }

    byte[] completedBody() {
        if (receivedChunks != received.length) {
            throw new IllegalStateException("incoming media is incomplete");
        }
        return body;
    }

    private static boolean validContentType(InlineResourceType type, @Nullable String value) {
        if (value == null || value.isBlank() || value.length() > MAX_CONTENT_TYPE_CHARS) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) || Character.isWhitespace(current)) {
                return false;
            }
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case IMAGE -> normalized.startsWith("image/");
            case AUDIO -> normalized.startsWith("audio/") || normalized.equals("application/ogg");
            case VIDEO -> normalized.startsWith("video/");
        };
    }

    private static @Nullable String normalizeFingerprint(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() != 32 && normalized.length() != 64) {
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.digit(normalized.charAt(index), 16) < 0) {
                return null;
            }
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static int divideCeil(int value, int divisor) {
        return (int) ((value + (long) divisor - 1L) / divisor);
    }

    enum AcceptStatus {
        PENDING,
        COMPLETED,
        REJECTED
    }
}