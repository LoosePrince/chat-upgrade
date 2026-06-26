package com.chat.upgrade.server;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.chat.upgrade.ChatUpgrade;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.chat.upgrade.platform.Platform;

/**
 * {@code config/chat-upgrade/server-media.json}
 */
public final class ServerMediaServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    private static volatile ServerMediaServerConfig instance = defaults();

    public boolean enabled = true;
    public StorageMode storageMode = StorageMode.MEMORY;
    public int maxSingleBytes = 2 * 1024 * 1024;
    public int maxChunkBytes = 32 * 1024;
    public long maxTotalBytes = 200L * 1024L * 1024L;
    public int ttlSeconds = 60 * 60;
    public String diskFolderName = "server-media-store";

    public enum StorageMode {
        MEMORY, DISK
    }

    public static ServerMediaServerConfig get() {
        return instance;
    }

    public static Path configPath() {
        return Platform.configDir()
                .resolve("chat-upgrade")
                .resolve("server-media.json");
    }

    public static void load() {
        synchronized (LOCK) {
            Path path = configPath();
            if (!Files.isRegularFile(path)) {
                instance = defaults();
                saveQuiet();
                return;
            }
            try (Reader r = Files.newBufferedReader(path)) {
                ServerMediaServerConfig read = GSON.fromJson(r, ServerMediaServerConfig.class);
                if (read != null) {
                    read.normalize();
                    instance = read;
                } else {
                    instance = defaults();
                }
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to load server-media config, using defaults: {}",
                        e.getMessage());
                instance = defaults();
            }
        }
    }

    private static ServerMediaServerConfig defaults() {
        ServerMediaServerConfig c = new ServerMediaServerConfig();
        c.enabled = true;
        c.storageMode = StorageMode.MEMORY;
        c.maxSingleBytes = 2 * 1024 * 1024;
        c.maxChunkBytes = 32 * 1024;
        c.maxTotalBytes = 200L * 1024L * 1024L;
        c.ttlSeconds = 60 * 60;
        c.diskFolderName = "server-media-store";
        c.normalize();
        return c;
    }

    public void normalize() {
        if (maxSingleBytes <= 0) {
            maxSingleBytes = 2 * 1024 * 1024;
        }
        if (maxChunkBytes <= 0) {
            maxChunkBytes = 32 * 1024;
        }
        maxChunkBytes = Math.min(maxChunkBytes, 256 * 1024);
        maxSingleBytes = Math.min(maxSingleBytes, 10 * 1024 * 1024);
        if (maxTotalBytes < 0) {
            maxTotalBytes = 0;
        }
        ttlSeconds = Math.max(0, ttlSeconds);
        if (storageMode == null) {
            storageMode = StorageMode.MEMORY;
        }
        if (diskFolderName == null || diskFolderName.isBlank()) {
            diskFolderName = "server-media-store";
        }
    }

    private static void saveQuiet() {
        try {
            synchronized (LOCK) {
                writeFile();
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to write default server-media config: {}", e.getMessage());
        }
    }

    private static void writeFile() throws Exception {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(instance, w);
        }
    }
}

