package com.chat.upgrade.client.media.image;
import com.chat.upgrade.ChatUpgrade;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;

/**
 * Animated WebP via TwelveMonkeys ImageIO; frame delays from ANMF chunk durations (ms).
 */
public final class WebpAnimatedDecoder {
    private WebpAnimatedDecoder() {}

    public static Optional<AnimatedDecodeResult> tryDecode(byte[] bytes) {
        if (bytes == null || !isRiffWebp(bytes)) {
            return Optional.empty();
        }
        int[] anmfDurations = WebpAnmfParser.parseFrameDurationsMs(bytes);
        if (anmfDurations.length < 2) {
            return Optional.empty();
        }
        ImageInputStream iis = null;
        try {
            iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            Iterator<ImageReader> readers = candidateReaders(iis);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    iis.seek(0);
                    reader.setInput(iis, false, false);
                    int[] delays = alignDelays(anmfDurations, AnimatedMultiFrameDecoder.MAX_FRAMES);
                    Optional<AnimatedDecodeResult> decoded = AnimatedMultiFrameDecoder.decodeFramesSequentialComposited(
                            reader,
                            (r, i) -> delays[Math.min(i, delays.length - 1)],
                            "WebP");
                    if (decoded.isPresent()) {
                        return decoded;
                    }
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("ChatUpgrade: WebP reader attempt failed: {}", e.getMessage());
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("ChatUpgrade: WebP animated decode failed: {}", e.getMessage());
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
     * Prefer explicit WebP readers first, then fallback to generic stream-matching readers.
     */
    private static Iterator<ImageReader> candidateReaders(ImageInputStream iis) {
        Iterator<ImageReader> byWebp = ImageIO.getImageReadersByFormatName("webp");
        if (byWebp.hasNext()) {
            return byWebp;
        }
        return ImageIO.getImageReaders(iis);
    }

    private static boolean isRiffWebp(byte[] b) {
        return b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P';
    }

    /**
     * Aligns ANMF-derived delays with ImageReader frame count (pads or truncates with defaults).
     */
    private static int[] alignDelays(int[] parsed, int maxFrames) {
        int limit = Math.min(maxFrames, AnimatedMultiFrameDecoder.MAX_FRAMES);
        int[] out = new int[limit];
        for (int i = 0; i < limit; i++) {
            long d = i < parsed.length ? parsed[i] : AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            if (d <= 0) {
                d = AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            out[i] = (int) Math.max(AnimatedMultiFrameDecoder.MIN_FRAME_DELAY_MS, d);
        }
        return out;
    }

    /** Walks RIFF WEBP chunks and collects ANMF frame durations (24-bit LE ms in ANMF header). */
    private static final class WebpAnmfParser {
        static int[] parseFrameDurationsMs(byte[] b) {
            ArrayList<Integer> ms = new ArrayList<>();
            int p = 12;
            while (p + 8 <= b.length) {
                String id = fourCC(b, p);
                int chunkSize = readLE32(b, p + 4);
                if (chunkSize < 0) {
                    break;
                }
                p += 8;
                if (p + chunkSize > b.length) {
                    break;
                }
                if ("ANMF".equals(id) && chunkSize >= 16) {
                    int dur = readLE24(b, p + 12);
                    int d = dur == 0 ? AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS
                            : Math.max(AnimatedMultiFrameDecoder.MIN_FRAME_DELAY_MS, dur);
                    ms.add(d);
                }
                p += chunkSize + (chunkSize & 1);
            }
            return ms.stream().mapToInt(Integer::intValue).toArray();
        }

        private static int readLE32(byte[] b, int off) {
            return (b[off] & 0xFF)
                    | ((b[off + 1] & 0xFF) << 8)
                    | ((b[off + 2] & 0xFF) << 16)
                    | ((b[off + 3] & 0xFF) << 24);
        }

        private static int readLE24(byte[] b, int off) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16);
        }

        private static String fourCC(byte[] b, int off) {
            return new String(b, off, 4, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
}
