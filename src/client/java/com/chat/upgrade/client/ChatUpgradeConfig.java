package com.chat.upgrade.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/chat-upgrade.json}
 */
public final class ChatUpgradeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    public static final int DEFAULT_MAX_RECEIVE_BYTES = 2 * 1024 * 1024;
    public static final int DEFAULT_MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

    /**
     * Hard cap (10 MiB) for both {@link #maxReceiveBytes} and {@link #maxUploadBytes}, including values read from a
     * hand-edited {@code chat-upgrade.json}. Cannot be exceeded at runtime; normalized values are written back on load
     * when the file contained out-of-range numbers.
     */
    public static final int ABSOLUTE_MAX_TRANSFER_BYTES = 10 * 1024 * 1024;

    /** Same as {@link #ABSOLUTE_MAX_TRANSFER_BYTES} (upload clamp). */
    public static final int ABSOLUTE_MAX_UPLOAD_BYTES = ABSOLUTE_MAX_TRANSFER_BYTES;

    /** Same as {@link #ABSOLUTE_MAX_TRANSFER_BYTES} (receive clamp). */
    public static final int ABSOLUTE_MAX_RECEIVE_BYTES = ABSOLUTE_MAX_TRANSFER_BYTES;

    private static volatile ChatUpgradeConfig instance = defaults();

    /**
     * When true, parse and emit {@code [[CICode,...]]}; when false,
     * {@code [[ChatUpgrade,...]]}.
     */
    public boolean ciCompatibility;

    /**
     * When true, incoming image URLs are not fetched until the player clicks the
     * aqua {@code [图片: …]} placeholder
     * in the chat screen (client-side
     * {@link com.chat.upgrade.client.ManualRevealClickEvent}).
     */
    public boolean manualImageReveal;

    /**
     * Maximum HTTP response body size when downloading an image for chat preview.
     * Default 2 MiB; clamped to {@link #ABSOLUTE_MAX_TRANSFER_BYTES} when loading
     * config.
     */
    public int maxReceiveBytes = DEFAULT_MAX_RECEIVE_BYTES;

    /**
     * Maximum size for local files / clipboard payloads sent via
     * {@code /chatupgrade upload …}.
     * Default 2 MiB; cannot exceed {@link #ABSOLUTE_MAX_TRANSFER_BYTES}.
     */
    public int maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;

    private static ChatUpgradeConfig defaults() {
        ChatUpgradeConfig c = new ChatUpgradeConfig();
        c.ciCompatibility = false;
        c.manualImageReveal = false;
        c.maxReceiveBytes = DEFAULT_MAX_RECEIVE_BYTES;
        c.maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;
        c.normalizeLimits();
        return c;
    }

    /**
     * Clamp and fix invalid values after JSON load or before save.
     *
     * @return {@code true} if any field was changed (e.g. manual edit exceeded {@link #ABSOLUTE_MAX_TRANSFER_BYTES})
     */
    public boolean normalizeLimits() {
        int beforeReceive = maxReceiveBytes;
        int beforeUpload = maxUploadBytes;
        if (maxReceiveBytes <= 0) {
            maxReceiveBytes = DEFAULT_MAX_RECEIVE_BYTES;
        }
        if (maxUploadBytes <= 0) {
            maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;
        }
        maxUploadBytes = Math.min(maxUploadBytes, ABSOLUTE_MAX_TRANSFER_BYTES);
        maxReceiveBytes = Math.min(maxReceiveBytes, ABSOLUTE_MAX_TRANSFER_BYTES);
        return beforeReceive != maxReceiveBytes || beforeUpload != maxUploadBytes;
    }

    public static ChatUpgradeConfig get() {
        return instance;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chat-upgrade.json");
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
                ChatUpgradeConfig read = GSON.fromJson(r, ChatUpgradeConfig.class);
                if (read != null) {
                    boolean corrected = read.normalizeLimits();
                    instance = read;
                    if (corrected) {
                        saveQuiet();
                        com.chat.upgrade.ChatUpgrade.LOGGER.info(
                                "chat-upgrade: config limits were out of range; wrote normalized values to {}",
                                path);
                    }
                } else {
                    instance = defaults();
                }
            } catch (Exception e) {
                com.chat.upgrade.ChatUpgrade.LOGGER.warn("chat-upgrade: failed to load config, using defaults: {}",
                        e.getMessage());
                instance = defaults();
            }
        }
    }

    public static void save() throws IOException {
        synchronized (LOCK) {
            writeConfigFile();
        }
    }

    private static void writeConfigFile() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(instance, w);
        }
    }

    private static void saveQuiet() {
        try {
            synchronized (LOCK) {
                writeConfigFile();
            }
        } catch (IOException e) {
            com.chat.upgrade.ChatUpgrade.LOGGER.warn("chat-upgrade: failed to write default config: {}",
                    e.getMessage());
        }
    }

    public static void setCiCompatibilityAndSave(boolean value) throws IOException {
        synchronized (LOCK) {
            instance.ciCompatibility = value;
            writeConfigFile();
        }
    }

    public static void setManualImageRevealAndSave(boolean value) throws IOException {
        synchronized (LOCK) {
            instance.manualImageReveal = value;
            writeConfigFile();
        }
    }

    public static void setMaxReceiveBytesAndSave(int bytes) throws IOException {
        synchronized (LOCK) {
            instance.maxReceiveBytes = bytes;
            instance.normalizeLimits();
            writeConfigFile();
        }
    }

    public static void setMaxUploadBytesAndSave(int bytes) throws IOException {
        synchronized (LOCK) {
            instance.maxUploadBytes = bytes;
            instance.normalizeLimits();
            writeConfigFile();
        }
    }

    /** Short human-readable size for chat / commands (e.g. {@code 2.0 MiB}). */
    public static String formatBytesHuman(long bytes) {
        if (bytes < 0) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KiB", kb);
        }
        double mib = kb / 1024.0;
        if (mib < 1024) {
            return String.format("%.2f MiB", mib);
        }
        return String.format("%.2f GiB", mib / 1024.0);
    }
}
