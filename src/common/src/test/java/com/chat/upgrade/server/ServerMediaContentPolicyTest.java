package com.chat.upgrade.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class ServerMediaContentPolicyTest {
    @Test
    void acceptsMatchingImageSignaturesAndRejectsSubtypeConfusion() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0
        };
        assertTrue(ServerMediaContentPolicy.accepts("image", "image/png", png));
        assertTrue(ServerMediaContentPolicy.accepts(" IMAGE ", "IMAGE/PNG;charset=binary", png));
        assertFalse(ServerMediaContentPolicy.accepts("image", "image/jpeg", png));
        assertFalse(ServerMediaContentPolicy.accepts("audio", "audio/mpeg", png));
    }

    @Test
    void rejectsJpeg2000AndUnknownImageSignatures() {
        byte[] jpeg2000Codestream = new byte[] { (byte) 0xFF, 0x4F, (byte) 0xFF, 0x51, 0, 0, 0, 0 };
        byte[] jpeg2000Container = new byte[] { 0, 0, 0, 12, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, (byte) 0x87, 0x0A };
        assertFalse(ServerMediaContentPolicy.accepts("image", "image/jp2", jpeg2000Codestream));
        assertFalse(ServerMediaContentPolicy.accepts("image", "image/jp2", jpeg2000Container));
        assertFalse(ServerMediaContentPolicy.accepts("image", "image/png", new byte[] { 1, 2, 3, 4 }));
    }

    @Test
    void distinguishesRiffContainers() {
        byte[] webp = riff("WEBP");
        byte[] wav = riff("WAVE");
        assertTrue(ServerMediaContentPolicy.accepts("image", "image/webp", webp));
        assertFalse(ServerMediaContentPolicy.accepts("audio", "audio/wav", webp));
        assertTrue(ServerMediaContentPolicy.accepts("audio", "audio/x-wav", wav));
        assertFalse(ServerMediaContentPolicy.accepts("image", "image/webp", wav));
    }

    @Test
    void acceptsOnlyMatchingAudioAndVideoContainerTypes() {
        byte[] ogg = "OggS0000".getBytes(StandardCharsets.US_ASCII);
        byte[] mp4 = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm' };
        byte[] ebml = new byte[] { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0 };

        assertTrue(ServerMediaContentPolicy.accepts("audio", "application/ogg", ogg));
        assertTrue(ServerMediaContentPolicy.accepts("video", "video/ogg", ogg));
        assertFalse(ServerMediaContentPolicy.accepts("video", "video/mp4", ogg));
        assertTrue(ServerMediaContentPolicy.accepts("video", "video/mp4", mp4));
        assertTrue(ServerMediaContentPolicy.accepts("video", "video/webm", ebml));
        assertFalse(ServerMediaContentPolicy.accepts("audio", "audio/mpeg", mp4));
    }

    private static byte[] riff(String formType) {
        byte[] body = new byte[12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, body, 0, 4);
        System.arraycopy(formType.getBytes(StandardCharsets.US_ASCII), 0, body, 8, 4);
        return body;
    }
}