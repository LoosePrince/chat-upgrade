package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import org.w3c.dom.NodeList;

/**
 * Decodes animated GIF into per-frame {@link NativeImage} and delay metadata (ImageReader path).
 * Single-frame GIF or failures return empty so callers can fall back to {@link RasterImageDecoder}.
 */
public final class GifAnimatedDecoder {
    /** Align with {@link ImageLoader} texture budget intent: avoid pathological frame counts. */
    private static final int MAX_FRAMES = 150;
    private static final int MIN_FRAME_DELAY_MS = 20;
    private static final int DEFAULT_FRAME_DELAY_MS = 100;
    /** Per-frame pixel cap before degrading to static decode elsewhere. */
    private static final long MAX_DECODE_PIXELS_PER_FRAME = 16L * 1024 * 1024;

    public record Result(NativeImage[] frames, int[] delayMs) {}

    private GifAnimatedDecoder() {}

    /**
     * Returns a multi-frame result only when there are at least two readable frames; otherwise empty
     * (caller should use {@link RasterImageDecoder#decode(byte[])} for static GIF / other formats).
     */
    public static Optional<Result> tryDecode(byte[] bytes) {
        if (bytes == null || !isGifSignature(bytes)) {
            return Optional.empty();
        }
        ImageReader reader = null;
        ImageInputStream iis = null;
        ArrayList<NativeImage> frameList = new ArrayList<>();
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            reader = readers.next();
            iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            reader.setInput(iis, false, true);
            int numImages = reader.getNumImages(true);
            if (numImages < 2) {
                return Optional.empty();
            }
            int limit = Math.min(numImages, MAX_FRAMES);
            ArrayList<Integer> delayList = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                BufferedImage bi;
                try {
                    bi = reader.read(i);
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("ChatUpgrade: GIF frame {} read failed: {}", i, e.getMessage());
                    if (i == 0) {
                        closeAll(frameList);
                        return Optional.empty();
                    }
                    break;
                }
                if (bi == null) {
                    if (i == 0) {
                        return Optional.empty();
                    }
                    break;
                }
                long pixels = (long) bi.getWidth() * bi.getHeight();
                if (pixels > MAX_DECODE_PIXELS_PER_FRAME) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: GIF frame {} exceeds pixel cap ({}), static fallback",
                            i,
                            MAX_DECODE_PIXELS_PER_FRAME);
                    closeAll(frameList);
                    return Optional.empty();
                }
                NativeImage ni = RasterImageDecoder.fromBufferedImage(bi);
                frameList.add(ni);
                delayList.add(readFrameDelayMs(reader, i));
            }
            if (frameList.size() < 2) {
                closeAll(frameList);
                return Optional.empty();
            }
            int[] delays = delayList.stream().mapToInt(Integer::intValue).toArray();
            return Optional.of(new Result(frameList.toArray(new NativeImage[0]), delays));
        } catch (Exception e) {
            closeAll(frameList);
            ChatUpgrade.LOGGER.debug("ChatUpgrade: GIF animated decode failed: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            if (iis != null) {
                try {
                    iis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean isGifSignature(byte[] b) {
        return b.length >= 6
                && b[0] == 'G'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == '8'
                && (b[4] == '7' || b[4] == '9')
                && b[5] == 'a';
    }

    private static void closeAll(ArrayList<NativeImage> frames) {
        for (NativeImage ni : frames) {
            try {
                ni.close();
            } catch (Exception ignored) {
            }
        }
        frames.clear();
    }

    private static int readFrameDelayMs(ImageReader reader, int frameIndex) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(frameIndex);
            if (metadata == null) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            String treeName = "javax_imageio_gif_image_1.0";
            if (!Arrays.asList(metadata.getMetadataFormatNames()).contains(treeName)) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(treeName);
            if (root == null) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            NodeList gceList = root.getElementsByTagName("GraphicControlExtension");
            if (gceList == null || gceList.getLength() == 0) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            IIOMetadataNode gce = (IIOMetadataNode) gceList.item(0);
            String delayTime = gce.getAttribute("delayTime");
            if (delayTime == null || delayTime.isEmpty()) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            int delayCentiseconds;
            try {
                delayCentiseconds = Integer.parseInt(delayTime);
            } catch (NumberFormatException e) {
                return DEFAULT_FRAME_DELAY_MS;
            }
            int delayMs = delayCentiseconds * 10;
            if (delayMs == 0) {
                delayMs = DEFAULT_FRAME_DELAY_MS;
            }
            return Math.max(MIN_FRAME_DELAY_MS, delayMs);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("ChatUpgrade: GIF delay metadata frame {}: {}", frameIndex, e.getMessage());
            return DEFAULT_FRAME_DELAY_MS;
        }
    }
}
