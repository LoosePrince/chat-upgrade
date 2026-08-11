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
    public int uploadTimeoutSeconds = 30;
    public int maxPendingUploadsPerPlayer = 2;
    public int maxPendingUploadsGlobal = 64;
    public long maxPendingBytesPerPlayer = 20L * 1024L * 1024L;
    public long maxPendingBytesGlobal = 128L * 1024L * 1024L;
    public int maxStructuredMessagesPer10Seconds = 8;
    public int maxUploadPacketsPer10Seconds = 512;
    public int maxMediaRequestsPer10Seconds = 32;
    public int maxAttachmentWritesPerMinute = 20;
    public int maxHistoryRequestsPerMinute = 6;
    public boolean allowExternalAttachmentUrls;
    public boolean chatHistoryEnabled;
    public int chatHistoryMaxMessages = 500;
    public int chatHistoryReplayLimit = 100;
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
        c.uploadTimeoutSeconds = 30;
        c.maxPendingUploadsPerPlayer = 2;
        c.maxPendingUploadsGlobal = 64;
        c.maxPendingBytesPerPlayer = 20L * 1024L * 1024L;
        c.maxPendingBytesGlobal = 128L * 1024L * 1024L;
        c.maxStructuredMessagesPer10Seconds = 8;
        c.maxUploadPacketsPer10Seconds = 512;
        c.maxMediaRequestsPer10Seconds = 32;
        c.maxAttachmentWritesPerMinute = 20;
        c.maxHistoryRequestsPerMinute = 6;
        c.allowExternalAttachmentUrls = false;
        c.chatHistoryEnabled = false;
        c.chatHistoryMaxMessages = 500;
        c.chatHistoryReplayLimit = 100;
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
        maxChunkBytes = Math.clamp(maxChunkBytes, 1_024, 256 * 1_024);
        maxSingleBytes = Math.min(maxSingleBytes, 10 * 1024 * 1024);
        if (maxTotalBytes < 0) {
            maxTotalBytes = 0;
        }
        ttlSeconds = Math.max(0, ttlSeconds);
        uploadTimeoutSeconds = Math.clamp(uploadTimeoutSeconds, 5, 300);
        maxPendingUploadsPerPlayer = Math.clamp(maxPendingUploadsPerPlayer, 1, 8);
        maxPendingUploadsGlobal = Math.clamp(
                maxPendingUploadsGlobal,
                maxPendingUploadsPerPlayer,
                256);
        maxPendingBytesPerPlayer = Math.clamp(
                maxPendingBytesPerPlayer,
                (long) maxSingleBytes,
                80L * 1024L * 1024L);
        maxPendingBytesGlobal = Math.clamp(
                maxPendingBytesGlobal,
                maxPendingBytesPerPlayer,
                512L * 1024L * 1024L);
        maxStructuredMessagesPer10Seconds = Math.clamp(maxStructuredMessagesPer10Seconds, 1, 100);
        maxUploadPacketsPer10Seconds = Math.clamp(maxUploadPacketsPer10Seconds, 32, 4_096);
        maxMediaRequestsPer10Seconds = Math.clamp(maxMediaRequestsPer10Seconds, 1, 200);
        maxAttachmentWritesPerMinute = Math.clamp(maxAttachmentWritesPerMinute, 1, 120);
        maxHistoryRequestsPerMinute = Math.clamp(maxHistoryRequestsPerMinute, 1, 60);
        chatHistoryMaxMessages = Math.clamp(chatHistoryMaxMessages, 10, 2_000);
        chatHistoryReplayLimit = Math.clamp(chatHistoryReplayLimit, 1, chatHistoryMaxMessages);
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

