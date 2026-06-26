package com.chat.upgrade.client.plugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageOutputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageTranscoderSpi;
import javax.imageio.spi.ImageWriterSpi;

import com.chat.upgrade.ChatUpgrade;

import com.chat.upgrade.platform.Platform;

/**
 * Loads external ImageIO SPI jars from {@code config/chat-upgrade/libs} at
 * startup (no hot reload).
 */
public final class ExternalImageIoPluginLoader {
    private static final String APNG_VERSION = "1.0.1";
    private static final String APNG_JAR_NAME = "imageio-apng-" + APNG_VERSION + ".jar";
    private static final String APNG_URL = "https://repo1.maven.org/maven2/com/tianscar/imageio/imageio-apng/"
            + APNG_VERSION + "/" + APNG_JAR_NAME;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static volatile boolean loaded = false;
    private static URLClassLoader externalPluginClassLoader;

    private ExternalImageIoPluginLoader() {
    }

    public static Path libsDir() {
        return Platform.configDir().resolve("chat-upgrade").resolve("libs");
    }

    public static Path apngJarPath() {
        return libsDir().resolve(APNG_JAR_NAME);
    }

    public static boolean hasApngJar() {
        return Files.isRegularFile(apngJarPath());
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static synchronized void reload(boolean forceDownload) {
        if (forceDownload) {
            try {
                Files.deleteIfExists(apngJarPath());
            } catch (IOException e) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: delete old APNG plugin failed: {}", e.getMessage());
            }
        }
        loaded = false;
        loadAtStartup();
    }

    public static synchronized void loadAtStartup() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path libsDir = libsDir();
        try {
            Files.createDirectories(libsDir);
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot create external plugin dir {}: {}", libsDir, e.getMessage());
            return;
        }
        ensureApngPluginDownloaded(libsDir);

        List<Path> jars = new ArrayList<>();
        try (var stream = Files.list(libsDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(jars::add);
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to scan external plugin dir {}: {}", libsDir, e.getMessage());
            return;
        }

        if (jars.isEmpty()) {
            ChatUpgrade.LOGGER.info("chat-upgrade: no external ImageIO plugin jar found in {}", libsDir);
            return;
        }

        try {
            URL[] urls = jars.stream().map(ExternalImageIoPluginLoader::toUrl).toArray(URL[]::new);
            externalPluginClassLoader = new URLClassLoader(
                    urls,
                    ExternalImageIoPluginLoader.class.getClassLoader());

            int registered = 0;
            registered += registerSpis(ImageReaderSpi.class);
            registered += registerSpis(ImageWriterSpi.class);
            registered += registerSpis(ImageInputStreamSpi.class);
            registered += registerSpis(ImageOutputStreamSpi.class);
            registered += registerSpis(ImageTranscoderSpi.class);

            // Let ImageIO discover any provider that relies on SPI metadata scanning.
            ImageIO.scanForPlugins();

            ChatUpgrade.LOGGER.info(
                    "chat-upgrade: external ImageIO plugins loaded from {} (jars={}, providers={})",
                    libsDir,
                    jars.size(),
                    registered);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to load external ImageIO plugins: {}", e.getMessage());
        }
    }

    private static void ensureApngPluginDownloaded(Path libsDir) {
        Path target = libsDir.resolve(APNG_JAR_NAME);
        if (Files.isRegularFile(target)) {
            return;
        }
        ChatUpgrade.LOGGER.info("chat-upgrade: downloading optional APNG plugin {}", APNG_URL);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APNG_URL))
                    .timeout(Duration.ofSeconds(45))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ChatUpgrade.LOGGER.warn(
                        "chat-upgrade: APNG plugin download skipped, status={} url={}",
                        response.statusCode(),
                        APNG_URL);
                return;
            }
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                in.transferTo(out);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            ChatUpgrade.LOGGER.info("chat-upgrade: APNG plugin downloaded to {}", target);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to download APNG plugin: {}", e.getMessage());
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad jar path: " + path, e);
        }
    }

    private static <T> int registerSpis(Class<T> spiType) {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        int count = 0;
        for (T spi : ServiceLoader.load(spiType, externalPluginClassLoader)) {
            registry.registerServiceProvider(spi);
            count++;
        }
        return count;
    }
}
