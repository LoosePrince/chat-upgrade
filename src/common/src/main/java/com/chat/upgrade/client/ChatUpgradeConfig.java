package com.chat.upgrade.client;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
    public static final String DEFAULT_PRIVATE_MESSAGE_COMMAND = "/msg <id> <message>";

    private static volatile ChatUpgradeConfig instance = defaults();

    public boolean ciCompatibility;
    public ChatInputMode chatInputMode = ChatInputMode.TAKEOVER;
    public String chatInputPlaceholder = "";
    public Boolean chatScreenMaskEnabled = false;
    public MentionNotificationMode mentionNotificationMode = MentionNotificationMode.SOUND;
    public Boolean messagePassthroughEnabled = false;
    public Boolean messageGroupingEnabled = false;
    public MessageGroupPosition messageGroupPosition = MessageGroupPosition.LEFT;
    public Boolean chatHistoryEnabled = true;
    public int chatHistoryMaxMessages = 500;
    public String privateMessageCommand = DEFAULT_PRIVATE_MESSAGE_COMMAND;
    public ChatPanelConfig chatPanel = new ChatPanelConfig();
    public AppearanceConfig appearance = new AppearanceConfig();

    public boolean manualImageReveal;
    public boolean manualAudioReveal;
    public boolean manualVideoReveal;
    public boolean compactMediaCards = true;
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
        public Boolean automaticHeight = true;
        public Boolean screenMarginsEnabled = true;

        public boolean usesAutomaticHeight() {
            return automaticHeight == null || automaticHeight;
        }

        public boolean usesScreenMargins() {
            return screenMarginsEnabled == null || screenMarginsEnabled;
        }

        private boolean matchesLegacyDefaults() {
            return left == 4 && bottomOffset == 40 && width == 360 && height == 220;
        }
    }

    public static final class AppearanceConfig {
        public int panelBackgroundColor = 0x000000;
        public int panelBackgroundOpacityPercent;
        public boolean panelBorderEnabled;
        public int panelBorderWidth = 1;
        public int panelBorderColor = 0x526176;

        public boolean vanillaStyleInput = true;
        public boolean showPlayerAvatars;
        public boolean avatarFirstLineOnly;
        public boolean doubleLineLayout;

        public int messageBackgroundColor = 0x000000;
        public int messageBackgroundOpacityPercent = 100;
        public boolean messageBubbles;
        public int bubblePadding = 3;
        public int bubbleColor = 0x2B3547;
        public boolean bubbleBorderEnabled = true;
        public int bubbleBorderWidth = 1;
        public int bubbleBorderColor = 0x5D7598;

        public boolean splitOwnMessages;
        public NonPlayerAlignment nonPlayerAlignment = NonPlayerAlignment.LEFT;
        public int cornerRadius;
        public int messageGap;
        public int groupGap;

        public int contextMenuScalePercent = 100;
        public int contextMenuBackgroundColor = 0x12141A;
        public boolean contextMenuBorderEnabled = true;
        public int contextMenuBorderWidth = 1;
        public int contextMenuBorderColor = 0x526176;
        public int contextMenuCornerRadius = 4;

        public AppearanceConfig copy() {
            return GSON.fromJson(GSON.toJson(this), AppearanceConfig.class);
        }

        private boolean matchesLegacyDefaults() {
            return panelBackgroundColor == 0x12141A
                    && panelBackgroundOpacityPercent == 90
                    && panelBorderEnabled
                    && panelBorderWidth == 1
                    && panelBorderColor == 0x526176
                    && !vanillaStyleInput
                    && showPlayerAvatars
                    && !avatarFirstLineOnly
                    && doubleLineLayout
                    && !messageBubbles
                    && bubblePadding == 3
                    && bubbleColor == 0x2B3547
                    && bubbleBorderEnabled
                    && bubbleBorderWidth == 1
                    && bubbleBorderColor == 0x5D7598
                    && !splitOwnMessages
                    && nonPlayerAlignment == NonPlayerAlignment.LEFT
                    && cornerRadius == 4
                    && contextMenuScalePercent == 100
                    && contextMenuBackgroundColor == 0x12141A
                    && contextMenuBorderEnabled
                    && contextMenuBorderWidth == 1
                    && contextMenuBorderColor == 0x526176
                    && contextMenuCornerRadius == 4;
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

    public enum MentionNotificationMode {
        NONE,
        SOUND,
        TITLE
    }

    public enum MessageGroupPosition {
        LEFT,
        RIGHT
    }

    private static ChatUpgradeConfig defaults() {
        ChatUpgradeConfig config = new ChatUpgradeConfig();
        config.ciCompatibility = false;
        config.manualImageReveal = false;
        config.manualAudioReveal = false;
        config.manualVideoReveal = false;
        config.compactMediaCards = true;
        config.smoothScrollEnabled = true;
        config.debugChatActions = false;
        config.maxReceiveBytes = DEFAULT_MAX_RECEIVE_BYTES;
        config.maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;
        config.audioVolumePercent = 100;
        config.videoVolumePercent = 100;
        config.uploadMode = UploadMode.AUTO;
        config.chatInputMode = ChatInputMode.TAKEOVER;
        config.chatInputPlaceholder = "";
        config.chatScreenMaskEnabled = false;
        config.mentionNotificationMode = MentionNotificationMode.SOUND;
        config.messagePassthroughEnabled = false;
        config.messageGroupingEnabled = false;
        config.chatHistoryEnabled = true;
        config.chatHistoryMaxMessages = 500;
        config.messageGroupPosition = MessageGroupPosition.LEFT;
        config.privateMessageCommand = DEFAULT_PRIVATE_MESSAGE_COMMAND;
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
        String beforeChatInputPlaceholder = chatInputPlaceholder;
        Boolean beforeChatScreenMaskEnabled = chatScreenMaskEnabled;
        MentionNotificationMode beforeMentionNotificationMode = mentionNotificationMode;
        Boolean beforeMessagePassthroughEnabled = messagePassthroughEnabled;
        Boolean beforeMessageGroupingEnabled = messageGroupingEnabled;
        Boolean beforeChatHistoryEnabled = chatHistoryEnabled;
        int beforeChatHistoryMaxMessages = chatHistoryMaxMessages;
        MessageGroupPosition beforeMessageGroupPosition = messageGroupPosition;
        String beforePrivateMessageCommand = privateMessageCommand;
        ChatPanelConfig beforeChatPanel = chatPanel;
        Boolean beforeScreenMarginsEnabled = chatPanel == null ? null : chatPanel.screenMarginsEnabled;
        AppearanceConfig beforeAppearance = appearance;
        String beforeChatPanelJson = chatPanel == null ? "" : GSON.toJson(chatPanel);
        String beforeAppearanceJson = appearance == null ? "" : GSON.toJson(appearance);

        maxReceiveBytes = normalizeTransferLimit(maxReceiveBytes, DEFAULT_MAX_RECEIVE_BYTES);
        maxUploadBytes = normalizeTransferLimit(maxUploadBytes, DEFAULT_MAX_UPLOAD_BYTES);
        audioVolumePercent = Math.clamp(audioVolumePercent, 1, 100);
        videoVolumePercent = Math.clamp(videoVolumePercent, 1, 100);
        smoothScrollEnabled = smoothScrollEnabled == null ? true : smoothScrollEnabled;
        uploadMode = uploadMode == null ? UploadMode.AUTO : uploadMode;
        chatInputMode = chatInputMode == null ? ChatInputMode.TAKEOVER : chatInputMode;
        chatInputPlaceholder = chatInputPlaceholder == null ? "" : chatInputPlaceholder;
        chatScreenMaskEnabled = chatScreenMaskEnabled == null ? false : chatScreenMaskEnabled;
        mentionNotificationMode = mentionNotificationMode == null
                ? MentionNotificationMode.SOUND
                : mentionNotificationMode;
        messagePassthroughEnabled = messagePassthroughEnabled == null ? false : messagePassthroughEnabled;
        messageGroupingEnabled = messageGroupingEnabled == null ? false : messageGroupingEnabled;
        chatHistoryEnabled = chatHistoryEnabled == null ? true : chatHistoryEnabled;
        chatHistoryMaxMessages = Math.clamp(chatHistoryMaxMessages, 10, 500);
        messageGroupPosition = messageGroupPosition == null ? MessageGroupPosition.LEFT : messageGroupPosition;
        privateMessageCommand = normalizePrivateMessageCommand(privateMessageCommand);

        if (chatPanel == null) {
            chatPanel = new ChatPanelConfig();
        }
        chatPanel.left = Math.max(0, chatPanel.left);
        chatPanel.bottomOffset = Math.max(0, chatPanel.bottomOffset);
        chatPanel.width = Math.max(1, chatPanel.width);
        chatPanel.height = Math.max(1, chatPanel.height);
        chatPanel.automaticHeight = chatPanel.automaticHeight == null ? true : chatPanel.automaticHeight;
        chatPanel.screenMarginsEnabled = chatPanel.screenMarginsEnabled == null ? true : chatPanel.screenMarginsEnabled;

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
                || beforeChatInputPlaceholder == null
                || !beforeChatInputPlaceholder.equals(chatInputPlaceholder)
                || beforeChatScreenMaskEnabled == null
                || beforeChatScreenMaskEnabled != chatScreenMaskEnabled
                || beforeMentionNotificationMode != mentionNotificationMode
                || beforeMessagePassthroughEnabled == null
                || beforeMessagePassthroughEnabled != messagePassthroughEnabled
                || beforeMessageGroupingEnabled == null
                || beforeMessageGroupingEnabled != messageGroupingEnabled
                || beforeChatHistoryEnabled == null
                || beforeChatHistoryEnabled != chatHistoryEnabled
                || beforeChatHistoryMaxMessages != chatHistoryMaxMessages
                || beforeMessageGroupPosition != messageGroupPosition
                || beforePrivateMessageCommand == null
                || !beforePrivateMessageCommand.equals(privateMessageCommand)
                || beforeChatPanel != chatPanel
                || beforeScreenMarginsEnabled == null
                || !beforeChatPanelJson.equals(GSON.toJson(chatPanel))
                || beforeAppearance != appearance
                || !beforeAppearanceJson.equals(GSON.toJson(appearance));
    }

    private static int normalizeTransferLimit(int bytes, int fallback) {
        int safe = bytes <= 0 ? fallback : bytes;
        return Math.min(safe, ABSOLUTE_MAX_TRANSFER_BYTES);
    }

    public static boolean validPrivateMessageCommand(String value) {
        String normalized = value == null ? "" : value.trim();
        int idIndex = normalized.indexOf("<id>");
        int uuidIndex = normalized.indexOf("<uuid>");
        int messageIndex = normalized.indexOf("<message>");
        boolean hasExactlyOneTarget = (idIndex >= 0) != (uuidIndex >= 0);
        int targetIndex = idIndex >= 0 ? idIndex : uuidIndex;
        return hasExactlyOneTarget && messageIndex > targetIndex;
    }

    private static String normalizePrivateMessageCommand(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!validPrivateMessageCommand(normalized)) {
            return DEFAULT_PRIVATE_MESSAGE_COMMAND;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static void normalizeAppearance(AppearanceConfig value) {
        value.panelBackgroundColor = rgb(value.panelBackgroundColor);
        value.panelBackgroundOpacityPercent = Math.clamp(value.panelBackgroundOpacityPercent, 0, 100);
        value.panelBorderWidth = Math.clamp(value.panelBorderWidth, 1, 4);
        value.panelBorderColor = rgb(value.panelBorderColor);
        value.messageBackgroundColor = rgb(value.messageBackgroundColor);
        value.messageBackgroundOpacityPercent = Math.clamp(value.messageBackgroundOpacityPercent, 0, 100);
        value.bubblePadding = Math.clamp(value.bubblePadding, 0, 16);
        value.bubbleColor = rgb(value.bubbleColor);
        value.bubbleBorderWidth = Math.clamp(value.bubbleBorderWidth, 1, 4);
        value.bubbleBorderColor = rgb(value.bubbleBorderColor);
        value.nonPlayerAlignment = value.nonPlayerAlignment == null ? NonPlayerAlignment.LEFT : value.nonPlayerAlignment;
        value.cornerRadius = Math.clamp(value.cornerRadius, 0, 16);
        value.messageGap = Math.clamp(value.messageGap, 0, 16);
        value.groupGap = Math.clamp(value.groupGap, 0, 16);
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
            return copyOf(instance);
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
                boolean containsAutomaticHeight = json.contains("\"automaticHeight\"");
                boolean containsScreenMarginsEnabled = json.contains("\"screenMarginsEnabled\"");
                boolean containsMessageBackground = json.contains("\"messageBackgroundColor\"");
                boolean containsAvatarFirstLineOnly = json.contains("\"avatarFirstLineOnly\"");
                boolean containsBubblePadding = json.contains("\"bubblePadding\"");
                boolean containsChatInputPlaceholder = json.contains("\"chatInputPlaceholder\"");
                boolean containsChatScreenMask = json.contains("\"chatScreenMaskEnabled\"");
                boolean containsMentionNotificationMode = json.contains("\"mentionNotificationMode\"");
                boolean containsMessagePassthrough = json.contains("\"messagePassthroughEnabled\"");
                boolean containsMessageGrouping = json.contains("\"messageGroupingEnabled\"");
                boolean containsChatHistory = json.contains("\"chatHistoryEnabled\"");
                boolean containsChatHistoryMaxMessages = json.contains("\"chatHistoryMaxMessages\"");
                boolean containsMessageGroupPosition = json.contains("\"messageGroupPosition\"");
                boolean containsPrivateMessageCommand = json.contains("\"privateMessageCommand\"");
                boolean containsCompactMediaCards = json.contains("\"compactMediaCards\"");
                ChatUpgradeConfig read = GSON.fromJson(json, ChatUpgradeConfig.class);
                if (read == null) {
                    instance = defaults();
                    return;
                }
                boolean migratedLegacyDefaults = false;
                if (!containsMentionNotificationMode) {
                    read.mentionNotificationMode = MentionNotificationMode.SOUND;
                    migratedLegacyDefaults = true;
                }
                if (!containsMessagePassthrough) {
                    read.messagePassthroughEnabled = false;
                    migratedLegacyDefaults = true;
                }
                if (!containsMessageGrouping) {
                    read.messageGroupingEnabled = false;
                    migratedLegacyDefaults = true;
                }
                if (!containsChatHistory) {
                    read.chatHistoryEnabled = true;
                    migratedLegacyDefaults = true;
                }
                if (!containsChatHistoryMaxMessages) {
                    read.chatHistoryMaxMessages = 500;
                    migratedLegacyDefaults = true;
                }
                if (!containsMessageGroupPosition) {
                    read.messageGroupPosition = MessageGroupPosition.LEFT;
                    migratedLegacyDefaults = true;
                }
                if (!containsPrivateMessageCommand) {
                    read.privateMessageCommand = DEFAULT_PRIVATE_MESSAGE_COMMAND;
                    migratedLegacyDefaults = true;
                }
                if (!containsCompactMediaCards) {
                    read.compactMediaCards = true;
                    migratedLegacyDefaults = true;
                }
                if (!containsAutomaticHeight && read.chatPanel != null) {
                    read.chatPanel.automaticHeight = read.chatPanel.matchesLegacyDefaults();
                    migratedLegacyDefaults = true;
                }
                if (!containsScreenMarginsEnabled && read.chatPanel != null) {
                    read.chatPanel.screenMarginsEnabled = true;
                    migratedLegacyDefaults = true;
                }
                if (!containsMessageBackground && read.appearance != null && read.appearance.matchesLegacyDefaults()) {
                    read.appearance = new AppearanceConfig();
                    migratedLegacyDefaults = true;
                }
                if (!containsBubblePadding && read.appearance != null) {
                    read.appearance.bubblePadding = 3;
                }
                boolean corrected = read.normalizeLimits()
                        || containsLegacyTheme
                        || !containsAutomaticHeight
                        || !containsScreenMarginsEnabled
                        || !containsMessageBackground
                        || !containsAvatarFirstLineOnly
                        || !containsBubblePadding
                        || !containsChatInputPlaceholder
                        || !containsChatScreenMask
                        || !containsMentionNotificationMode
                        || !containsMessagePassthrough
                        || !containsMessageGrouping
                        || !containsChatHistory
                        || !containsChatHistoryMaxMessages
                        || !containsMessageGroupPosition
                        || !containsPrivateMessageCommand
                        || !containsCompactMediaCards
                        || migratedLegacyDefaults;
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
            ChatUpgradeConfig candidate = copyOf(instance);
            candidate.normalizeLimits();
            writeConfigFile(candidate);
            instance = candidate;
        }
    }

    public static void replaceAndSave(ChatUpgradeConfig next) throws IOException {
        synchronized (LOCK) {
            ChatUpgradeConfig candidate = next == null ? defaults() : copyOf(next);
            candidate.normalizeLimits();
            writeConfigFile(candidate);
            instance = candidate;
        }
    }

    private static ChatUpgradeConfig copyOf(ChatUpgradeConfig source) {
        return GSON.fromJson(GSON.toJson(source), ChatUpgradeConfig.class);
    }

    private static void writeConfigFile(ChatUpgradeConfig config) throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void saveQuiet() {
        try {
            writeConfigFile(instance);
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

    public boolean usesChatScreenMask() {
        return chatScreenMaskEnabled != null && chatScreenMaskEnabled;
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
            int height,
            boolean automaticHeight) throws IOException {
        updateAndSave(config -> {
            if (config.chatPanel == null) {
                config.chatPanel = new ChatPanelConfig();
            }
            config.chatPanel.left = left;
            config.chatPanel.bottomOffset = bottomOffset;
            config.chatPanel.width = width;
            config.chatPanel.height = height;
            config.chatPanel.automaticHeight = automaticHeight;
        });
    }

    public static void setChatPanelWidthAndSave(int width) throws IOException {
        updateAndSave(config -> {
            if (config.chatPanel == null) {
                config.chatPanel = new ChatPanelConfig();
            }
            config.chatPanel.width = width;
        });
    }

    private static void updateAndSave(ConfigMutation mutation) throws IOException {
        synchronized (LOCK) {
            ChatUpgradeConfig candidate = copyOf(instance);
            mutation.apply(candidate);
            candidate.normalizeLimits();
            writeConfigFile(candidate);
            instance = candidate;
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
