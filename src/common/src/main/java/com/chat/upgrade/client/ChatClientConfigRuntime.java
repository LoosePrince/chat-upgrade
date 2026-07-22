package com.chat.upgrade.client;

import java.io.IOException;
import java.util.function.Consumer;

import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatUiPreferences;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;

/** Single entry point for loading, previewing, saving, and applying client configuration. */
public final class ChatClientConfigRuntime {
    private static volatile ChatUiPreferences uiPreferences = ChatUiPreferences.from(ChatUpgradeConfig.get());

    private ChatClientConfigRuntime() {
    }

    public static ChatUiPreferences uiPreferences() {
        return uiPreferences;
    }

    public static void initializeLoadedConfig() {
        applyRuntime(ChatUpgradeConfig.get());
    }

    public static ChatUpgradeConfig draft() {
        return ChatUpgradeConfig.copyCurrent();
    }

    public static void preview(ChatUpgradeConfig draft) {
        if (draft == null) {
            cancelPreview();
            return;
        }
        draft.normalizeLimits();
        uiPreferences = ChatUiPreferences.from(draft);
        AudioPlayerService.setGlobalVolumePercent(draft.audioVolumePercent);
        VideoPlayerService.setGlobalVolumePercent(draft.videoVolumePercent);
        ChatAppearanceRuntime.preview(draft);
        RichChatViewport.invalidateAll();
    }

    public static void cancelPreview() {
        restorePreviewBaseline(ChatUpgradeConfig.get());
    }

    public static void restorePreviewBaseline(ChatUpgradeConfig baseline) {
        ChatUpgradeConfig restore = baseline == null ? ChatUpgradeConfig.get() : baseline;
        restore.normalizeLimits();
        uiPreferences = ChatUiPreferences.from(restore);
        AudioPlayerService.setGlobalVolumePercent(restore.audioVolumePercent);
        VideoPlayerService.setGlobalVolumePercent(restore.videoVolumePercent);
        ChatAppearanceRuntime.commit(restore);
        RichChatViewport.invalidateAll();
    }

    public static void save(ChatUpgradeConfig draft) throws IOException {
        ChatUpgradeConfig.replaceAndSave(draft);
        applyRuntime(ChatUpgradeConfig.get());
    }

    public static void updateAndSave(Consumer<ChatUpgradeConfig> mutation) throws IOException {
        ChatUpgradeConfig next = ChatUpgradeConfig.copyCurrent();
        mutation.accept(next);
        save(next);
    }

    public static void reload() {
        ChatUpgradeConfig.load();
        applyRuntime(ChatUpgradeConfig.get());
    }

    private static void applyRuntime(ChatUpgradeConfig config) {
        config.normalizeLimits();
        uiPreferences = ChatUiPreferences.from(config);
        AudioPlayerService.setGlobalVolumePercent(config.audioVolumePercent);
        VideoPlayerService.setGlobalVolumePercent(config.videoVolumePercent);
        ChatAppearanceRuntime.commit(config);
        RichChatViewport.invalidateAll();
    }
}