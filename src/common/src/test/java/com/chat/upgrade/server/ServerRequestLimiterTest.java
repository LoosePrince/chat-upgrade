package com.chat.upgrade.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ServerRequestLimiterTest {
    private final UUID playerA = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private final UUID playerB = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private ServerMediaConfigSnapshot configSnapshot;

    @BeforeEach
    void configureLimits() {
        configSnapshot = ServerMediaConfigSnapshot.capture();
        ServerRequestLimiter.clear();
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        config.maxStructuredMessagesPer10Seconds = 2;
        config.maxUploadPacketsPer10Seconds = 3;
        config.maxMediaRequestsPer10Seconds = 2;
        config.maxAttachmentWritesPerMinute = 2;
        config.maxHistoryRequestsPerMinute = 2;
    }

    @AfterEach
    void clearState() {
        ServerRequestLimiter.clear();
        configSnapshot.restore();
    }

    @Test
    void refillsProportionallyWithoutAllowingClockRollbackRefill() {
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 10_000L));
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 10_000L));
        assertFalse(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 10_000L));

        assertFalse(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 15_000L));
        assertFalse(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 15_000L));
    }

    @Test
    void keepsBucketsIndependentByPlayerAndRequestKind() {
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));
        assertFalse(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));

        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.MEDIA_READ, 1_000L));
        assertTrue(ServerRequestLimiter.allow(playerB, ServerRequestLimiter.Kind.CHAT, 1_000L));
    }

    @Test
    void discardAndIdleCleanupReleasePlayerState() {
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));
        assertFalse(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_000L));

        ServerRequestLimiter.discard(playerA);
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_001L));

        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_001L));
        assertFalse(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 1_001L));
        ServerRequestLimiter.cleanup(301_001L);
        assertTrue(ServerRequestLimiter.allow(playerA, ServerRequestLimiter.Kind.CHAT, 301_001L));
    }

    @Test
    void rejectsMissingIdentityOrKind() {
        assertFalse(ServerRequestLimiter.allow(null, ServerRequestLimiter.Kind.CHAT, 1_000L));
        assertFalse(ServerRequestLimiter.allow(playerA, null, 1_000L));
    }
}