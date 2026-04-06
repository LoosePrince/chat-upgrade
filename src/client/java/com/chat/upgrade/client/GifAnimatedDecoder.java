package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import org.w3c.dom.NodeList;

/**
 * Decodes animated GIF into per-frame {@link NativeImage} and delay metadata (ImageReader path).
 * Single-frame GIF or failures return empty so callers should fall back to {@link RasterImageDecoder}.
 */
public final class GifAnimatedDecoder {
    private GifAnimatedDecoder() {}

    /**
     * Returns a multi-frame result only when there are at least two readable frames; otherwise empty
     * (caller should use {@link RasterImageDecoder#decode(byte[])} for static GIF / other formats).
     */
    public static Optional<AnimatedDecodeResult> tryDecode(byte[] bytes) {
        if (bytes == null || !isGifSignature(bytes)) {
            return Optional.empty();
        }
        ImageReader reader = null;
        ImageInputStream iis = null;
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            reader = readers.next();
            iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            reader.setInput(iis, false, true);
            return AnimatedMultiFrameDecoder.decodeFrames(reader, GifAnimatedDecoder::readFrameDelayMs, "GIF");
        } catch (Exception e) {
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

    private static int readFrameDelayMs(javax.imageio.ImageReader reader, int frameIndex) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(frameIndex);
            if (metadata == null) {
                return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            String treeName = "javax_imageio_gif_image_1.0";
            if (!Arrays.asList(metadata.getMetadataFormatNames()).contains(treeName)) {
                return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(treeName);
            if (root == null) {
                return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            NodeList gceList = root.getElementsByTagName("GraphicControlExtension");
            if (gceList == null || gceList.getLength() == 0) {
                return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            IIOMetadataNode gce = (IIOMetadataNode) gceList.item(0);
            String delayTime = gce.getAttribute("delayTime");
            if (delayTime == null || delayTime.isEmpty()) {
                return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            int delayCentiseconds;
            try {
                delayCentiseconds = Integer.parseInt(delayTime);
            } catch (NumberFormatException e) {
                return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            int delayMs = delayCentiseconds * 10;
            if (delayMs == 0) {
                delayMs = AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
            }
            return Math.max(AnimatedMultiFrameDecoder.MIN_FRAME_DELAY_MS, delayMs);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("ChatUpgrade: GIF delay metadata frame {}: {}", frameIndex, e.getMessage());
            return AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS;
        }
    }
}
