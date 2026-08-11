package com.chat.upgrade.client.net.servermedia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chat.upgrade.client.media.model.InlineResourceType;

import org.junit.jupiter.api.Test;

final class IncomingMediaAssemblyTest {
    private static final String MEDIA_ID = "0123456789abcdef0123456789abcdef";
    private static final ServerMediaCapability CAPABILITY = new ServerMediaCapability(
            true,
            4_096,
            1_024,
            ServerMediaCapability.StorageMode.MEMORY,
            60,
            false,
            0);

    @Test
    void rejectsMetadataBeforeAllocatingAssembly() {
        assertTrue(create(MEDIA_ID, InlineResourceType.IMAGE, "image/png", "", 2_048, 2).isPresent());
        assertFalse(create("invalid", InlineResourceType.IMAGE, "image/png", "", 2_048, 2).isPresent());
        assertFalse(create(MEDIA_ID, InlineResourceType.IMAGE, "audio/ogg", "", 2_048, 2).isPresent());
        assertFalse(create(MEDIA_ID, InlineResourceType.IMAGE, "image/png", "xyz", 2_048, 2).isPresent());
        assertFalse(create(MEDIA_ID, InlineResourceType.IMAGE, "image/png", "", 4_097, 5).isPresent());
        assertFalse(create(MEDIA_ID, InlineResourceType.IMAGE, "image/png", "", 2_048,
                Integer.MAX_VALUE).isPresent());
    }

    @Test
    void rejectsMediaThatFitsTheServerButExceedsTheClientReceiveLimit() {
        ServerMediaCapability serverLimit = new ServerMediaCapability(
                true,
                10 * 1_024 * 1_024,
                32 * 1_024,
                ServerMediaCapability.StorageMode.MEMORY,
                60,
                false,
                0);

        assertFalse(IncomingMediaAssembly.create(
                MEDIA_ID,
                InlineResourceType.VIDEO,
                "video/mp4",
                "",
                3 * 1_024 * 1_024,
                96,
                serverLimit,
                2 * 1_024 * 1_024,
                10_000L).isPresent());
    }

    @Test
    void requiresExactChunkLengthsAndCompletesOutOfOrder() {
        IncomingMediaAssembly assembly = create(
                MEDIA_ID,
                InlineResourceType.IMAGE,
                "image/png",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                2_500,
                3).orElseThrow();

        assertThrows(IllegalStateException.class, assembly::completedBody);
        assertEquals(IncomingMediaAssembly.AcceptStatus.PENDING,
                assembly.acceptChunk(2, filled(452, (byte) 3), 1_001L));
        assertEquals(IncomingMediaAssembly.AcceptStatus.PENDING,
                assembly.acceptChunk(0, filled(1_024, (byte) 1), 1_002L));
        assertEquals(IncomingMediaAssembly.AcceptStatus.COMPLETED,
                assembly.acceptChunk(1, filled(1_024, (byte) 2), 1_003L));

        byte[] expected = new byte[2_500];
        java.util.Arrays.fill(expected, 0, 1_024, (byte) 1);
        java.util.Arrays.fill(expected, 1_024, 2_048, (byte) 2);
        java.util.Arrays.fill(expected, 2_048, 2_500, (byte) 3);
        assertArrayEquals(expected, assembly.completedBody());
    }

    @Test
    void rejectsDuplicateWrongSizedAndOutOfRangeChunks() {
        IncomingMediaAssembly duplicate = create(
                MEDIA_ID, InlineResourceType.AUDIO, "audio/ogg", "", 2_048, 2).orElseThrow();
        assertEquals(IncomingMediaAssembly.AcceptStatus.PENDING,
                duplicate.acceptChunk(0, new byte[1_024], 1_001L));
        assertEquals(IncomingMediaAssembly.AcceptStatus.REJECTED,
                duplicate.acceptChunk(0, new byte[1_024], 1_002L));

        IncomingMediaAssembly wrongSize = create(
                MEDIA_ID, InlineResourceType.VIDEO, "video/mp4", "", 2_500, 3).orElseThrow();
        assertEquals(IncomingMediaAssembly.AcceptStatus.REJECTED,
                wrongSize.acceptChunk(2, new byte[451], 1_001L));
        assertEquals(IncomingMediaAssembly.AcceptStatus.REJECTED,
                wrongSize.acceptChunk(3, new byte[1], 1_001L));
        assertEquals(IncomingMediaAssembly.AcceptStatus.REJECTED,
                wrongSize.acceptChunk(-1, new byte[1], 1_001L));
    }

    @Test
    void expiryIgnoresWallClockRollback() {
        IncomingMediaAssembly assembly = create(
                MEDIA_ID, InlineResourceType.IMAGE, "image/png", "", 2_048, 2).orElseThrow();
        assertEquals(IncomingMediaAssembly.AcceptStatus.PENDING,
                assembly.acceptChunk(0, new byte[1_024], 1_000L));
        assertFalse(assembly.isExpired(1_000L, 30_000L));
        assertFalse(assembly.isExpired(39_999L, 30_000L));
        assertTrue(assembly.isExpired(40_000L, 30_000L));
    }

    private static java.util.Optional<IncomingMediaAssembly> create(
            String mediaId,
            InlineResourceType type,
            String contentType,
            String fingerprint,
            int totalLen,
            int totalChunks) {
        return IncomingMediaAssembly.create(
                mediaId,
                type,
                contentType,
                fingerprint,
                totalLen,
                totalChunks,
                CAPABILITY,
                4_096,
                10_000L);
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}