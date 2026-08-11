package com.chat.upgrade.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ServerUploadRegistryTest {
    private final UUID playerA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID playerB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private ServerMediaConfigSnapshot configSnapshot;

    @BeforeEach
    void configureLimits() {
        configSnapshot = ServerMediaConfigSnapshot.capture();
        ServerUploadRegistry.clear();
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        config.maxSingleBytes = 4_096;
        config.maxChunkBytes = 1_024;
        config.uploadTimeoutSeconds = 5;
        config.maxPendingUploadsPerPlayer = 2;
        config.maxPendingUploadsGlobal = 4;
        config.maxPendingBytesPerPlayer = 4_096;
        config.maxPendingBytesGlobal = 8_192;
    }

    @AfterEach
    void clearState() {
        ServerUploadRegistry.clear();
        configSnapshot.restore();
    }

    @Test
    void rejectsInvalidMetadataWithoutCreatingSession() {
        assertEquals("invalid_total_length",
                ServerUploadRegistry.begin(playerA, 1L, "image", "image/png", 4_097, 5, 1_000L).orElseThrow());
        assertEquals("invalid_chunk_count",
                ServerUploadRegistry.begin(playerA, 2L, "image", "image/png", 2_048,
                        Integer.MAX_VALUE, 1_000L).orElseThrow());
        assertEquals("upload_not_found",
                ServerUploadRegistry.accept(playerA, 2L, 0, new byte[1_024], 1_001L).error());
    }

    @Test
    void bindsSessionToPlayerAndBuildsChunksInIndexOrder() {
        assertTrue(ServerUploadRegistry.begin(
                playerA, 10L, "IMAGE", "IMAGE/PNG", 2_048, 2, 1_000L).isEmpty());

        assertEquals("upload_not_found",
                ServerUploadRegistry.accept(playerB, 10L, 0, new byte[1_024], 1_001L).error());
        assertEquals(ServerUploadRegistry.Status.PENDING,
                ServerUploadRegistry.accept(playerA, 10L, 1, filled(1_024, (byte) 2), 1_002L).status());

        ServerUploadRegistry.AcceptResult completed = ServerUploadRegistry.accept(
                playerA, 10L, 0, filled(1_024, (byte) 1), 1_003L);
        assertEquals(ServerUploadRegistry.Status.COMPLETED, completed.status());
        assertEquals("image", completed.typeWire());
        assertEquals("image/png", completed.contentType());
        byte[] expected = new byte[2_048];
        java.util.Arrays.fill(expected, 0, 1_024, (byte) 1);
        java.util.Arrays.fill(expected, 1_024, 2_048, (byte) 2);
        assertArrayEquals(expected, completed.body());
    }

    @Test
    void malformedChunkTerminatesSessionAndReleasesQuota() {
        ServerMediaServerConfig.get().maxPendingUploadsPerPlayer = 1;
        assertTrue(ServerUploadRegistry.begin(
                playerA, 20L, "image", "image/png", 2_048, 2, 1_000L).isEmpty());
        assertEquals(ServerUploadRegistry.Status.PENDING,
                ServerUploadRegistry.accept(playerA, 20L, 0, new byte[1_024], 1_001L).status());
        assertEquals("duplicate_chunk",
                ServerUploadRegistry.accept(playerA, 20L, 0, new byte[1_024], 1_002L).error());
        assertEquals("upload_not_found",
                ServerUploadRegistry.accept(playerA, 20L, 1, new byte[1_024], 1_003L).error());
        assertTrue(ServerUploadRegistry.begin(
                playerA, 21L, "image", "image/png", 1_024, 1, 1_004L).isEmpty());
    }

    @Test
    void enforcesPendingByteAndSessionQuotasPerPlayer() {
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        config.maxPendingBytesPerPlayer = 2_048;
        config.maxPendingUploadsPerPlayer = 2;

        assertTrue(ServerUploadRegistry.begin(
                playerA, 30L, "image", "image/png", 2_048, 2, 1_000L).isEmpty());
        assertEquals("player_pending_bytes_exceeded",
                ServerUploadRegistry.begin(
                        playerA, 31L, "image", "image/png", 1_024, 1, 1_001L).orElseThrow());
        assertTrue(ServerUploadRegistry.begin(
                playerB, 31L, "image", "image/png", 1_024, 1, 1_001L).isEmpty());

        ServerUploadRegistry.discardPlayer(playerA);
        assertTrue(ServerUploadRegistry.begin(
                playerA, 32L, "image", "image/png", 2_048, 2, 1_002L).isEmpty());
    }

    @Test
    void expiresIdleSessionsButIgnoresClockRollback() {
        assertTrue(ServerUploadRegistry.begin(
                playerA, 40L, "image", "image/png", 2_048, 2, 10_000L).isEmpty());
        assertEquals(ServerUploadRegistry.Status.PENDING,
                ServerUploadRegistry.accept(playerA, 40L, 0, new byte[1_024], 1_000L).status());
        ServerUploadRegistry.cleanup(1_001L);
        assertEquals("duplicate_upload_id",
                ServerUploadRegistry.begin(
                        playerA, 40L, "image", "image/png", 2_048, 2, 1_002L).orElseThrow());

        ServerUploadRegistry.cleanup(14_999L);
        assertEquals("duplicate_upload_id",
                ServerUploadRegistry.begin(
                        playerA, 40L, "image", "image/png", 2_048, 2, 14_999L).orElseThrow());
        ServerUploadRegistry.cleanup(15_000L);
        assertTrue(ServerUploadRegistry.begin(
                playerA, 40L, "image", "image/png", 1_024, 1, 15_001L).isEmpty());
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}