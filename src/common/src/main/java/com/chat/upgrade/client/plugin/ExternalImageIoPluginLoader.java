package com.chat.upgrade.client.plugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
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
    private static final String APNG_URL = "https://repo.maven.apache.org/maven2/com/tianscar/imageio/imageio-apng/"
            + APNG_VERSION + "/" + APNG_JAR_NAME;
    private static final String APNG_SHA256 = "a3fa5f977bd0089ce2363ea1c2f2a2731bf02d2343cd569e1406a1c1fded8b45";
    private static final int MAX_PLUGIN_BYTES = 2 * 1024 * 1024;
    private static final ProxySelector NO_PROXY = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        }
    };
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(NO_PROXY)
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
        return verifiedApngJar(apngJarPath());
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

        Path libsDir = libsDir();
        try {
            Files.createDirectories(libsDir);
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot create external plugin dir {}: {}", libsDir, e.getMessage());
            return;
        }
        ensureApngPluginDownloaded(libsDir);
        Path plugin = apngJarPath();
        if (!verifiedApngJar(plugin)) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: APNG plugin is unavailable or failed integrity verification");
            return;
        }

        try {
            externalPluginClassLoader = new URLClassLoader(
                    new URL[] { toUrl(plugin) },
                    ExternalImageIoPluginLoader.class.getClassLoader());

            int registered = 0;
            registered += registerSpis(ImageReaderSpi.class);
            registered += registerSpis(ImageWriterSpi.class);
            registered += registerSpis(ImageInputStreamSpi.class);
            registered += registerSpis(ImageOutputStreamSpi.class);
            registered += registerSpis(ImageTranscoderSpi.class);
            ImageIO.scanForPlugins();
            loaded = registered > 0;

            ChatUpgrade.LOGGER.info(
                    "chat-upgrade: verified APNG ImageIO plugin loaded (providers={})",
                    registered);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to load APNG ImageIO plugin: {}", e.getMessage());
        }
    }

    private static void ensureApngPluginDownloaded(Path libsDir) {
        Path target = libsDir.resolve(APNG_JAR_NAME);
        if (verifiedApngJar(target)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot remove unverified APNG plugin: {}", e.getMessage());
            return;
        }
        ChatUpgrade.LOGGER.info("chat-upgrade: downloading optional pinned APNG plugin");
        Path temp = null;
        try {
            temp = Files.createTempFile(libsDir, APNG_JAR_NAME + ".", ".tmp");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APNG_URL))
                    .timeout(Duration.ofSeconds(45))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                ChatUpgrade.LOGGER.warn("chat-upgrade: APNG plugin download skipped, status={}", response.statusCode());
                return;
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength < 0L || contentLength > MAX_PLUGIN_BYTES) {
                response.body().close();
                throw new IOException("APNG plugin has an invalid Content-Length");
            }
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                copyCapped(in, out, MAX_PLUGIN_BYTES);
            }
            if (!verifiedApngJar(temp)) {
                throw new IOException("APNG plugin SHA-256 mismatch");
            }
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            ChatUpgrade.LOGGER.info("chat-upgrade: verified APNG plugin downloaded to {}", target);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to download APNG plugin: {}", e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void copyCapped(InputStream in, OutputStream out, int maxBytes) throws IOException {
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (total + read > maxBytes) {
                throw new IOException("APNG plugin exceeds size limit");
            }
            out.write(buffer, 0, read);
            total += read;
        }
    }

    private static boolean verifiedApngJar(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path)
                    || Files.size(path) <= 0L
                    || Files.size(path) > MAX_PLUGIN_BYTES) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8_192];
            int total = 0;
            try (InputStream in = Files.newInputStream(path)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (total + read > MAX_PLUGIN_BYTES) {
                        return false;
                    }
                    digest.update(buffer, 0, read);
                    total += read;
                }
            }
            return total > 0 && APNG_SHA256.equals(HexFormat.of().formatHex(digest.digest()));
        } catch (Exception e) {
            return false;
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
