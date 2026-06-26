package com.chat.upgrade.client.plugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bytedeco.ffmpeg.global.avutil;

import com.chat.upgrade.ChatUpgrade;

/**
 * Ensures JavaCPP/FFmpeg native libraries are available in production runtime.
 *
 * Strategy:
 * 1) Try direct probe first (already bundled or previously loaded).
 * 2) If missing, download current-platform classifier jars into
 * config/chat-upgrade/libs.
 * 3) Extract native binaries directly into java.library.path writable directory
 * (typically Minecraft's <version>-natives folder).
 * 4) System.load non-jni libs first, then jni* libs.
 */
public final class FfmpegNativeBootstrap {
    private static final String JAVACPP_VERSION = "1.5.11";
    private static final String FFMPEG_VERSION = "7.1-1.5.11";
    private static final String MAVEN_BASE = "https://repo1.maven.org/maven2";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Object LOCK = new Object();
    private static volatile boolean ready = false;
    private static volatile boolean attempted = false;

    private FfmpegNativeBootstrap() {
    }

    public record Status(
            boolean ready,
            boolean attempted,
            String platform,
            Path libsDir,
            Path javacppJar,
            boolean javacppPresent,
            Path ffmpegJar,
            boolean ffmpegPresent) {
    }

    public static void warmupAsync() {
        CompletableFuture.runAsync(FfmpegNativeBootstrap::ensureReady);
    }

    public static Status status() {
        Platform platform = detectPlatform();
        Path libsDir = com.chat.upgrade.platform.Platform.configDir().resolve("chat-upgrade").resolve("libs");
        Path javacppJar = platform == null ? null
                : libsDir.resolve("javacpp-" + JAVACPP_VERSION + "-" + platform.classifier + ".jar");
        Path ffmpegJar = platform == null ? null
                : libsDir.resolve("ffmpeg-" + FFMPEG_VERSION + "-" + platform.classifier + ".jar");
        return new Status(
                ready,
                attempted,
                platform == null ? "unsupported" : platform.classifier,
                libsDir,
                javacppJar,
                javacppJar != null && Files.isRegularFile(javacppJar),
                ffmpegJar,
                ffmpegJar != null && Files.isRegularFile(ffmpegJar));
    }

    public static boolean reload(boolean forceDownload) {
        synchronized (LOCK) {
            ready = false;
            attempted = false;
            if (forceDownload) {
                deleteDownloadedRuntimeJars();
            }
        }
        return ensureReady();
    }

    public static boolean ensureReady() {
        if (ready) {
            return true;
        }
        synchronized (LOCK) {
            if (ready) {
                return true;
            }
            if (attempted && !ready) {
                return false;
            }
            attempted = true;
            try {
                Platform platform = detectPlatform();
                if (platform == null) {
                    ChatUpgrade.LOGGER.warn(
                            "chat-upgrade: FFmpeg auto-download skipped: unsupported platform os={} arch={}",
                            System.getProperty("os.name"),
                            System.getProperty("os.arch"));
                    return false;
                }
                Path libsDir = com.chat.upgrade.platform.Platform.configDir().resolve("chat-upgrade").resolve("libs");
                Files.createDirectories(libsDir);
                Path nativeDir = resolveJavaLibraryPathWritableDir();
                if (nativeDir == null) {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: no writable java.library.path directory found");
                    return false;
                }

                Path javacppJar = libsDir.resolve("javacpp-" + JAVACPP_VERSION + "-" + platform.classifier + ".jar");
                Path ffmpegJar = libsDir.resolve("ffmpeg-" + FFMPEG_VERSION + "-" + platform.classifier + ".jar");
                downloadIfMissing(javacppUrl(platform.classifier), javacppJar);
                downloadIfMissing(ffmpegUrl(platform.classifier), ffmpegJar);

                extractNativeBinaries(javacppJar, nativeDir, platform.extension);
                extractNativeBinaries(ffmpegJar, nativeDir, platform.extension);
                loadNativeBinaries(nativeDir, platform.extension);

                if (probeFfmpegReady()) {
                    ready = true;
                    ChatUpgrade.LOGGER.info(
                            "chat-upgrade: FFmpeg natives ready ({}) from {}",
                            platform.classifier,
                            nativeDir);
                } else {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: FFmpeg probe still failed after native bootstrap");
                }
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: FFmpeg native bootstrap failed: {}", e.toString());
            }
            return ready;
        }
    }

    private static boolean probeFfmpegReady() {
        try {
            String v = avutil.av_version_info().getString();
            return v != null && !v.isBlank();
        } catch (Throwable t) {
            ChatUpgrade.LOGGER.warn(
                    "chat-upgrade: FFmpeg probe failed type={} msg={}",
                    t.getClass().getName(),
                    t.getMessage());
            return false;
        }
    }

    private static void downloadIfMissing(String url, Path target) throws Exception {
        if (Files.isRegularFile(target) && Files.size(target) > 0L) {
            return;
        }
        ChatUpgrade.LOGGER.info("chat-upgrade: downloading FFmpeg runtime {}", url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("download failed status=" + resp.statusCode() + " url=" + url);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(temp)) {
            in.transferTo(out);
        }
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void extractNativeBinaries(Path jar, Path nativeDir, String extension) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (!name.contains("/")) {
                    continue;
                }
                String file = name.substring(name.lastIndexOf('/') + 1);
                if (!file.toLowerCase(Locale.ROOT).endsWith(extension)) {
                    continue;
                }
                Path out = nativeDir.resolve(file);
                if (Files.isRegularFile(out) && Files.size(out) > 0L) {
                    continue;
                }
                try (InputStream in = jf.getInputStream(e)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void loadNativeBinaries(Path nativeDir, String extension) throws Exception {
        List<Path> all = new ArrayList<>();
        try (var s = Files.list(nativeDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(all::add);
        }
        List<Path> nonJni = all.stream()
                .filter(p -> !p.getFileName().toString().toLowerCase(Locale.ROOT).contains("jni"))
                .toList();
        List<Path> jni = all.stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("jni"))
                .toList();

        int loadedBase = loadWithRetry(nonJni, "base");
        int loadedJni = loadWithRetry(jni, "jni");
        ChatUpgrade.LOGGER.info(
                "chat-upgrade: native load summary dir={} baseLoaded={}/{} jniLoaded={}/{}",
                nativeDir,
                loadedBase,
                nonJni.size(),
                loadedJni,
                jni.size());
    }

    private static int loadWithRetry(List<Path> libs, String stage) {
        if (libs.isEmpty()) {
            return 0;
        }
        List<Path> pending = new ArrayList<>(libs);
        Map<Path, String> lastErrors = new LinkedHashMap<>();
        int loaded = 0;
        boolean progressed;
        do {
            progressed = false;
            List<Path> next = new ArrayList<>();
            for (Path lib : pending) {
                try {
                    System.load(lib.toAbsolutePath().toString());
                    loaded++;
                    progressed = true;
                } catch (UnsatisfiedLinkError e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("already loaded")) {
                        loaded++;
                        progressed = true;
                    } else {
                        next.add(lib);
                        lastErrors.put(lib, msg == null ? "unknown" : msg);
                    }
                }
            }
            pending = next;
        } while (progressed && !pending.isEmpty());

        if (!pending.isEmpty()) {
            for (Path lib : pending) {
                ChatUpgrade.LOGGER.warn(
                        "chat-upgrade: native load unresolved stage={} lib={} reason={}",
                        stage,
                        lib.getFileName(),
                        lastErrors.getOrDefault(lib, "unknown"));
            }
        }
        return loaded;
    }

    private static Path resolveJavaLibraryPathWritableDir() {
        String raw = System.getProperty("java.library.path", "");
        if (raw.isBlank()) {
            return null;
        }
        String[] entries = raw.split(java.io.File.pathSeparator);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path p = Path.of(entry);
            if (Files.isDirectory(p) && Files.isWritable(p)) {
                ChatUpgrade.LOGGER.info("chat-upgrade: selected java.library.path target {}", p);
                return p;
            }
        }
        return null;
    }

    private static String javacppUrl(String classifier) {
        return MAVEN_BASE + "/org/bytedeco/javacpp/" + JAVACPP_VERSION
                + "/javacpp-" + JAVACPP_VERSION + "-" + classifier + ".jar";
    }

    private static String ffmpegUrl(String classifier) {
        return MAVEN_BASE + "/org/bytedeco/ffmpeg/" + FFMPEG_VERSION
                + "/ffmpeg-" + FFMPEG_VERSION + "-" + classifier + ".jar";
    }

    private static void deleteDownloadedRuntimeJars() {
        Platform platform = detectPlatform();
        if (platform == null) {
            return;
        }
        Path libsDir = com.chat.upgrade.platform.Platform.configDir().resolve("chat-upgrade").resolve("libs");
        Path javacppJar = libsDir.resolve("javacpp-" + JAVACPP_VERSION + "-" + platform.classifier + ".jar");
        Path ffmpegJar = libsDir.resolve("ffmpeg-" + FFMPEG_VERSION + "-" + platform.classifier + ".jar");
        try {
            Files.deleteIfExists(javacppJar);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("chat-upgrade: failed to delete {}", javacppJar);
        }
        try {
            Files.deleteIfExists(ffmpegJar);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("chat-upgrade: failed to delete {}", ffmpegJar);
        }
    }

    private static Platform detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("win")) {
            if (arm) {
                return null;
            }
            return new Platform("windows-x86_64", ".dll");
        }
        if (os.contains("linux")) {
            return new Platform(arm ? "linux-arm64" : "linux-x86_64", ".so");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return new Platform(arm ? "macosx-arm64" : "macosx-x86_64", ".dylib");
        }
        return null;
    }

    private record Platform(String classifier, String extension) {
    }
}
