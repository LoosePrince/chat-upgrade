package com.chat.upgrade.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/chat-upgrade.json}
 */
public final class ChatUpgradeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static volatile ChatUpgradeConfig instance = defaults();

    /** When true, parse and emit {@code [[CICode,...]]}; when false, {@code [[ChatUpgrade,...]]}. */
    public boolean ciCompatibility;

    /**
     * When true, incoming image URLs are not fetched until the player clicks the aqua {@code [图片: …]} placeholder
     * in the chat screen (client-side {@link com.chat.upgrade.client.ManualRevealClickEvent}).
     */
    public boolean manualImageReveal;

    private static ChatUpgradeConfig defaults() {
        ChatUpgradeConfig c = new ChatUpgradeConfig();
        c.ciCompatibility = false;
        c.manualImageReveal = false;
        return c;
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
                instance = read != null ? read : defaults();
            } catch (Exception e) {
                com.chat.upgrade.ChatUpgrade.LOGGER.warn("chat-upgrade: failed to load config, using defaults: {}", e.getMessage());
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
            com.chat.upgrade.ChatUpgrade.LOGGER.warn("chat-upgrade: failed to write default config: {}", e.getMessage());
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
}
