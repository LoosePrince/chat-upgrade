package com.chat.upgrade.server.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DiskMediaStoreTest {
    private static final String MEDIA_ID = "0123456789abcdef0123456789abcdef";
    private static final String FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsOwnerAndFingerprintAcrossRestart() throws Exception {
        String ownerId = UUID.fromString("00000000-0000-0000-0000-000000000031").toString();
        byte[] body = new byte[] { 1, 2, 3, 4, 5 };
        DiskMediaStore first = new DiskMediaStore(temporaryDirectory);
        first.put(new StoredMedia(
                MEDIA_ID,
                "image",
                "image/png",
                FINGERPRINT,
                body,
                1_000L,
                10_000L,
                ownerId));

        DiskMediaStore restarted = new DiskMediaStore(temporaryDirectory);
        StoredMedia restored = restarted.get(MEDIA_ID.toUpperCase(java.util.Locale.ROOT)).orElseThrow();
        assertEquals(ownerId, restored.ownerId());
        assertEquals(FINGERPRINT, restored.fingerprint());
        assertArrayEquals(body, restored.body());
        assertEquals(MEDIA_ID, restarted.findMediaIdByFingerprint(FINGERPRINT).orElseThrow());
    }

    @Test
    void rejectsInvalidOwnerOnWrite() throws Exception {
        DiskMediaStore store = new DiskMediaStore(temporaryDirectory);
        assertThrows(IllegalArgumentException.class, () -> store.put(new StoredMedia(
                MEDIA_ID,
                "image",
                "image/png",
                FINGERPRINT,
                new byte[] { 1 },
                1_000L,
                0L,
                "not-a-uuid")));
    }

    @Test
    void ignoresMetadataWhoseDeclaredLengthDoesNotMatchFile() throws Exception {
        Files.write(temporaryDirectory.resolve(MEDIA_ID + ".bin"), new byte[] { 1, 2, 3, 4 });
        String metadata = """
                {
                  "mediaId": "%s",
                  "typeWire": "image",
                  "contentType": "image/png",
                  "fingerprint": "%s",
                  "createdAtMs": 1000,
                  "expiresAtMs": 0,
                  "byteLen": 10485760,
                  "ownerId": null
                }
                """.formatted(MEDIA_ID, FINGERPRINT);
        Files.writeString(
                temporaryDirectory.resolve(MEDIA_ID + ".meta.json"),
                metadata,
                StandardCharsets.UTF_8);

        DiskMediaStore store = new DiskMediaStore(temporaryDirectory);
        assertFalse(store.get(MEDIA_ID).isPresent());
        assertTrue(Files.exists(temporaryDirectory.resolve(MEDIA_ID + ".bin")));
        assertFalse(Files.exists(temporaryDirectory.resolve(MEDIA_ID + ".meta.json")));
    }
}