package com.chat.upgrade.client.media.image;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

final class ImagePayloadPolicyTest {
    @Test
    void acceptsBoundedPngDimensions() {
        assertDoesNotThrow(() -> ImagePayloadPolicy.validate(png(1_024, 1_024)));
        assertDoesNotThrow(() -> ImagePayloadPolicy.validate(png(8_192, 2_048)));
    }

    @Test
    void rejectsInvalidOversizedAndExcessivePixelDimensions() {
        assertThrows(IOException.class, () -> ImagePayloadPolicy.validate(png(0, 100)));
        assertThrows(IOException.class, () -> ImagePayloadPolicy.validate(png(8_193, 1)));
        assertThrows(IOException.class, () -> ImagePayloadPolicy.validate(png(8_192, 2_049)));
        assertThrows(IOException.class, () -> ImagePayloadPolicy.validate(png(Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    void rejectsJpeg2000BeforeAnyImageIoDecoderDispatch() {
        byte[] codestream = new byte[] { (byte) 0xFF, 0x4F, (byte) 0xFF, 0x51, 0, 0, 0, 0 };
        byte[] container = new byte[] { 0, 0, 0, 12, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, (byte) 0x87, 0x0A };
        assertThrows(IOException.class, () -> ImagePayloadPolicy.validate(codestream));
        assertThrows(IOException.class, () -> ImagePayloadPolicy.validate(container));
    }

    @Test
    void acceptsBoundedJpegSofAndRejectsTruncatedMetadata() {
        assertDoesNotThrow(() -> ImagePayloadPolicy.validate(jpeg(640, 480)));
        assertThrows(IOException.class,
                () -> ImagePayloadPolicy.validate(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0 }));
    }

    private static byte[] png(int width, int height) {
        byte[] body = new byte[24];
        body[0] = (byte) 0x89;
        body[1] = 'P';
        body[2] = 'N';
        body[3] = 'G';
        body[4] = 0x0D;
        body[5] = 0x0A;
        body[6] = 0x1A;
        body[7] = 0x0A;
        body[12] = 'I';
        body[13] = 'H';
        body[14] = 'D';
        body[15] = 'R';
        writeIntBigEndian(body, 16, width);
        writeIntBigEndian(body, 20, height);
        return body;
    }

    private static byte[] jpeg(int width, int height) {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xC0,
                0, 7,
                8,
                (byte) (height >>> 8), (byte) height,
                (byte) (width >>> 8), (byte) width,
                1
        };
    }

    private static void writeIntBigEndian(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}