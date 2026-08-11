package com.chat.upgrade.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ServerRequestLimiter {
    private static final Object LOCK = new Object();
    private static final long IDLE_TTL_MS = 5L * 60L * 1_000L;
    private static final Map<Key, Bucket> BUCKETS = new HashMap<>();

    private ServerRequestLimiter() {
    }

    static boolean allow(UUID playerId, Kind kind, long nowMs) {
        if (playerId == null || kind == null) {
            return false;
        }
        Limit limit = limit(kind, ServerMediaServerConfig.get());
        synchronized (LOCK) {
            cleanupLocked(nowMs);
            Bucket bucket = BUCKETS.computeIfAbsent(
                    new Key(playerId, kind),
                    ignored -> new Bucket(limit.capacity(), nowMs));
            return bucket.tryConsume(limit, nowMs);
        }
    }

    static void discard(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (LOCK) {
            BUCKETS.keySet().removeIf(key -> key.playerId().equals(playerId));
        }
    }

    static void cleanup(long nowMs) {
        synchronized (LOCK) {
            cleanupLocked(nowMs);
        }
    }

    static void clear() {
        synchronized (LOCK) {
            BUCKETS.clear();
        }
    }

    private static Limit limit(Kind kind, ServerMediaServerConfig config) {
        return switch (kind) {
            case CHAT -> new Limit(config.maxStructuredMessagesPer10Seconds, 10_000L);
            case UPLOAD_PACKET -> new Limit(config.maxUploadPacketsPer10Seconds, 10_000L);
            case MEDIA_READ -> new Limit(config.maxMediaRequestsPer10Seconds, 10_000L);
            case ATTACHMENT_WRITE -> new Limit(config.maxAttachmentWritesPerMinute, 60_000L);
            case HISTORY_READ -> new Limit(config.maxHistoryRequestsPerMinute, 60_000L);
        };
    }

    private static void cleanupLocked(long nowMs) {
        BUCKETS.entrySet().removeIf(entry -> {
            long lastSeenMs = entry.getValue().lastSeenMs;
            long idleMs = nowMs >= lastSeenMs ? nowMs - lastSeenMs : 0L;
            return idleMs >= IDLE_TTL_MS;
        });
    }

    enum Kind {
        CHAT,
        UPLOAD_PACKET,
        MEDIA_READ,
        ATTACHMENT_WRITE,
        HISTORY_READ
    }

    private record Key(UUID playerId, Kind kind) {
    }

    private record Limit(int capacity, long refillPeriodMs) {
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillMs;
        private long lastSeenMs;

        private Bucket(int capacity, long nowMs) {
            this.tokens = capacity;
            this.lastRefillMs = nowMs;
            this.lastSeenMs = nowMs;
        }

        private boolean tryConsume(Limit limit, long nowMs) {
            long elapsedMs = nowMs >= lastRefillMs ? nowMs - lastRefillMs : 0L;
            double refill = elapsedMs * (limit.capacity() / (double) limit.refillPeriodMs());
            tokens = Math.min(limit.capacity(), tokens + refill);
            lastRefillMs = Math.max(lastRefillMs, nowMs);
            lastSeenMs = Math.max(lastSeenMs, nowMs);
            if (tokens < 1.0D) {
                return false;
            }
            tokens -= 1.0D;
            return true;
        }
    }
}