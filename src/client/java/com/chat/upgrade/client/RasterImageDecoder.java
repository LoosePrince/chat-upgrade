package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
        try {
            return NativeImage.read(bytes);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("ChatUpgrade: NativeImage.read failed, trying ImageIO: {}", e.getMessage());
        }
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
        if (bi == null) {
            throw new IOException("Unsupported or corrupt image data");
        }
        return fromBufferedImage(bi);
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
