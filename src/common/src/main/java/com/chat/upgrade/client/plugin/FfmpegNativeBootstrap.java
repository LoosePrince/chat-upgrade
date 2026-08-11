package com.chat.upgrade.client.plugin;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.javacpp.Loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Probes the FFmpeg runtime supplied by the application classpath.
 *
 * <p>Native components are deliberately never downloaded or loaded from the
 * configuration directory at runtime. Release builds must embed the supported,
 * dependency-manager-verified native artifacts.</p>
 */
public final class FfmpegNativeBootstrap {
    public static final String FFMPEG_VERSION = "8.1.2-1.5.14";
    public static final String JAVACPP_VERSION = "1.5.14";

    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);
    private static final AtomicBoolean READY = new AtomicBoolean(false);

    private FfmpegNativeBootstrap() {
    }

    public static CompletableFuture<Boolean> warmupAsync() {
        return CompletableFuture.completedFuture(ensureReady());
    }

    public static boolean ensureReady() {
        if (READY.get()) {
            return true;
        }
        if (!ATTEMPTED.compareAndSet(false, true)) {
            return READY.get();
        }

        try {
            Loader.load(avutil.class);
            Loader.load(avcodec.class);
            Loader.load(avformat.class);
            Loader.load(swresample.class);
            Loader.load(swscale.class);
            READY.set(true);
            return true;
        } catch (Throwable throwable) {
            ChatUpgrade.LOGGER.warn(
                    "FFmpeg runtime is unavailable. Runtime native downloads are disabled; use a release with embedded natives.",
                    throwable);
            return false;
        }
    }

    public static boolean reload(boolean clearLegacyRuntimeFiles) {
        if (clearLegacyRuntimeFiles) {
            deleteDownloadedRuntimeJars();
        }
        ATTEMPTED.set(false);
        READY.set(false);
        return ensureReady();
    }

    public static Status status() {
        Path directory = downloadDirectory();
        String platform = resolvePlatform();
        List<Path> jars = List.of(
                directory.resolve("ffmpeg-" + FFMPEG_VERSION + "-" + platform + ".jar"),
                directory.resolve("javacpp-" + JAVACPP_VERSION + "-" + platform + ".jar"));
        boolean legacyFilesPresent = jars.stream().anyMatch(Files::isRegularFile);
        return new Status(
                ATTEMPTED.get(),
                READY.get(),
                false,
                true,
                true,
                platform,
                legacyFilesPresent,
                directory,
                jars);
    }

    public static boolean deleteDownloadedRuntimeJars() {
        boolean success = true;
        Path directory = downloadDirectory();
        if (!Files.isDirectory(directory)) {
            return true;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if ((name.startsWith("ffmpeg-") || name.startsWith("javacpp-")) && name.endsWith(".jar")) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        success = false;
                        ChatUpgrade.LOGGER.warn("Failed to remove legacy FFmpeg runtime file {}", path, exception);
                    }
                }
            }
        } catch (IOException exception) {
            ChatUpgrade.LOGGER.warn("Failed to inspect legacy FFmpeg runtime directory {}", directory, exception);
            return false;
        }
        return success;
    }

    private static Path downloadDirectory() {
        return ChatUpgradeConfig.configPath()
                .getParent()
                .resolve("ffmpeg-runtime");
    }

    private static String resolvePlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = normalizeArchitecture(System.getProperty("os.arch", ""));
        if (osName.contains("win")) {
            return "windows-" + arch;
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "macosx-" + arch;
        }
        if (osName.contains("linux")) {
            return "linux-" + arch;
        }
        return "unsupported";
    }

    private static String normalizeArchitecture(String rawArchitecture) {
        String architecture = rawArchitecture.toLowerCase(Locale.ROOT);
        if (architecture.equals("amd64") || architecture.equals("x86_64")) {
            return "x86_64";
        }
        if (architecture.equals("aarch64") || architecture.equals("arm64")) {
            return "arm64";
        }
        return "unsupported";
    }

    public record Status(
            boolean attempted,
            boolean ready,
            boolean downloading,
            boolean javacppPresent,
            boolean ffmpegPresent,
            String platform,
            boolean jarsPresent,
            Path downloadDirectory,
            List<Path> jars) {
    }
}