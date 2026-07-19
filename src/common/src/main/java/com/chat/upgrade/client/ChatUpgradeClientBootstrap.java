package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.emoji.TwikooOwoRegistry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.plugin.ExternalImageIoPluginLoader;
import com.chat.upgrade.client.plugin.FfmpegNativeBootstrap;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.UpgradePhantomCoordinator;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureArena;
import com.chat.upgrade.client.ui.chat.interaction.ChatMessageVisibilityStore;
import com.chat.upgrade.client.ui.chat.state.RichChatIngress;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionCoordinator;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.Minecraft;

/**
 * Loader-agnostic client bootstrap and lifecycle hooks. Each loader's client entry point calls
 * {@link #init()} once and wires the lifecycle hooks to its native tick/lifecycle events.
 */
public final class ChatUpgradeClientBootstrap {
    private static int lastGuiScaledWidth = -1;
    private static int lastGuiScaledHeight = -1;
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;

    private ChatUpgradeClientBootstrap() {
    }

    public static void init() {
        System.setProperty("java.awt.headless", "false");
        ExternalImageIoPluginLoader.loadAtStartup();
        ChatUpgradeConfig.load();
        ChatUpgrade.LOGGER.info(
                "chat-upgrade: loaded config from {} | maxReceive={} maxUpload={} manual(image/audio/video)={}/{}/{} volume(audio/video)={}/{}",
                ChatUpgradeConfig.configPath(),
                ChatUpgradeConfig.get().maxReceiveBytes,
                ChatUpgradeConfig.get().maxUploadBytes,
                ChatUpgradeConfig.get().manualImageReveal,
                ChatUpgradeConfig.get().manualAudioReveal,
                ChatUpgradeConfig.get().manualVideoReveal,
                ChatUpgradeConfig.get().audioVolumePercent,
                ChatUpgradeConfig.get().videoVolumePercent);
        FfmpegNativeBootstrap.warmupAsync();
        ChatClientConfigRuntime.initializeLoadedConfig();
        TwikooOwoRegistry.refreshIfExpired();
    }

    /** Invalidate cached HUD textures when the window / GUI scale changes. */
    public static void onClientTick(Minecraft client) {
        var w = client.getWindow();
        int sw = w.getGuiScaledWidth();
        int sh = w.getGuiScaledHeight();
        int fw = w.getWidth();
        int fh = w.getHeight();
        if (sw != lastGuiScaledWidth
                || sh != lastGuiScaledHeight
                || fw != lastFramebufferWidth
                || fh != lastFramebufferHeight) {
            lastGuiScaledWidth = sw;
            lastGuiScaledHeight = sh;
            lastFramebufferWidth = fw;
            lastFramebufferHeight = fh;
            ImageLoader.invalidateTextureCache();
            VideoLoader.invalidateVideoCache();
            UiTextureAtlas.invalidate();
        }
    }

    public static void clearAllRuntimeState() {
        clearAllChatRuntimeState();
        clearAllMediaRuntimeState();
    }

    public static void clearAllChatRuntimeState() {
        RichChatIngress.clear();
        RichChatProjectionCoordinator.clear();
        RichChatInteractionRouter.clear();
        ChatMessageVisibilityStore.clearSession();
        ChatTextSelectionState.clear();
        RichChatViewport.invalidateAll();
        RichChatViewport.state().clear();
        ChatUpgradeChatRenderState.cancelWheelOverscroll();
        UpgradePhantomCoordinator.clear();
        ChatSurfaceController.onChatScreenClosed();
        ChatGestureArena.resetPointerState();
    }

    public static void clearAllMediaRuntimeState() {
        AudioLoader.invalidateAudioCache();
        VideoLoader.invalidateVideoCache();
        ImageLoader.invalidateTextureCache();
        UiTextureAtlas.invalidate();
        AudioFloatingWindow.clear();
        ServerMediaClient.clearRuntimeState();
        InlineEmojiCoordinator.clearPendingSlots();
        TwikooOwoRegistry.clearRuntimeState();
    }
}
