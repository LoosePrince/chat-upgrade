package com.chat.upgrade.client.media.image;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NodeList;

/**
 * Decodes animated GIFs into complete logical-screen frames and delay metadata.
 * GIF image blocks are often cropped deltas, so frame offsets and disposal rules
 * must be applied before the images are uploaded as independent GPU textures.
 */
public final class GifAnimatedDecoder {
    private static final String IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String STREAM_METADATA_FORMAT = "javax_imageio_gif_stream_1.0";

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
            return decodeCompositedFrames(reader);
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

    private static Optional<AnimatedDecodeResult> decodeCompositedFrames(ImageReader reader) {
        ArrayList<NativeImage> frames = new ArrayList<>();
        try {
            int frameCount = reader.getNumImages(true);
            if (frameCount < 2) {
                return Optional.empty();
            }

            int limit = Math.min(frameCount, AnimatedMultiFrameDecoder.MAX_FRAMES);
            GifStreamInfo stream = readStreamInfo(reader);
            long canvasPixels = (long) stream.width() * stream.height();
            if (canvasPixels > AnimatedMultiFrameDecoder.MAX_DECODE_PIXELS_PER_FRAME) {
                ChatUpgrade.LOGGER.debug(
                        "ChatUpgrade: GIF logical screen exceeds pixel cap ({}), static fallback",
                        AnimatedMultiFrameDecoder.MAX_DECODE_PIXELS_PER_FRAME);
                return Optional.empty();
            }

            GifFrameInfo firstFrame = readFrameInfo(reader, 0);
            int canvasBackgroundArgb = firstFrame.transparent() ? 0 : stream.backgroundArgb();
            BufferedImage canvas = new BufferedImage(stream.width(), stream.height(), BufferedImage.TYPE_INT_ARGB);
            clear(canvas, 0, 0, stream.width(), stream.height(), canvasBackgroundArgb);

            ArrayList<Integer> delays = new ArrayList<>(limit);
            @Nullable GifFrameInfo previousFrame = null;
            @Nullable BufferedImage restoreToPrevious = null;

            for (int i = 0; i < limit; i++) {
                if (previousFrame != null) {
                    applyDisposal(canvas, previousFrame, restoreToPrevious, stream.backgroundArgb());
                }

                GifFrameInfo frameInfo = i == 0 ? firstFrame : readFrameInfo(reader, i);
                BufferedImage frame;
                try {
                    frame = reader.read(i);
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("ChatUpgrade: GIF frame {} read failed: {}", i, e.getMessage());
                    if (i == 0) {
                        closeAll(frames);
                        return Optional.empty();
                    }
                    break;
                }
                if (frame == null) {
                    break;
                }

                long framePixels = (long) frame.getWidth() * frame.getHeight();
                if (framePixels > AnimatedMultiFrameDecoder.MAX_DECODE_PIXELS_PER_FRAME) {
                    closeAll(frames);
                    return Optional.empty();
                }

                BufferedImage currentRestore = frameInfo.disposal() == Disposal.RESTORE_TO_PREVIOUS
                        ? copy(canvas)
                        : null;
                drawFrame(canvas, frame, frameInfo);
                frames.add(RasterImageDecoder.fromBufferedImage(canvas));
                delays.add(frameInfo.delayMs());
                previousFrame = frameInfo;
                restoreToPrevious = currentRestore;
            }

            if (frames.size() < 2) {
                closeAll(frames);
                return Optional.empty();
            }
            return Optional.of(new AnimatedDecodeResult(
                    frames.toArray(new NativeImage[0]),
                    delays.stream().mapToInt(Integer::intValue).toArray()));
        } catch (Exception e) {
            closeAll(frames);
            ChatUpgrade.LOGGER.debug("ChatUpgrade: GIF composite decode failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void applyDisposal(
            BufferedImage canvas,
            GifFrameInfo previous,
            @Nullable BufferedImage restoreToPrevious,
            int backgroundArgb) {
        switch (previous.disposal()) {
            case KEEP -> {
            }
            case RESTORE_TO_BACKGROUND -> clear(
                    canvas,
                    previous.left(),
                    previous.top(),
                    previous.width(),
                    previous.height(),
                    previous.transparent() ? 0 : backgroundArgb);
            case RESTORE_TO_PREVIOUS -> {
                if (restoreToPrevious != null) {
                    Graphics2D graphics = canvas.createGraphics();
                    try {
                        graphics.setComposite(AlphaComposite.Src);
                        graphics.drawImage(restoreToPrevious, 0, 0, null);
                    } finally {
                        graphics.dispose();
                    }
                }
            }
        }
    }

    private static void drawFrame(BufferedImage canvas, BufferedImage frame, GifFrameInfo info) {
        boolean readerReturnedLogicalScreen = frame.getWidth() == canvas.getWidth()
                && frame.getHeight() == canvas.getHeight()
                && (info.width() != canvas.getWidth() || info.height() != canvas.getHeight());
        int drawX = readerReturnedLogicalScreen ? 0 : info.left();
        int drawY = readerReturnedLogicalScreen ? 0 : info.top();
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(frame, drawX, drawY, null);
        } finally {
            graphics.dispose();
        }
    }

    private static void clear(BufferedImage image, int x, int y, int width, int height, int argb) {
        int left = Math.clamp(x, 0, image.getWidth());
        int top = Math.clamp(y, 0, image.getHeight());
        long requestedRight = (long) x + Math.max(0, width);
        long requestedBottom = (long) y + Math.max(0, height);
        int right = (int) Math.clamp(requestedRight, (long) left, image.getWidth());
        int bottom = (int) Math.clamp(requestedBottom, (long) top, image.getHeight());
        if (right <= left || bottom <= top) {
            return;
        }
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(argb, true));
            graphics.fillRect(left, top, right - left, bottom - top);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static GifStreamInfo readStreamInfo(ImageReader reader) throws IOException {
        int fallbackWidth = Math.max(1, reader.getWidth(0));
        int fallbackHeight = Math.max(1, reader.getHeight(0));
        IIOMetadata metadata = reader.getStreamMetadata();
        if (metadata == null || !hasFormat(metadata, STREAM_METADATA_FORMAT)) {
            return new GifStreamInfo(fallbackWidth, fallbackHeight, 0);
        }
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(STREAM_METADATA_FORMAT);
        IIOMetadataNode descriptor = firstNode(root, "LogicalScreenDescriptor");
        int width = intAttribute(descriptor, "logicalScreenWidth", fallbackWidth);
        int height = intAttribute(descriptor, "logicalScreenHeight", fallbackHeight);
        int backgroundArgb = readBackgroundArgb(root);
        return new GifStreamInfo(Math.max(1, width), Math.max(1, height), backgroundArgb);
    }

    private static int readBackgroundArgb(IIOMetadataNode root) {
        IIOMetadataNode table = firstNode(root, "GlobalColorTable");
        if (table == null) {
            return 0;
        }
        int backgroundIndex = intAttribute(table, "backgroundColorIndex", -1);
        NodeList entries = table.getElementsByTagName("ColorTableEntry");
        for (int i = 0; i < entries.getLength(); i++) {
            if (!(entries.item(i) instanceof IIOMetadataNode entry)
                    || intAttribute(entry, "index", -2) != backgroundIndex) {
                continue;
            }
            int red = intAttribute(entry, "red", 0);
            int green = intAttribute(entry, "green", 0);
            int blue = intAttribute(entry, "blue", 0);
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
        return 0;
    }

    private static GifFrameInfo readFrameInfo(ImageReader reader, int frameIndex) throws IOException {
        int fallbackWidth = Math.max(1, reader.getWidth(frameIndex));
        int fallbackHeight = Math.max(1, reader.getHeight(frameIndex));
        IIOMetadata metadata = reader.getImageMetadata(frameIndex);
        if (metadata == null || !hasFormat(metadata, IMAGE_METADATA_FORMAT)) {
            return new GifFrameInfo(
                    0,
                    0,
                    fallbackWidth,
                    fallbackHeight,
                    Disposal.KEEP,
                    false,
                    AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS);
        }

        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(IMAGE_METADATA_FORMAT);
        IIOMetadataNode descriptor = firstNode(root, "ImageDescriptor");
        IIOMetadataNode control = firstNode(root, "GraphicControlExtension");
        int delayCentiseconds = intAttribute(control, "delayTime", 0);
        int delayMs = delayCentiseconds <= 0
                ? AnimatedMultiFrameDecoder.DEFAULT_FRAME_DELAY_MS
                : delayCentiseconds * 10;
        return new GifFrameInfo(
                intAttribute(descriptor, "imageLeftPosition", 0),
                intAttribute(descriptor, "imageTopPosition", 0),
                Math.max(1, intAttribute(descriptor, "imageWidth", fallbackWidth)),
                Math.max(1, intAttribute(descriptor, "imageHeight", fallbackHeight)),
                Disposal.fromMetadata(attribute(control, "disposalMethod")),
                "TRUE".equalsIgnoreCase(attribute(control, "transparentColorFlag")),
                Math.max(AnimatedMultiFrameDecoder.MIN_FRAME_DELAY_MS, delayMs));
    }

    private static boolean hasFormat(IIOMetadata metadata, String format) {
        return Arrays.asList(metadata.getMetadataFormatNames()).contains(format);
    }

    private static @Nullable IIOMetadataNode firstNode(IIOMetadataNode root, String name) {
        NodeList nodes = root.getElementsByTagName(name);
        return nodes.getLength() > 0 && nodes.item(0) instanceof IIOMetadataNode node ? node : null;
    }

    private static String attribute(@Nullable IIOMetadataNode node, String name) {
        return node == null ? "" : node.getAttribute(name);
    }

    private static int intAttribute(@Nullable IIOMetadataNode node, String name, int fallback) {
        String value = attribute(node, name);
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void closeAll(ArrayList<NativeImage> frames) {
        for (NativeImage frame : frames) {
            try {
                frame.close();
            } catch (Exception ignored) {
            }
        }
        frames.clear();
    }

    private static boolean isGifSignature(byte[] bytes) {
        return bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a';
    }

    private enum Disposal {
        KEEP,
        RESTORE_TO_BACKGROUND,
        RESTORE_TO_PREVIOUS;

        static Disposal fromMetadata(String value) {
            return switch (value) {
                case "restoreToBackgroundColor" -> RESTORE_TO_BACKGROUND;
                case "restoreToPrevious" -> RESTORE_TO_PREVIOUS;
                default -> KEEP;
            };
        }
    }

    private record GifStreamInfo(int width, int height, int backgroundArgb) {
    }

    private record GifFrameInfo(
            int left,
            int top,
            int width,
            int height,
            Disposal disposal,
            boolean transparent,
            int delayMs) {
    }
}
