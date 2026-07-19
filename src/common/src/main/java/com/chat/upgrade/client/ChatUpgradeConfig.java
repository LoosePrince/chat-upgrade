package com.chat.upgrade.client;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.chat.upgrade.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** {@code config/chat-upgrade/chat-upgrade.json} */
public final class ChatUpgradeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    public static final int DEFAULT_MAX_RECEIVE_BYTES = 2 * 1024 * 1024;
    public static final int DEFAULT_MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    public static final int ABSOLUTE_MAX_TRANSFER_BYTES = 10 * 1024 * 1024;
    public static final int ABSOLUTE_MAX_UPLOAD_BYTES = ABSOLUTE_MAX_TRANSFER_BYTES;
    public static final int ABSOLUTE_MAX_RECEIVE_BYTES = ABSOLUTE_MAX_TRANSFER_BYTES;

    private static volatile ChatUpgradeConfig instance = defaults();

    public boolean ciCompatibility;
    public ChatInputMode chatInputMode = ChatInputMode.TAKEOVER;
    public ChatPanelConfig chatPanel = new ChatPanelConfig();
    public AppearanceConfig appearance = new AppearanceConfig();

    public boolean manualImageReveal;
    public boolean manualAudioReveal;
    public boolean manualVideoReveal;
    public Boolean smoothScrollEnabled;
    public boolean debugChatActions;

    public int maxReceiveBytes = DEFAULT_MAX_RECEIVE_BYTES;
    public int maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;
    public int audioVolumePercent = 100;
    public int videoVolumePercent = 100;
    public UploadMode uploadMode = UploadMode.AUTO;

    public static final class ChatPanelConfig {
        public int left = 4;
        public int bottomOffset = 40;
        public int width = 360;
        public int height = 220;
    }

    public static final class AppearanceConfig {
        public int panelBackgroundColor = 0x12141A;
        public int panelBackgroundOpacityPercent = 90;
        public boolean panelBorderEnabled = true;
        public int panelBorderWidth = 1;
        public int panelBorderColor = 0x526176;

        public boolean vanillaStyleInput;
        public boolean showPlayerAvatars = true;
        public boolean doubleLineLayout = true;

        public boolean messageBubbles;
        public int bubbleColor = 0x2B3547;
        public boolean bubbleBorderEnabled = true;
        public int bubbleBorderWidth = 1;
        public int bubbleBorderColor = 0x5D7598;

        public boolean splitOwnMessages;
        public NonPlayerAlignment nonPlayerAlignment = NonPlayerAlignment.LEFT;
        public int cornerRadius = 4;

        public int contextMenuScalePercent = 100;
        public int contextMenuBackgroundColor = 0x12141A;
        public boolean contextMenuBorderEnabled = true;
        public int contextMenuBorderWidth = 1;
        public int contextMenuBorderColor = 0x526176;
        public int contextMenuCornerRadius = 4;

        public AppearanceConfig copy() {
            return GSON.fromJson(GSON.toJson(this), AppearanceConfig.class);
        }
    }

    public enum NonPlayerAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum UploadMode {
        AUTO,
        SERVER,
        THIRD_PARTY
    }

    public enum ChatInputMode {
        TAKEOVER,
        COMPAT_TEXT_VANILLA
    }

    private static ChatUpgradeConfig defaults() {
        ChatUpgradeConfig config = new ChatUpgradeConfig();
        config.ciCompatibility = false;
        config.manualImageReveal = false;
        config.manualAudioReveal = false;
        config.manualVideoReveal = false;
        config.smoothScrollEnabled = true;
        config.debugChatActions = false;
        config.maxReceiveBytes = DEFAULT_MAX_RECEIVE_BYTES;
        config.maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;
        config.audioVolumePercent = 100;
        config.videoVolumePercent = 100;
        config.uploadMode = UploadMode.AUTO;
        config.chatInputMode = ChatInputMode.TAKEOVER;
        config.chatPanel = new ChatPanelConfig();
        config.appearance = new AppearanceConfig();
        config.normalizeLimits();
        return config;
    }

    public boolean normalizeLimits() {
        int beforeReceive = maxReceiveBytes;
        int beforeUpload = maxUploadBytes;
        int beforeAudioVolume = audioVolumePercent;
        int beforeVideoVolume = videoVolumePercent;
        Boolean beforeSmoothScroll = smoothScrollEnabled;
        UploadMode beforeUploadMode = uploadMode;
        ChatInputMode beforeChatInputMode = chatInputMode;
        ChatPanelConfig beforeChatPanel = chatPanel;
        AppearanceConfig beforeAppearance = appearance;
        String beforeAppearanceJson = appearance == null ? "" : GSON.toJson(appearance);

        maxReceiveBytes = normalizeTransferLimit(maxReceiveBytes, DEFAULT_MAX_RECEIVE_BYTES);
        maxUploadBytes = normalizeTransferLimit(maxUploadBytes, DEFAULT_MAX_UPLOAD_BYTES);
        audioVolumePercent = Math.clamp(audioVolumePercent, 1, 100);
        videoVolumePercent = Math.clamp(videoVolumePercent, 1, 100);
        smoothScrollEnabled = smoothScrollEnabled == null ? true : smoothScrollEnabled;
        uploadMode = uploadMode == null ? UploadMode.AUTO : uploadMode;
        chatInputMode = chatInputMode == null ? ChatInputMode.TAKEOVER : chatInputMode;

        if (chatPanel == null) {
            chatPanel = new ChatPanelConfig();
        }
        chatPanel.left = Math.max(0, chatPanel.left);
        chatPanel.bottomOffset = Math.max(0, chatPanel.bottomOffset);
        chatPanel.width = Math.max(1, chatPanel.width);
        chatPanel.height = Math.max(1, chatPanel.height);

        if (appearance == null) {
            appearance = new AppearanceConfig();
        }
        normalizeAppearance(appearance);

        return beforeReceive != maxReceiveBytes
                || beforeUpload != maxUploadBytes
                || beforeAudioVolume != audioVolumePercent
                || beforeVideoVolume != videoVolumePercent
                || beforeSmoothScroll == null
                || beforeUploadMode != uploadMode
                || beforeChatInputMode != chatInputMode
                || beforeChatPanel != chatPanel
                || beforeAppearance != appearance
                || !beforeAppearanceJson.equals(GSON.toJson(appearance));
    }

    private static int normalizeTransferLimit(int bytes, int fallback) {
        int safe = bytes <= 0 ? fallback : bytes;
        return Math.min(safe, ABSOLUTE_MAX_TRANSFER_BYTES);
    }

    private static void normalizeAppearance(AppearanceConfig value) {
        value.panelBackgroundColor = rgb(value.panelBackgroundColor);
        value.panelBackgroundOpacityPercent = Math.clamp(value.panelBackgroundOpacityPercent, 0, 100);
        value.panelBorderWidth = Math.clamp(value.panelBorderWidth, 1, 4);
        value.panelBorderColor = rgb(value.panelBorderColor);
        value.bubbleColor = rgb(value.bubbleColor);
        value.bubbleBorderWidth = Math.clamp(value.bubbleBorderWidth, 1, 4);
        value.bubbleBorderColor = rgb(value.bubbleBorderColor);
        value.nonPlayerAlignment = value.nonPlayerAlignment == null ? NonPlayerAlignment.LEFT : value.nonPlayerAlignment;
        value.cornerRadius = Math.clamp(value.cornerRadius, 0, 16);
        value.contextMenuScalePercent = Math.clamp(value.contextMenuScalePercent, 75, 150);
        value.contextMenuBackgroundColor = rgb(value.contextMenuBackgroundColor);
        value.contextMenuBorderWidth = Math.clamp(value.contextMenuBorderWidth, 1, 4);
        value.contextMenuBorderColor = rgb(value.contextMenuBorderColor);
        value.contextMenuCornerRadius = Math.clamp(value.contextMenuCornerRadius, 0, 12);
    }

    private static int rgb(int color) {
        return color & 0x00FFFFFF;
    }

    public static ChatUpgradeConfig get() {
        return instance;
    }

    public static ChatUpgradeConfig copyCurrent() {
        synchronized (LOCK) {
            return GSON.fromJson(GSON.toJson(instance), ChatUpgradeConfig.class);
        }
    }

    public static AppearanceConfig defaultAppearance() {
        return new AppearanceConfig();
    }

    public static Path configPath() {
        return Platform.configDir().resolve("chat-upgrade").resolve("chat-upgrade.json");
    }

    public static void load() {
        synchronized (LOCK) {
            Path path = configPath();
            if (!Files.isRegularFile(path)) {
                instance = defaults();
                saveQuiet();
                return;
            }
            try {
                String json = Files.readString(path);
                boolean containsLegacyTheme = json.contains("\"chatTheme\"");
                ChatUpgradeConfig read = GSON.fromJson(json, ChatUpgradeConfig.class);
                if (read == null) {
                    instance = defaults();
                    return;
                }
                boolean corrected = read.normalizeLimits() || containsLegacyTheme;
                instance = read;
                if (corrected) {
                    saveQuiet();
                    com.chat.upgrade.ChatUpgrade.LOGGER.info(
                            "chat-upgrade: normalized client config and removed deprecated fields at {}",
                            path);
                }
            } catch (Exception e) {
                com.chat.upgrade.ChatUpgrade.LOGGER.warn(
                        "chat-upgrade: failed to load config, using defaults: {}",
                        e.getMessage());
                instance = defaults();
            }
        }
    }

    public static void save() throws IOException {
        synchronized (LOCK) {
            instance.normalizeLimits();
            writeConfigFile();
        }
    }

    public static void replaceAndSave(ChatUpgradeConfig next) throws IOException {
        synchronized (LOCK) {
            ChatUpgradeConfig safe = next == null ? defaults() : next;
            safe.normalizeLimits();
            instance = safe;
            writeConfigFile();
        }
    }

    private static void writeConfigFile() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(instance, writer);
        }
    }

    private static void saveQuiet() {
        try {
            writeConfigFile();
        } catch (IOException e) {
            com.chat.upgrade.ChatUpgrade.LOGGER.warn(
                    "chat-upgrade: failed to write default config: {}",
                    e.getMessage());
        }
    }

    public static void setCiCompatibilityAndSave(boolean value) throws IOException {
        updateAndSave(config -> config.ciCompatibility = value);
    }

    public static void setManualImageRevealAndSave(boolean value) throws IOException {
        updateAndSave(config -> config.manualImageReveal = value);
    }

    public static void setManualAudioRevealAndSave(boolean value) throws IOException {
        updateAndSave(config -> config.manualAudioReveal = value);
    }

    public static void setManualVideoRevealAndSave(boolean value) throws IOException {
        updateAndSave(config -> config.manualVideoReveal = value);
    }

    public static void setSmoothScrollEnabledAndSave(boolean value) throws IOException {
        updateAndSave(config -> config.smoothScrollEnabled = value);
    }

    public static void setDebugChatActionsAndSave(boolean value) throws IOException {
        updateAndSave(config -> config.debugChatActions = value);
    }

    public static boolean isSmoothScrollEnabled() {
        Boolean enabled = instance.smoothScrollEnabled;
        return enabled == null || enabled;
    }

    public static void setMaxReceiveBytesAndSave(int bytes) throws IOException {
        updateAndSave(config -> config.maxReceiveBytes = bytes);
    }

    public static void setMaxUploadBytesAndSave(int bytes) throws IOException {
        updateAndSave(config -> config.maxUploadBytes = bytes);
    }

    public static void setAudioVolumePercentAndSave(int percent) throws IOException {
        updateAndSave(config -> config.audioVolumePercent = percent);
    }

    public static void setVideoVolumePercentAndSave(int percent) throws IOException {
        updateAndSave(config -> config.videoVolumePercent = percent);
    }

    public static void setUploadModeAndSave(UploadMode mode) throws IOException {
        updateAndSave(config -> config.uploadMode = mode == null ? UploadMode.AUTO : mode);
    }

    public static void setChatInputModeAndSave(ChatInputMode mode) throws IOException {
        updateAndSave(config -> config.chatInputMode = mode == null ? ChatInputMode.TAKEOVER : mode);
    }

    public static void setAppearanceAndSave(AppearanceConfig nextAppearance) throws IOException {
        updateAndSave(config -> config.appearance = nextAppearance == null
                ? new AppearanceConfig()
                : nextAppearance.copy());
    }

    public static void setChatPanelGeometryAndSave(
            int left,
            int bottomOffset,
            int width,
            int height) throws IOException {
        updateAndSave(config -> {
            if (config.chatPanel == null) {
                config.chatPanel = new ChatPanelConfig();
            }
            config.chatPanel.left = left;
            config.chatPanel.bottomOffset = bottomOffset;
            config.chatPanel.width = width;
            config.chatPanel.height = height;
        });
    }

    private static void updateAndSave(ConfigMutation mutation) throws IOException {
        synchronized (LOCK) {
            mutation.apply(instance);
            instance.normalizeLimits();
            writeConfigFile();
        }
    }

    @FunctionalInterface
    private interface ConfigMutation {
        void apply(ChatUpgradeConfig config);
    }

    public static boolean isCompatTextVanillaInputMode() {
        return instance.chatInputMode == ChatInputMode.COMPAT_TEXT_VANILLA;
    }

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