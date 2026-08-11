package com.chat.upgrade.client.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.javacpp.Loader;

/** Securely acquires and loads the pinned current-platform FFmpeg natives. */
public final class FfmpegNativeBootstrap {
    public static final String FFMPEG_VERSION = "8.1.2-1.5.14";
    public static final String JAVACPP_VERSION = "1.5.14";

    private static final String MAVEN_BASE = "https://repo.maven.apache.org/maven2";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final int MAX_NATIVE_ENTRIES = 256;
    private static final long MAX_NATIVE_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_NATIVE_TOTAL_BYTES = 512L * 1024L * 1024L;
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> PRIVATE_EXECUTABLE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Map<String, PlatformArtifact> PLATFORM_ARTIFACTS = Map.of(
            "windows-x86_64", new PlatformArtifact(
                    "windows-x86_64",
                    ".dll",
                    new Artifact("javacpp", JAVACPP_VERSION, 2_031_831L,
                            "69bb4b0322aa807199485a9bf394ffe2117bb9ef5c34762b7d6567d7053087b5"),
                    new Artifact("ffmpeg", FFMPEG_VERSION, 30_441_678L,
                            "67ebc3e6940add83b1b8a9dfd337a5c67556f2b72cb3225efe91a26874445c8e")),
            "linux-x86_64", new PlatformArtifact(
                    "linux-x86_64",
                    ".so",
                    new Artifact("javacpp", JAVACPP_VERSION, 47_603L,
                            "1efab617735a44529bb85daaaefabe46c8686582989c46593c36569e9786fdaa"),
                    new Artifact("ffmpeg", FFMPEG_VERSION, 26_915_834L,
                            "6d0f000c4ddede3b669aa7c5c585e9b71f69b7f621fed7ab28ec01045dc336d0")),
            "linux-arm64", new PlatformArtifact(
                    "linux-arm64",
                    ".so",
                    new Artifact("javacpp", JAVACPP_VERSION, 42_779L,
                            "30f63f17bb05cba4bdf488b77a3d9785b2ec261d4d528cf219b6c7081d20d636"),
                    new Artifact("ffmpeg", FFMPEG_VERSION, 26_304_735L,
                            "c8729978c862b0e2e5643ade1b41266c5ba8c34b791f41104e5794ad3cefd0bf")),
            "macosx-x86_64", new PlatformArtifact(
                    "macosx-x86_64",
                    ".dylib",
                    new Artifact("javacpp", JAVACPP_VERSION, 39_995L,
                            "ce2e642c0317d08f08fc97ba44b2c18d2a22a85516fdec6b9854f3a930c5127b"),
                    new Artifact("ffmpeg", FFMPEG_VERSION, 23_977_215L,
                            "b5a8f5124fb2d04412bd01eb5ca469d153d1c396fd0c67a33d3f9a1501c94c0c")),
            "macosx-arm64", new PlatformArtifact(
                    "macosx-arm64",
                    ".dylib",
                    new Artifact("javacpp", JAVACPP_VERSION, 38_712L,
                            "75d755656d3ddafaa51c5d2d8d340c731a0bf2d742b033a37641a905aa2a7332"),
                    new Artifact("ffmpeg", FFMPEG_VERSION, 20_582_183L,
                            "af57468c0bb7b2e9c8c93547e6d599e2b4faa4117118555be92675850972420c")));

    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);
    private static final AtomicBoolean READY = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);

    private FfmpegNativeBootstrap() {
    }

    public static CompletableFuture<Boolean> warmupAsync() {
        return CompletableFuture.supplyAsync(FfmpegNativeBootstrap::ensureReady);
    }

    public static boolean ensureReady() {
        return ensureReady(PluginDownloadProgress.NONE);
    }

    public static boolean ensureReady(PluginDownloadProgress progress) {
        PluginDownloadProgress safeProgress = progress == null ? PluginDownloadProgress.NONE : progress;
        if (READY.get()) {
            return true;
        }
        if (!ATTEMPTED.compareAndSet(false, true)) {
            return READY.get();
        }

        try {
            PlatformArtifact platform = resolvePlatform();
            if (platform == null) {
                ChatUpgrade.LOGGER.warn(
                        "chat-upgrade: FFmpeg runtime download skipped for unsupported os={} arch={}",
                        System.getProperty("os.name"), System.getProperty("os.arch"));
                return false;
            }
            Path artifacts = prepareArtifactDirectory();
            Path javacppJar = acquireArtifact(artifacts, platform, platform.javacpp(), safeProgress);
            Path ffmpegJar = acquireArtifact(artifacts, platform, platform.ffmpeg(), safeProgress);
            Path nativeDirectory = prepareNativeDirectory();
            extractNativeLibraries(javacppJar, nativeDirectory, platform);
            extractNativeLibraries(ffmpegJar, nativeDirectory, platform);
            loadVerifiedNatives(nativeDirectory, platform);
            READY.set(true);
            ChatUpgrade.LOGGER.info("chat-upgrade: verified FFmpeg natives ready for {}", platform.classifier());
            return true;
        } catch (Exception | LinkageError exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: FFmpeg runtime bootstrap failed", exception);
            return false;
        }
    }

    public static boolean reload(boolean clearDownloadedRuntimeFiles) {
        return reload(clearDownloadedRuntimeFiles, PluginDownloadProgress.NONE);
    }

    public static boolean reload(boolean clearDownloadedRuntimeFiles, PluginDownloadProgress progress) {
        if (READY.get()) {
            return true;
        }
        if (clearDownloadedRuntimeFiles && !deleteDownloadedRuntimeJars()) {
            return false;
        }
        ATTEMPTED.set(false);
        READY.set(false);
        return ensureReady(progress);
    }

    public static Status status() {
        PlatformArtifact platform = resolvePlatform();
        Path directory = artifactDirectory();
        List<Path> jars = platform == null
                ? List.of()
                : List.of(
                        artifactPath(directory, platform, platform.javacpp()),
                        artifactPath(directory, platform, platform.ffmpeg()));
        boolean javacppPresent = platform != null && isVerifiedArtifact(jars.getFirst(), platform.javacpp());
        boolean ffmpegPresent = platform != null && isVerifiedArtifact(jars.getLast(), platform.ffmpeg());
        return new Status(
                ATTEMPTED.get(),
                READY.get(),
                DOWNLOADING.get(),
                javacppPresent,
                ffmpegPresent,
                platform == null ? "unsupported" : platform.classifier(),
                javacppPresent && ffmpegPresent,
                directory,
                jars);
    }

    public static boolean deleteDownloadedRuntimeJars() {
        PlatformArtifact platform = resolvePlatform();
        if (platform == null) {
            return true;
        }
        boolean success = true;
        for (Artifact artifact : List.of(platform.javacpp(), platform.ffmpeg())) {
            Path jar = artifactPath(artifactDirectory(), platform, artifact);
            try {
                Files.deleteIfExists(jar);
            } catch (IOException exception) {
                success = false;
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to remove FFmpeg runtime artifact {}", jar);
            }
        }
        return success;
    }

    private static Path acquireArtifact(
            Path runtime,
            PlatformArtifact platform,
            Artifact artifact,
            PluginDownloadProgress progress) throws Exception {
        Path target = artifactPath(runtime, platform, artifact);
        if (isVerifiedArtifact(target, artifact)) {
            return target;
        }
        Files.deleteIfExists(target);
        Path temporary = Files.createTempFile(runtime, artifact.fileName(platform.classifier()) + ".", ".part");
        try {
            setPrivatePermissions(temporary, false);
            downloadVerifiedArtifact(artifact, platform.classifier(), temporary, progress);
            moveAtomically(temporary, target);
            setPrivatePermissions(target, false);
            if (!isVerifiedArtifact(target, artifact)) {
                throw new IOException("installed artifact verification failed");
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void downloadVerifiedArtifact(
            Artifact artifact,
            String classifier,
            Path target,
            PluginDownloadProgress progress) throws Exception {
        DOWNLOADING.set(true);
        try {
            URI uri = URI.create(artifact.url(classifier));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"repo.maven.apache.org".equalsIgnoreCase(uri.getHost())) {
                throw new IOException("refusing an unpinned FFmpeg artifact origin");
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("download failed with HTTP " + response.statusCode());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declaredLength != artifact.sizeBytes()) {
                response.body().close();
                throw new IOException("unexpected artifact content length");
            }
            progress.update(artifact.artifact(), 0L, artifact.sizeBytes());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = 0L;
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[16_384];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    copied = Math.addExact(copied, read);
                    if (copied > artifact.sizeBytes()) {
                        throw new IOException("artifact exceeds its fixed byte limit");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                    progress.update(artifact.artifact(), copied, artifact.sizeBytes());
                }
            }
            if (copied != artifact.sizeBytes()
                    || !artifact.sha256().equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("artifact SHA-256 verification failed");
            }
        } finally {
            DOWNLOADING.set(false);
        }
    }

    private static void extractNativeLibraries(Path jar, Path nativeDirectory, PlatformArtifact platform) throws Exception {
        long extracted = 0L;
        int entries = 0;
        try (JarFile archive = new JarFile(jar.toFile(), true)) {
            var enumeration = archive.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(platform.extension())) {
                    continue;
                }
                String filename = safeNativeFilename(name, platform.extension());
                long size = entry.getSize();
                if (size < 1L || size > MAX_NATIVE_ENTRY_BYTES || ++entries > MAX_NATIVE_ENTRIES) {
                    throw new IOException("invalid native library archive entry");
                }
                extracted = Math.addExact(extracted, size);
                if (extracted > MAX_NATIVE_TOTAL_BYTES) {
                    throw new IOException("native library extraction limit exceeded");
                }
                Path output = nativeDirectory.resolve(filename).normalize();
                if (!output.getParent().equals(nativeDirectory) || Files.exists(output)) {
                    throw new IOException("duplicate or unsafe native library filename");
                }
                copyJarEntryAtomically(archive, entry, output, size);
            }
        }
        if (entries == 0) {
            throw new IOException("verified artifact contains no native libraries");
        }
    }

    static String safeNativeFilename(String entryName, String extension) throws IOException {
        int separator = entryName.lastIndexOf('/');
        String filename = separator < 0 ? entryName : entryName.substring(separator + 1);
        if (filename.isBlank()
                || filename.length() > 180
                || !filename.endsWith(extension)
                || !filename.matches("[A-Za-z0-9._-]+")
                || entryName.contains("..")) {
            throw new IOException("unsafe native library archive path");
        }
        return filename;
    }

    private static void copyJarEntryAtomically(JarFile archive, JarEntry entry, Path output, long expectedSize)
            throws IOException {
        Path temporary = Files.createTempFile(output.getParent(), output.getFileName().toString() + ".", ".part");
        try {
            setPrivatePermissions(temporary, true);
            long copied = 0L;
            try (InputStream input = archive.getInputStream(entry); OutputStream target = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16_384];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    copied = Math.addExact(copied, read);
                    if (copied > expectedSize) {
                        throw new IOException("native library entry exceeds declared size");
                    }
                    target.write(buffer, 0, read);
                }
            }
            if (copied != expectedSize) {
                throw new IOException("native library entry is truncated");
            }
            moveAtomically(temporary, output);
            setPrivatePermissions(output, true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void loadVerifiedNatives(Path nativeDirectory, PlatformArtifact platform) throws IOException {
        if (Files.isSymbolicLink(nativeDirectory) || !Files.isDirectory(nativeDirectory)) {
            throw new IOException("unsafe FFmpeg native directory");
        }
        preloadVerifiedLibraries(nativeDirectory, platform);
        Properties properties = new Properties();
        properties.putAll(Loader.loadProperties());
        String nativePath = nativeDirectory.toAbsolutePath().toString();
        properties.setProperty("platform.preloadpath", nativePath);
        properties.setProperty("platform.linkpath", nativePath);
        try {
            Loader.load(avutil.class, properties, true);
            Loader.load(avcodec.class, properties, true);
            Loader.load(avformat.class, properties, true);
            Loader.load(swresample.class, properties, true);
            Loader.load(swscale.class, properties, true);
            String version = avutil.av_version_info().getString();
            if (version == null || version.isBlank()) {
                throw new IOException("FFmpeg version probe returned no version");
            }
        } catch (LinkageError exception) {
            throw new IOException("JavaCPP failed to load verified FFmpeg natives", exception);
        }
    }

    private static void preloadVerifiedLibraries(Path nativeDirectory, PlatformArtifact platform) throws IOException {
        List<String> libraries = switch (platform.classifier()) {
            case "windows-x86_64" -> List.of(
                    "ucrtbase.dll",
                    "vcruntime140.dll",
                    "vcruntime140_1.dll",
                    "msvcp140.dll",
                    "libwinpthread-1.dll",
                    "avutil-60.dll",
                    "swresample-6.dll",
                    "swscale-9.dll",
                    "avcodec-62.dll",
                    "avformat-62.dll",
                    "jniavutil.dll",
                    "jniavcodec.dll",
                    "jniavformat.dll",
                    "jniswresample.dll",
                    "jniswscale.dll");
            case "linux-x86_64", "linux-arm64" -> List.of(
                    "libdrm.so.2",
                    "libva.so.2",
                    "libva-drm.so.2",
                    "libavutil.so.60",
                    "libswresample.so.6",
                    "libswscale.so.9",
                    "libavcodec.so.62",
                    "libavformat.so.62",
                    "libjniavutil.so",
                    "libjniavcodec.so",
                    "libjniavformat.so",
                    "libjniswresample.so",
                    "libjniswscale.so");
            case "macosx-x86_64", "macosx-arm64" -> List.of(
                    "libatomic.1.dylib",
                    "libavutil.60.dylib",
                    "libswresample.6.dylib",
                    "libswscale.9.dylib",
                    "libavcodec.62.dylib",
                    "libavformat.62.dylib",
                    "libjniavutil.dylib",
                    "libjniavcodec.dylib",
                    "libjniavformat.dylib",
                    "libjniswresample.dylib",
                    "libjniswscale.dylib");
            default -> throw new IOException("unsupported FFmpeg runtime platform");
        };
        for (String library : libraries) {
            Path path = nativeDirectory.resolve(library).normalize();
            if (!path.getParent().equals(nativeDirectory)
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path)) {
                throw new IOException("required verified FFmpeg native is unavailable: " + library);
            }
            try {
                System.load(path.toAbsolutePath().toString());
            } catch (UnsatisfiedLinkError exception) {
                throw new IOException("failed to load verified FFmpeg native: " + library, exception);
            }
        }
    }

    private static Path prepareArtifactDirectory() throws IOException {
        Path directory = artifactDirectory();
        ensurePrivateDirectory(directory);
        deleteLegacyRuntimeDirectory();
        return directory;
    }

    private static Path prepareNativeDirectory() throws IOException {
        Path directory = nativeDirectory();
        ensurePrivateDirectory(directory.getParent());
        if (Files.exists(directory)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
                throw new IOException("unsafe FFmpeg native directory");
            }
            deleteDirectoryContents(directory);
        } else {
            Files.createDirectories(directory);
        }
        ensurePrivateDirectory(directory);
        return directory;
    }

    private static Path artifactDirectory() {
        return ChatUpgradeConfig.configPath().getParent().resolve("libs");
    }

    private static Path nativeDirectory() {
        return ChatUpgradeConfig.configPath().getParent().resolve("cache").resolve("ffmpeg-native");
    }

    private static void deleteLegacyRuntimeDirectory() {
        Path legacy = ChatUpgradeConfig.configPath().getParent().resolve("ffmpeg-runtime");
        try {
            if (Files.exists(legacy) && !Files.isSymbolicLink(legacy) && Files.isDirectory(legacy)) {
                deleteDirectoryContents(legacy);
                Files.deleteIfExists(legacy);
            }
        } catch (IOException exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to remove legacy FFmpeg runtime cache {}", legacy);
        }
    }

    private static void deleteDirectoryContents(Path directory) throws IOException {
        List<Path> paths;
        try (var walk = Files.walk(directory)) {
            paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            if (path.equals(directory)) {
                continue;
            }
            if (!Files.isSymbolicLink(path) && !Files.isRegularFile(path) && !Files.isDirectory(path)) {
                throw new IOException("unsafe entry in FFmpeg runtime directory");
            }
            Files.deleteIfExists(path);
        }
    }

    private static void ensurePrivateDirectory(Path directory) throws IOException {
        if (Files.exists(directory) && (Files.isSymbolicLink(directory) || !Files.isDirectory(directory))) {
            throw new IOException("unsafe FFmpeg runtime directory: " + directory);
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("unsafe FFmpeg runtime directory: " + directory);
        }
        setPrivatePermissions(directory, true);
    }

    private static void setPrivatePermissions(Path path, boolean executable) {
        try {
            Files.setPosixFilePermissions(path, executable ? PRIVATE_EXECUTABLE_PERMISSIONS : PRIVATE_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static boolean isVerifiedArtifact(Path path, Artifact artifact) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) != artifact.sizeBytes()) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16_384];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return artifact.sha256().equals(HexFormat.of().formatHex(digest.digest()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path artifactPath(Path runtime, PlatformArtifact platform, Artifact artifact) {
        return runtime.resolve(artifact.fileName(platform.classifier()));
    }

    private static PlatformArtifact resolvePlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = normalizeArchitecture(System.getProperty("os.arch", ""));
        String classifier = os.contains("win") ? "windows-" + architecture
                : os.contains("linux") ? "linux-" + architecture
                : (os.contains("mac") || os.contains("darwin")) ? "macosx-" + architecture
                : "unsupported";
        return PLATFORM_ARTIFACTS.get(classifier);
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

    record Artifact(String artifact, String version, long sizeBytes, String sha256) {
        private String fileName(String classifier) {
            return artifact + "-" + version + "-" + classifier + ".jar";
        }

        private String url(String classifier) {
            return MAVEN_BASE + "/org/bytedeco/" + artifact + "/" + version + "/" + fileName(classifier);
        }
    }

    private record PlatformArtifact(String classifier, String extension, Artifact javacpp, Artifact ffmpeg) {
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
