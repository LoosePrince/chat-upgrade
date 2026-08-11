package com.chat.upgrade.client.media.image;

import java.io.IOException;

final class ImagePayloadPolicy {
    private static final int MAX_DIMENSION = 8_192;
    private static final long MAX_PIXELS = 16L * 1024L * 1024L;

    private ImagePayloadPolicy() {
    }

    static void validate(byte[] body) throws IOException {
        Dimensions dimensions = dimensions(body);
        if (dimensions.width() <= 0
                || dimensions.height() <= 0
                || dimensions.width() > MAX_DIMENSION
                || dimensions.height() > MAX_DIMENSION
                || (long) dimensions.width() * dimensions.height() > MAX_PIXELS) {
            throw new IOException("image dimensions exceed the decode policy");
        }
    }

    private static Dimensions dimensions(byte[] body) throws IOException {
        if (isPng(body)) {
            return new Dimensions(readIntBigEndian(body, 16), readIntBigEndian(body, 20));
        }
        if (isGif(body)) {
            return new Dimensions(readUnsignedShortLittleEndian(body, 6), readUnsignedShortLittleEndian(body, 8));
        }
        if (isJpeg(body)) {
            return jpegDimensions(body);
        }
        if (isWebp(body)) {
            return webpDimensions(body);
        }
        throw new IOException("unsupported image format; allowed formats are PNG, JPEG, GIF, and WebP");
    }

    private static Dimensions jpegDimensions(byte[] body) throws IOException {
        int offset = 2;
        while (offset + 3 < body.length) {
            if ((body[offset] & 0xFF) != 0xFF) {
                offset++;
                continue;
            }
            int marker = body[offset + 1] & 0xFF;
            offset += 2;
            while (marker == 0xFF && offset < body.length) {
                marker = body[offset++] & 0xFF;
            }
            if (marker == 0xD8 || marker == 0xD9 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (offset + 1 >= body.length) {
                break;
            }
            int segmentLength = ((body[offset] & 0xFF) << 8) | (body[offset + 1] & 0xFF);
            if (segmentLength < 2 || offset + segmentLength > body.length) {
                break;
            }
            if (isStartOfFrame(marker)) {
                if (segmentLength < 7) {
                    break;
                }
                int height = ((body[offset + 3] & 0xFF) << 8) | (body[offset + 4] & 0xFF);
                int width = ((body[offset + 5] & 0xFF) << 8) | (body[offset + 6] & 0xFF);
                return new Dimensions(width, height);
            }
            offset += segmentLength;
        }
        throw new IOException("JPEG dimensions are unavailable");
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0
                && marker <= 0xCF
                && marker != 0xC4
                && marker != 0xC8
                && marker != 0xCC;
    }

    private static Dimensions webpDimensions(byte[] body) throws IOException {
        if (matches(body, 12, "VP8X") && body.length >= 30) {
            return new Dimensions(1 + readUnsigned24LittleEndian(body, 24), 1 + readUnsigned24LittleEndian(body, 27));
        }
        if (matches(body, 12, "VP8L") && body.length >= 25 && (body[20] & 0xFF) == 0x2F) {
            int b1 = body[21] & 0xFF;
            int b2 = body[22] & 0xFF;
            int b3 = body[23] & 0xFF;
            int b4 = body[24] & 0xFF;
            int width = 1 + (b1 | ((b2 & 0x3F) << 8));
            int height = 1 + ((b2 >>> 6) | (b3 << 2) | ((b4 & 0x0F) << 10));
            return new Dimensions(width, height);
        }
        if (matches(body, 12, "VP8 ")
                && body.length >= 30
                && (body[23] & 0xFF) == 0x9D
                && (body[24] & 0xFF) == 0x01
                && (body[25] & 0xFF) == 0x2A) {
            int width = readUnsignedShortLittleEndian(body, 26) & 0x3FFF;
            int height = readUnsignedShortLittleEndian(body, 28) & 0x3FFF;
            return new Dimensions(width, height);
        }
        throw new IOException("WebP dimensions are unavailable");
    }

    private static boolean isPng(byte[] body) {
        return body.length >= 24
                && (body[0] & 0xFF) == 0x89
                && matches(body, 1, "PNG\r\n\u001a\n")
                && matches(body, 12, "IHDR");
    }

    private static boolean isGif(byte[] body) {
        return body.length >= 10 && (matches(body, 0, "GIF87a") || matches(body, 0, "GIF89a"));
    }

    private static boolean isJpeg(byte[] body) {
        return body.length >= 4 && (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8;
    }

    private static boolean isWebp(byte[] body) {
        return body.length >= 30 && matches(body, 0, "RIFF") && matches(body, 8, "WEBP");
    }

    private static boolean matches(byte[] body, int offset, String value) {
        if (offset < 0 || offset + value.length() > body.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ((body[offset + i] & 0xFF) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int readIntBigEndian(byte[] body, int offset) {
        return ((body[offset] & 0xFF) << 24)
                | ((body[offset + 1] & 0xFF) << 16)
                | ((body[offset + 2] & 0xFF) << 8)
                | (body[offset + 3] & 0xFF);
    }

    private static int readUnsignedShortLittleEndian(byte[] body, int offset) {
        return (body[offset] & 0xFF) | ((body[offset + 1] & 0xFF) << 8);
    }

    private static int readUnsigned24LittleEndian(byte[] body, int offset) {
        return (body[offset] & 0xFF)
                | ((body[offset + 1] & 0xFF) << 8)
                | ((body[offset + 2] & 0xFF) << 16);
    }

    private record Dimensions(int width, int height) {
    }
}