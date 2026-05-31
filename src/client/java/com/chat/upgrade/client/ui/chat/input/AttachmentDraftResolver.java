package com.chat.upgrade.client.ui.chat.input;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.upload.LocalImageSources;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class AttachmentDraftResolver {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "apng", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "jfif", "ico");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "ogg", "wav", "mp3", "flac", "m4a", "aac", "opus", "webm");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "mov", "mkv", "m4v", "avi");

    private AttachmentDraftResolver() {
    }

    public record ResolveResult(Optional<AttachmentDraft> draft, Optional<Component> message, boolean consumesInput) {
        public static ResolveResult draft(AttachmentDraft draft) {
            return new ResolveResult(Optional.of(draft), Optional.empty(), true);
        }

        public static ResolveResult message(Component message) {
            return new ResolveResult(Optional.empty(), Optional.of(message), false);
        }

        public static ResolveResult consumedMessage(Component message) {
            return new ResolveResult(Optional.empty(), Optional.of(message), true);
        }

        public static ResolveResult passThrough() {
            return new ResolveResult(Optional.empty(), Optional.empty(), false);
        }
    }

    public static ResolveResult pickFile(InlineResourceType type) {
        Optional<Path> picked = switch (type) {
            case IMAGE -> LocalImageSources.pickImageWithFileChooser();
            case AUDIO -> LocalImageSources.pickAudioWithFileChooser();
            case VIDEO -> LocalImageSources.pickVideoWithFileChooser();
        };
        if (picked.isEmpty()) {
            return ResolveResult.message(Component.translatable("chatupgrade.upload.no_file_picked").withStyle(ChatFormatting.GRAY));
        }
        return fromFile(picked.get(), AttachmentDraft.Source.FILE_PICKER, Optional.of(type));
    }

    public static ResolveResult fromClipboard() {
        Optional<Path> copiedFile = readClipboardFile();
        if (copiedFile.isPresent()) {
            ResolveResult fileResult = fromFile(copiedFile.get(), AttachmentDraft.Source.CLIPBOARD, Optional.empty());
            if (fileResult.draft().isPresent()) {
                return fileResult;
            }
            return fileResult.message()
                    .map(ResolveResult::consumedMessage)
                    .orElseGet(ResolveResult::passThrough);
        }
        if (!hasClipboardImage()) {
            return ResolveResult.passThrough();
        }
        Optional<byte[]> pngBytes = LocalImageSources.readClipboardImagePngBytes();
        if (pngBytes.isEmpty()) {
            return ResolveResult.consumedMessage(Component.translatable("chatupgrade.input.error.clipboard_empty")
                    .withStyle(ChatFormatting.GRAY));
        }
        byte[] bytes = pngBytes.get();
        if (bytes.length > ChatUpgradeConfig.get().maxUploadBytes) {
            return ResolveResult.message(tooLargeMessage(bytes.length));
        }
        return ResolveResult.draft(AttachmentDraft.fromBytes(
                InlineResourceType.IMAGE,
                bytes,
                "clipboard.png",
                Component.translatable("chatupgrade.upload.default_name.paste").getString(),
                AttachmentDraft.Source.CLIPBOARD,
                "image/png"));
    }

    public static ResolveResult fromFile(Path file, AttachmentDraft.Source source, Optional<InlineResourceType> forcedType) {
        try {
            if (!Files.isRegularFile(file)) {
                return ResolveResult.message(Component.translatable("chatupgrade.input.error.not_regular_file")
                        .withStyle(ChatFormatting.RED));
            }
            InlineResourceType type = forcedType.orElseGet(() -> inferType(file).orElse(null));
            if (type == null || !hasSupportedExtension(file, type)) {
                return ResolveResult.message(Component.translatable("chatupgrade.input.error.unsupported_file")
                        .withStyle(ChatFormatting.RED));
            }
            long size = Files.size(file);
            if (size > ChatUpgradeConfig.get().maxUploadBytes) {
                return ResolveResult.message(tooLargeMessage(size));
            }
            byte[] bytes = Files.readAllBytes(file);
            String displayName = displayNameFromPath(file);
            return ResolveResult.draft(AttachmentDraft.fromFile(
                    type,
                    file,
                    bytes,
                    displayName,
                    source,
                    contentType(file, type)));
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: attachment draft read failed: {}", e.getMessage());
            return ResolveResult.message(Component.translatable("chatupgrade.input.error.read_failed")
                    .withStyle(ChatFormatting.RED));
        } catch (RuntimeException e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: attachment draft failed: {}", e.toString());
            return ResolveResult.message(Component.translatable("chatupgrade.input.error.read_failed")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static boolean hasClipboardImage() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        AtomicReference<Boolean> ref = new AtomicReference<>(false);
        Runnable read = () -> ref.set(hasClipboardImageOnEdt());
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                read.run();
            } else {
                SwingUtilities.invokeAndWait(read);
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: clipboard image probe failed: {}", e.getMessage());
        }
        return ref.get();
    }

    private static boolean hasClipboardImageOnEdt() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable == null) {
                return false;
            }
            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return true;
            }
            for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
                if (flavor == null) {
                    continue;
                }
                if (flavor.isFlavorTextType()) {
                    continue;
                }
                String mime = flavor.getMimeType();
                if (mime != null && mime.startsWith("image/")) {
                    return true;
                }
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: clipboard image probe content failed: {}", e.getMessage());
        }
        return false;
    }

    private static Optional<Path> readClipboardFile() {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        AtomicReference<Optional<Path>> ref = new AtomicReference<>(Optional.empty());
        Runnable read = () -> ref.set(readClipboardFileOnEdt());
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                read.run();
            } else {
                SwingUtilities.invokeAndWait(read);
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: clipboard file read failed: {}", e.getMessage());
        }
        return ref.get();
    }

    private static Optional<Path> readClipboardFileOnEdt() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return Optional.empty();
            }
            Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(data instanceof List<?> files)) {
                return Optional.empty();
            }
            for (Object item : files) {
                Path path = switch (item) {
                    case File file -> file.toPath();
                    case Path p -> p;
                    default -> null;
                };
                if (path != null) {
                    return Optional.of(path);
                }
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: clipboard file content failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<InlineResourceType> inferType(Path file) {
        String ext = extension(file);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return Optional.of(InlineResourceType.IMAGE);
        }
        if (AUDIO_EXTENSIONS.contains(ext)) {
            return Optional.of(InlineResourceType.AUDIO);
        }
        if (VIDEO_EXTENSIONS.contains(ext)) {
            return Optional.of(InlineResourceType.VIDEO);
        }
        return Optional.empty();
    }

    private static boolean hasSupportedExtension(Path file, InlineResourceType type) {
        String ext = extension(file);
        return switch (type) {
            case IMAGE -> IMAGE_EXTENSIONS.contains(ext);
            case AUDIO -> AUDIO_EXTENSIONS.contains(ext);
            case VIDEO -> VIDEO_EXTENSIONS.contains(ext);
        };
    }

    private static String extension(Path file) {
        Path namePath = file.getFileName();
        if (namePath == null) {
            return "";
        }
        String name = namePath.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String displayNameFromPath(Path file) {
        Path namePath = file.getFileName();
        String name = namePath == null ? "attachment" : namePath.toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String contentType(Path file, InlineResourceType type) {
        try {
            String probed = Files.probeContentType(file);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (IOException ignored) {
        }
        return switch (type) {
            case IMAGE -> "image/" + normalizeImageExtension(extension(file));
            case AUDIO -> "audio/" + extension(file);
            case VIDEO -> "video/" + extension(file);
        };
    }

    private static String normalizeImageExtension(String ext) {
        if ("jpg".equals(ext) || "jfif".equals(ext)) {
            return "jpeg";
        }
        return ext.isBlank() ? "png" : ext;
    }

    private static Component tooLargeMessage(long sizeBytes) {
        return Component.translatable(
                "chatupgrade.upload.too_large",
                ChatUpgradeConfig.formatBytesHuman(ChatUpgradeConfig.get().maxUploadBytes),
                ChatUpgradeConfig.formatBytesHuman(sizeBytes)).withStyle(ChatFormatting.RED);
    }
}