package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Resolves local images: folder scan, Swing {@link JFileChooser}, AWT clipboard (requires non-headless JVM).
 */
public final class LocalImageSources {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "apng", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "jfif", "ico");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "ogg", "wav", "mp3", "flac", "m4a", "aac", "opus", "webm");

    private LocalImageSources() {}

    public static Optional<Path> resolveFolderOrFile(Path path) {
        return resolveFolderOrFileWithExtensions(path, IMAGE_EXTENSIONS);
    }

    public static Optional<Path> resolveAudioFolderOrFile(Path path) {
        return resolveFolderOrFileWithExtensions(path, AUDIO_EXTENSIONS);
    }

    private static Optional<Path> resolveFolderOrFileWithExtensions(Path path, Set<String> extensions) {
        try {
            if (Files.isRegularFile(path)) {
                return isExtension(path, extensions) ? Optional.of(path) : Optional.empty();
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .filter(p -> isExtension(p, extensions))
                            .max(Comparator.comparingLong(LocalImageSources::lastModifiedSafe));
                }
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: path resolve failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static long lastModifiedSafe(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean isExtension(Path p, Set<String> extensions) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extensions.contains(ext);
    }

    /**
     * Cross-platform {@link JFileChooser}; requires {@code java.awt.headless=false} and a working AWT/Swing display.
     */
    public static Optional<Path> pickImageWithFileChooser() {
        if (GraphicsEnvironment.isHeadless()) {
            ChatUpgrade.LOGGER.warn(
                    "ChatUpgrade: AWT headless，无法打开文件选择器。请使用非 headless 启动（例如 Gradle Loom 的 client 运行配置中加 vmArg -Djava.awt.headless=false；Linux 需 DISPLAY）。");
            return Optional.empty();
        }
        try {
            Toolkit.getDefaultToolkit();
        } catch (Throwable t) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: AWT toolkit unavailable: {}", t.getMessage());
            return Optional.empty();
        }
        return pickWithJFileChooser();
    }

    public static Optional<Path> pickAudioWithFileChooser() {
        if (GraphicsEnvironment.isHeadless()) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: AWT headless，无法打开文件选择器。");
            return Optional.empty();
        }
        try {
            Toolkit.getDefaultToolkit();
        } catch (Throwable t) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: AWT toolkit unavailable: {}", t.getMessage());
            return Optional.empty();
        }
        return pickAudioWithJFileChooser();
    }

    private static Optional<Path> pickWithJFileChooser() {
        final Path[] holder = new Path[1];
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("选择要上传的图片");
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "图片",
                            "png", "apng", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "jfif", "ico"));
                chooser.setMultiSelectionEnabled(false);
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    holder[0] = chooser.getSelectedFile().toPath();
                }
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("ChatUpgrade: JFileChooser error: {}", e.getMessage());
            } finally {
                done.countDown();
            }
        });
        awaitLatch(done);
        return Optional.ofNullable(holder[0]);
    }

    private static Optional<Path> pickAudioWithJFileChooser() {
        final Path[] holder = new Path[1];
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("选择要上传的音频");
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "音频",
                        "ogg", "wav", "mp3", "flac", "m4a", "aac", "opus", "webm"));
                chooser.setMultiSelectionEnabled(false);
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    holder[0] = chooser.getSelectedFile().toPath();
                }
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("ChatUpgrade: JFileChooser audio error: {}", e.getMessage());
            } finally {
                done.countDown();
            }
        });
        awaitLatch(done);
        return Optional.ofNullable(holder[0]);
    }

    private static void awaitLatch(CountDownLatch done) {
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reads image data from the clipboard and returns PNG bytes for upload (AWT; same headless requirements as file chooser).
     */
    public static Optional<byte[]> readClipboardImagePngBytes() {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        final AtomicReference<Optional<byte[]>> ref = new AtomicReference<>(Optional.empty());
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                ref.set(readClipboardOnEdt());
            } else {
                SwingUtilities.invokeAndWait(() -> ref.set(readClipboardOnEdt()));
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: clipboard read failed: {}", e.getMessage());
        }
        return ref.get();
    }

    private static Optional<byte[]> readClipboardOnEdt() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = clipboard.getContents(null);
            if (t == null) {
                return Optional.empty();
            }
            for (DataFlavor flavor : t.getTransferDataFlavors()) {
                if (flavor == null || flavor.isFlavorTextType()) {
                    continue;
                }
                try {
                    if (DataFlavor.imageFlavor.equals(flavor) || Image.class.isAssignableFrom(flavor.getRepresentationClass())) {
                        Object data = t.getTransferData(flavor);
                        Optional<byte[]> png = bufferedImageToPng(toBufferedImage(data));
                        if (png.isPresent()) {
                            return png;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            for (DataFlavor flavor : t.getTransferDataFlavors()) {
                if (flavor == null) {
                    continue;
                }
                String mime = flavor.getMimeType();
                if (mime != null && mime.startsWith("image/")) {
                    try {
                        Object data = t.getTransferData(flavor);
                        if (data instanceof byte[] bytes) {
                            if (looksLikePng(bytes)) {
                                return Optional.of(bytes);
                            }
                            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
                            if (bi != null) {
                                return bufferedImageToPng(bi);
                            }
                        }
                        if (data instanceof InputStream is) {
                            byte[] bytes = is.readAllBytes();
                            if (looksLikePng(bytes)) {
                                return Optional.of(bytes);
                            }
                            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
                            if (bi != null) {
                                return bufferedImageToPng(bi);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: clipboard: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static boolean looksLikePng(byte[] bytes) {
        return bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G';
    }

    private static Optional<byte[]> bufferedImageToPng(BufferedImage buffered) {
        if (buffered == null) {
            return Optional.empty();
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(buffered, "png", baos);
            return Optional.of(baos.toByteArray());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static BufferedImage toBufferedImage(Object data) {
        if (data instanceof BufferedImage bi) {
            return bi;
        }
        if (data instanceof Image img) {
            waitForImage(img);
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            if (w <= 0 || h <= 0) {
                return null;
            }
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            try {
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
            return bi;
        }
        return null;
    }

    private static void waitForImage(Image img) {
        Toolkit.getDefaultToolkit().prepareImage(img, -1, -1, null);
        // Do not Thread.sleep here: clipboard path runs on the AWT EDT.
    }
}
