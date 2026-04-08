package com.chat.upgrade.client.media.image;
import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;
import org.apache.commons.imaging.Imaging;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decodes image bytes for chat preview: tries {@link NativeImage} (fast path for PNG), then {@link ImageIO}
 * (JPEG, GIF first frame, BMP, TIFF, WebP when SPI is present). Animated WebP / APNG are handled by
 * {@link WebpAnimatedDecoder} / {@link ApngAnimatedDecoder} before this path.
 */
public final class RasterImageDecoder {
    private RasterImageDecoder() {}

    public static NativeImage decode(InputStream rawStream) throws IOException {
        return decode(rawStream.readAllBytes());
    }

    public static NativeImage decode(byte[] bytes) throws IOException {
        boolean isJpeg = looksLikeJpeg(bytes);
        if (isJpeg) {
            ChatUpgrade.LOGGER.info("ChatUpgrade: JPEG detected, using Commons Imaging path");
            BufferedImage jpeg = decodeJpegWithCommonsImaging(bytes);
            return fromBufferedImage(jpeg);
        }
        try {
            return NativeImage.read(bytes);
        } catch (Throwable t) {
            ChatUpgrade.LOGGER.debug(
                    "ChatUpgrade: NativeImage.read failed, trying ImageIO: type={} msg={}",
                    t.getClass().getName(),
                    t.getMessage());
        }
        BufferedImage bi = decodeWithImageReaders(bytes);
        if (bi == null) {
            throw new IOException("Unsupported or corrupt image data (no ImageReader could decode)");
        }
        return fromBufferedImage(bi);
    }

    private static BufferedImage decodeJpegWithCommonsImaging(byte[] bytes) throws IOException {
        try {
            BufferedImage bi = Imaging.getBufferedImage(bytes);
            if (bi == null) {
                throw new IOException("Commons Imaging returned null for JPEG");
            }
            return bi;
        } catch (Exception e) {
            throw new IOException("Commons Imaging JPEG decode failed: " + e.getMessage(), e);
        }
    }

    private static BufferedImage decodeWithImageReaders(byte[] bytes) throws IOException {
        List<ImageReader> readers = collectReaders(bytes);
        if (readers.isEmpty()) {
            return null;
        }

        IOException lastIo = null;
        Throwable lastNonIo = null;
        for (ImageReader reader : readers) {
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                reader.setInput(iis, true, true);
                BufferedImage bi = reader.read(0);
                if (bi != null) {
                    ChatUpgrade.LOGGER.debug(
                            "ChatUpgrade: ImageIO decode success via reader={}",
                            reader.getClass().getName());
                    return bi;
                }
            } catch (IOException ioe) {
                lastIo = ioe;
                ChatUpgrade.LOGGER.debug(
                        "ChatUpgrade: ImageReader IO failure reader={} msg={}",
                        reader.getClass().getName(),
                        ioe.getMessage());
            } catch (Throwable t) {
                // Includes UnsatisfiedLinkError, which is the observed JPEG classloader collision symptom.
                lastNonIo = t;
                ChatUpgrade.LOGGER.warn(
                        "ChatUpgrade: ImageReader failure reader={} type={} msg={}",
                        reader.getClass().getName(),
                        t.getClass().getName(),
                        t.getMessage());
            } finally {
                reader.dispose();
            }
        }

        if (lastIo != null) {
            throw lastIo;
        }
        if (lastNonIo != null) {
            throw new IOException("ImageReader failure: " + lastNonIo.getClass().getSimpleName(), lastNonIo);
        }
        return null;
    }

    private static List<ImageReader> collectReaders(byte[] bytes) throws IOException {
        ArrayList<ImageReader> preferred = new ArrayList<>();
        ArrayList<ImageReader> fallback = new ArrayList<>();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            while (it.hasNext()) {
                ImageReader reader = it.next();
                String className = reader.getClass().getName();
                if (className.startsWith("com.sun.imageio.")) {
                    fallback.add(reader);
                } else {
                    preferred.add(reader);
                }
            }
        }
        preferred.addAll(fallback);
        return preferred;
    }

    private static boolean looksLikeJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    /**
     * Converts an AWT {@link BufferedImage} to RGBA {@link NativeImage} (used for GIF frames and ImageIO paths).
     */
    public static NativeImage fromBufferedImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argbPixel = argb.getRGB(x, y);
                int a = (argbPixel >> 24) & 0xFF;
                int r = (argbPixel >> 16) & 0xFF;
                int gCh = (argbPixel >> 8) & 0xFF;
                int b = argbPixel & 0xFF;
                int abgr = (a << 24) | (b << 16) | (gCh << 8) | r;
                ni.setPixelABGR(x, y, abgr);
            }
        }
        return ni;
    }
}
