package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageOutputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageTranscoderSpi;
import javax.imageio.spi.ImageWriterSpi;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads external ImageIO SPI jars from {@code config/chat-upgrade/libs} at startup (no hot reload).
 */
public final class ExternalImageIoPluginLoader {
    private static volatile boolean loaded = false;
    private static URLClassLoader externalPluginClassLoader;

    private ExternalImageIoPluginLoader() {
    }

    public static synchronized void loadAtStartup() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path libsDir = FabricLoader.getInstance().getConfigDir().resolve("chat-upgrade").resolve("libs");
        try {
            Files.createDirectories(libsDir);
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot create external plugin dir {}: {}", libsDir, e.getMessage());
            return;
        }

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
