package com.chat.upgrade.client.media.image;
import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageReader;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Shared multi-frame decode loop for ImageReader-based animations (GIF, WebP, APNG).
 */
public final class AnimatedMultiFrameDecoder {
    static final int MAX_FRAMES = 60;
    static final int MIN_FRAME_DELAY_MS = 20;
    static final int DEFAULT_FRAME_DELAY_MS = 100;
    static final long MAX_DECODE_PIXELS_PER_FRAME = 16L * 1024 * 1024;
    static final long MAX_TOTAL_DECODE_PIXELS = 32L * 1024 * 1024;

    @FunctionalInterface
    public interface FrameDelaySource {
        int delayMs(ImageReader reader, int frameIndex) throws IOException;
    }

    private AnimatedMultiFrameDecoder() {}

    /**
     * Reads up to {@link #MAX_FRAMES} frames; returns empty if fewer than two frames succeed.
     */
    public static Optional<AnimatedDecodeResult> decodeFrames(
            ImageReader reader,
            FrameDelaySource delaySource,
            String formatLabel
    ) {
        ArrayList<NativeImage> frameList = new ArrayList<>();
        try {
            int numImages = reader.getNumImages(true);
            if (numImages < 2) {
                return Optional.empty();
            }
            int limit = Math.min(numImages, MAX_FRAMES);
            ArrayList<Integer> delayList = new ArrayList<>(limit);
            long totalPixels = 0L;
            for (int i = 0; i < limit; i++) {
                BufferedImage bi;
                try {
                    bi = reader.read(i);
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: {} frame {} read failed: {}",
                            formatLabel,
                            i,
                            e.getMessage());
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
                if (pixels <= 0L
                        || pixels > MAX_DECODE_PIXELS_PER_FRAME
                        || pixels > MAX_TOTAL_DECODE_PIXELS - totalPixels) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: {} frame {} exceeds decoded pixel policy, static fallback",
                            formatLabel,
                            i);
                    closeAll(frameList);
                    return Optional.empty();
                }
                totalPixels += pixels;
                NativeImage ni = RasterImageDecoder.fromBufferedImage(bi);
                frameList.add(ni);
                int d;
                try {
                    d = delaySource.delayMs(reader, i);
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: {} delay frame {}: {}",
                            formatLabel,
                            i,
                            e.getMessage());
                    d = DEFAULT_FRAME_DELAY_MS;
                }
                delayList.add(Math.max(MIN_FRAME_DELAY_MS, d));
            }
            if (frameList.size() < 2) {
                closeAll(frameList);
                return Optional.empty();
            }
            int[] delays = delayList.stream().mapToInt(Integer::intValue).toArray();
            return Optional.of(new AnimatedDecodeResult(frameList.toArray(new NativeImage[0]), delays));
        } catch (Exception e) {
            closeAll(frameList);
            ChatUpgrade.LOGGER.debug("ChatUpgrade: {} animated decode failed: {}", formatLabel, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads frames sequentially until read fails/end is reached, without relying on {@link ImageReader#getNumImages(boolean)}.
     * Useful for plugins that can decode animation frames but do not expose an accurate frame count.
     */
    public static Optional<AnimatedDecodeResult> decodeFramesSequential(
            ImageReader reader,
            FrameDelaySource delaySource,
            String formatLabel
    ) {
        ArrayList<NativeImage> frameList = new ArrayList<>();
        ArrayList<Integer> delayList = new ArrayList<>();
        long totalPixels = 0L;
        try {
            for (int i = 0; i < MAX_FRAMES; i++) {
                BufferedImage bi;
                try {
                    bi = reader.read(i);
                } catch (Exception e) {
                    if (i == 0) {
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
                if (pixels <= 0L
                        || pixels > MAX_DECODE_PIXELS_PER_FRAME
                        || pixels > MAX_TOTAL_DECODE_PIXELS - totalPixels) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: {} frame {} exceeds decoded pixel policy, static fallback",
                            formatLabel,
                            i);
                    closeAll(frameList);
                    return Optional.empty();
                }
                totalPixels += pixels;
                NativeImage ni = RasterImageDecoder.fromBufferedImage(bi);
                frameList.add(ni);
                int d;
                try {
                    d = delaySource.delayMs(reader, i);
                } catch (Exception e) {
                    d = DEFAULT_FRAME_DELAY_MS;
                }
                delayList.add(Math.max(MIN_FRAME_DELAY_MS, d));
            }
            if (frameList.size() < 2) {
                closeAll(frameList);
                return Optional.empty();
            }
            int[] delays = delayList.stream().mapToInt(Integer::intValue).toArray();
            return Optional.of(new AnimatedDecodeResult(frameList.toArray(new NativeImage[0]), delays));
        } catch (Exception e) {
            closeAll(frameList);
            ChatUpgrade.LOGGER.debug("ChatUpgrade: {} sequential animated decode failed: {}", formatLabel, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Like {@link #decodeFramesSequential(ImageReader, FrameDelaySource, String)}, but composites each frame onto a
     * persistent canvas before converting to {@link NativeImage}. This is important for formats/readers that yield
     * delta frames with transparency (common for APNG), otherwise later frames may appear blank.
     */
    public static Optional<AnimatedDecodeResult> decodeFramesSequentialComposited(
            ImageReader reader,
            FrameDelaySource delaySource,
            String formatLabel
    ) {
        ArrayList<NativeImage> frameList = new ArrayList<>();
        ArrayList<Integer> delayList = new ArrayList<>();
        long totalPixels = 0L;
        BufferedImage canvas = null;
        Graphics2D g = null;
        try {
            for (int i = 0; i < MAX_FRAMES; i++) {
                BufferedImage bi;
                try {
                    bi = reader.read(i);
                } catch (Exception e) {
                    if (i == 0) {
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
                long sourcePixels = (long) bi.getWidth() * bi.getHeight();
                int outputWidth = canvas == null ? bi.getWidth() : canvas.getWidth();
                int outputHeight = canvas == null ? bi.getHeight() : canvas.getHeight();
                long outputPixels = (long) outputWidth * outputHeight;
                if (sourcePixels <= 0L
                        || sourcePixels > MAX_DECODE_PIXELS_PER_FRAME
                        || outputPixels <= 0L
                        || outputPixels > MAX_DECODE_PIXELS_PER_FRAME
                        || outputPixels > MAX_TOTAL_DECODE_PIXELS - totalPixels) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: {} frame {} exceeds decoded pixel policy, static fallback",
                            formatLabel,
                            i);
                    closeAll(frameList);
                    return Optional.empty();
                }
                totalPixels += outputPixels;

                if (canvas == null) {
                    canvas = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    g = canvas.createGraphics();
                    g.setComposite(AlphaComposite.SrcOver);
                }

                if (g != null) {
                    g.drawImage(bi, 0, 0, null);
                }

                NativeImage ni = RasterImageDecoder.fromBufferedImage(canvas != null ? canvas : bi);
                frameList.add(ni);

                int d;
                try {
                    d = delaySource.delayMs(reader, i);
                } catch (Exception e) {
                    d = DEFAULT_FRAME_DELAY_MS;
                }
                delayList.add(Math.max(MIN_FRAME_DELAY_MS, d));
            }
            if (frameList.size() < 2) {
                closeAll(frameList);
                return Optional.empty();
            }
            int[] delays = delayList.stream().mapToInt(Integer::intValue).toArray();
            return Optional.of(new AnimatedDecodeResult(frameList.toArray(new NativeImage[0]), delays));
        } catch (Exception e) {
            closeAll(frameList);
            ChatUpgrade.LOGGER.debug("ChatUpgrade: {} sequential composite decode failed: {}", formatLabel, e.getMessage());
            return Optional.empty();
        } finally {
            if (g != null) {
                try {
                    g.dispose();
                } catch (Exception ignored) {
                }
            }
        }
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
}
