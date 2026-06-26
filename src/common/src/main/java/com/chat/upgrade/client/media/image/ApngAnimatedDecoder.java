package com.chat.upgrade.client.media.image;
import com.chat.upgrade.ChatUpgrade;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

/**
 * Animated PNG (APNG) via ImageIO plugin; frame delays from fcTL chunks.
 */
public final class ApngAnimatedDecoder {
    private ApngAnimatedDecoder() {}

    public static Optional<AnimatedDecodeResult> tryDecode(byte[] bytes) {
        if (bytes == null || !PngChunkParser.isPngSignature(bytes)) {
            return Optional.empty();
        }
        if (!PngChunkParser.hasAcTL(bytes)) {
            return Optional.empty();
        }
        int[] fcTLDelays = PngChunkParser.parseFcTLDelaysMs(bytes);
        ImageInputStream iis = null;
        try {
            iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            Iterator<ImageReader> readers = candidateReaders(iis);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    iis.seek(0);
                    reader.setInput(iis, false, false);
                    int[] delays = alignFcTLDelays(fcTLDelays, AnimatedMultiFrameDecoder.MAX_FRAMES);
                    Optional<AnimatedDecodeResult> decoded = AnimatedMultiFrameDecoder.decodeFramesSequentialComposited(
                            reader,
                            (r, i) -> delays[Math.min(i, delays.length - 1)],
                            "APNG");
                    if (decoded.isPresent()) {
                        return decoded;
                    }
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("ChatUpgrade: APNG reader attempt failed: {}", e.getMessage());
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("ChatUpgrade: APNG animated decode failed: {}", e.getMessage());
        } finally {
            if (iis != null) {
                try {
                    iis.close();
                } catch (IOException ignored) {
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Prefer explicit APNG readers first, then fallback to generic stream-matching readers.
     */
    private static Iterator<ImageReader> candidateReaders(ImageInputStream iis) {
        Iterator<ImageReader> byApng = ImageIO.getImageReadersByFormatName("apng");
        if (byApng.hasNext()) {
            return byApng;
        }
        return ImageIO.getImageReaders(iis);
    }

    /**
     * Aligns fcTL delays with reader frame count: first frame may have no fcTL (prepend default), or one fcTL per frame.
     */
    private static int[] alignFcTLDelays(int[] fromFcTL, int numImages) {
        int limit = Math.min(numImages, AnimatedMultiFrameDecoder.MAX_FRAMES);
        int[] out = new int[limit];
        if (fromFcTL.length == 0) {
            Arrays.fill(out, AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS);
        } else if (fromFcTL.length == limit) {
            System.arraycopy(fromFcTL, 0, out, 0, limit);
        } else if (fromFcTL.length == limit - 1) {
            out[0] = AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            System.arraycopy(fromFcTL, 0, out, 1, limit - 1);
        } else {
            for (int i = 0; i < limit; i++) {
                out[i] = i < fromFcTL.length
                        ? fromFcTL[i]
                        : AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] <= 0) {
                out[i] = AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            out[i] = Math.max(AnimatedMultiFrameDecoder.MIN_FRAME_DELAY_MS, out[i]);
        }
        return out;
    }

    private static final class PngChunkParser {
        static boolean isPngSignature(byte[] b) {
            return b.length >= 8
                    && (b[0] & 0xFF) == 0x89
                    && b[1] == 'P'
                    && b[2] == 'N'
                    && b[3] == 'G'
                    && b[4] == '\r'
                    && b[5] == '\n'
                    && b[6] == 0x1A
                    && b[7] == '\n';
        }

        /** acTL must appear before the first IDAT (APNG spec). */
        static boolean hasAcTL(byte[] b) {
            int p = 8;
            while (p + 12 <= b.length) {
                int chunkLen = readBE32(b, p);
                String type = fourCC(b, p + 4);
                if ("IDAT".equals(type)) {
                    break;
                }
                if ("acTL".equals(type)) {
                    return true;
                }
                p += 4 + 4 + chunkLen + 4;
            }
            return false;
        }

        static int[] parseFcTLDelaysMs(byte[] b) {
            int[] tmp = new int[AnimatedMultiFrameDecoder.MAX_FRAMES + 1];
            int count = 0;
            int p = 8;
            while (p + 12 <= b.length) {
                int chunkLen = readBE32(b, p);
                String type = fourCC(b, p + 4);
                int dataStart = p + 8;
                int nextChunk = dataStart + chunkLen + 4; // +CRC
                if (chunkLen < 0 || nextChunk > b.length) {
                    break;
                }
                if ("fcTL".equals(type) && chunkLen >= 26) {
                    int delayNum = readBE16(b, dataStart + 20);
                    int delayDen = readBE16(b, dataStart + 22);
                    int ms;
                    if (delayDen == 0) {
                        ms = AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
                    } else {
                        ms = (int) Math.min(600_000L, Math.max(1L, (delayNum * 1000L) / delayDen));
                    }
                    ms = Math.max(AnimatedMultiFrameDecoder.MIN_FRAME_DELAY_MS, ms);
                    if (count < tmp.length) {
                        tmp[count++] = ms;
                    }
                }
                p = nextChunk;
            }
            return count == 0 ? new int[0] : Arrays.copyOf(tmp, count);
        }

        private static int readBE32(byte[] b, int off) {
            return ((b[off] & 0xFF) << 24)
                    | ((b[off + 1] & 0xFF) << 16)
                    | ((b[off + 2] & 0xFF) << 8)
                    | (b[off + 3] & 0xFF);
        }

        private static int readBE16(byte[] b, int off) {
            return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
        }

        private static String fourCC(byte[] b, int off) {
            return new String(b, off, 4, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
}
