package com.chat.upgrade.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.server.store.StoredAttachment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ServerMediaAccessControlTest {
    private static final String ATTACHMENT_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private final UUID recipient = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private final UUID stranger = UUID.fromString("00000000-0000-0000-0000-000000000023");
    private ServerMediaConfigSnapshot configSnapshot;

    @BeforeEach
    void configureStore() {
        configSnapshot = ServerMediaConfigSnapshot.capture();
        ServerMediaService.clearAll();
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        config.maxSingleBytes = 4_096;
        config.maxChunkBytes = 1_024;
        config.maxPendingUploadsPerPlayer = 2;
        config.maxPendingUploadsGlobal = 8;
        config.maxPendingBytesPerPlayer = 8_192;
        config.maxPendingBytesGlobal = 32_768;
        config.ttlSeconds = 3_600;
        config.allowExternalAttachmentUrls = false;
    }

    @AfterEach
    void clearStore() {
        ServerMediaService.clearAll();
        configSnapshot.restore();
    }

    @Test
    void onlyOwnerAndExplicitRecipientsCanReadMedia() {
        String mediaId = uploadPng(owner, 1L, (byte) 1);
        assertTrue(ServerMediaService.getForPlayer(owner, mediaId).isPresent());
        assertEquals(ServerMediaService.MediaReadFailure.ACCESS_DENIED,
                ServerMediaService.readForPlayer(recipient, mediaId).failure());
        assertEquals(ServerMediaService.MediaReadFailure.NOT_FOUND,
                ServerMediaService.readForPlayer(recipient, "00000000000000000000000000000000").failure());
        assertFalse(ServerMediaService.getForPlayer(recipient, mediaId).isPresent());
        assertFalse(ServerMediaService.getForPlayer(stranger, mediaId).isPresent());

        ServerMediaService.grantReadAccess(mediaId.toUpperCase(java.util.Locale.ROOT), List.of(recipient));
        assertTrue(ServerMediaService.getForPlayer(recipient, mediaId).isPresent());
        assertTrue(ServerMediaService.getForPlayer(recipient,
                mediaId.toUpperCase(java.util.Locale.ROOT)).isPresent());
        assertFalse(ServerMediaService.getForPlayer(stranger, mediaId).isPresent());
    }

    @Test
    void attachmentIdsAreImmutableAndBoundToOwnerMedia() {
        String firstMedia = uploadPng(owner, 2L, (byte) 2);
        StructuredAttachment first = StructuredAttachment.serverMedia(
                ATTACHMENT_ID, firstMedia, "image", "first.png");
        StoredAttachment stored = ServerAttachmentService.put(owner, first).orElseThrow();
        assertEquals(ATTACHMENT_ID, stored.attachmentId());
        assertEquals(stored, ServerAttachmentService.put(owner, first).orElseThrow());
        assertTrue(ServerAttachmentService.put(stranger, first).isEmpty());

        String secondMedia = uploadPng(owner, 3L, (byte) 3);
        StructuredAttachment collision = StructuredAttachment.serverMedia(
                ATTACHMENT_ID, secondMedia, "image", "second.png");
        assertTrue(ServerAttachmentService.put(owner, collision).isEmpty());
        assertEquals(firstMedia, ServerAttachmentService.get(ATTACHMENT_ID).orElseThrow().mediaId());
    }

    @Test
    void externalAttachmentsRequireExplicitOptInAndHttps() {
        assertTrue(ServerAttachmentService.createExternal(
                owner, "image", "remote", "https://cdn.example.com/image.png").isEmpty());

        ServerMediaServerConfig.get().allowExternalAttachmentUrls = true;
        assertTrue(ServerAttachmentService.createExternal(
                owner, "image", "remote", "http://cdn.example.com/image.png").isEmpty());
        assertTrue(ServerAttachmentService.createExternal(
                owner, "image", "remote", "https://user:secret@cdn.example.com/image.png").isEmpty());
        assertTrue(ServerAttachmentService.createExternal(
                owner, "image", "remote", "https://cdn.example.com/image.png").isPresent());
    }

    @Test
    void rejectsNonCanonicalClientProvidedAttachmentIds() {
        String mediaId = uploadPng(owner, 4L, (byte) 4);
        StructuredAttachment invalid = StructuredAttachment.serverMedia(
                "../not-an-id\n", mediaId, "image", "invalid");
        assertTrue(ServerAttachmentService.put(owner, invalid).isEmpty());
    }

    private static String uploadPng(UUID playerId, long uploadId, byte fill) {
        byte[] body = new byte[1_024];
        java.util.Arrays.fill(body, fill);
        body[0] = (byte) 0x89;
        body[1] = 0x50;
        body[2] = 0x4E;
        body[3] = 0x47;
        body[4] = 0x0D;
        body[5] = 0x0A;
        body[6] = 0x1A;
        body[7] = 0x0A;
        assertTrue(ServerMediaService.beginUpload(
                playerId, uploadId, "image", "image/png", body.length, 1).isEmpty());
        ServerMediaService.UploadCompleted completed = ServerMediaService.acceptUploadChunk(
                playerId, uploadId, 0, body).orElseThrow();
        assertEquals(null, completed.error());
        return completed.mediaId();
    }
}