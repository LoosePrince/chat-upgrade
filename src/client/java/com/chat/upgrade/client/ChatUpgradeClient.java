package com.chat.upgrade.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.upload.UploadRouter;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.net.servermedia.ServerMediaNetworking;
import com.chat.upgrade.client.plugin.ExternalImageIoPluginLoader;
import com.chat.upgrade.client.plugin.FfmpegNativeBootstrap;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.upload.LocalImageSources;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class ChatUpgradeClient implements ClientModInitializer {
    private static int lastGuiScaledWidth = -1;
    private static int lastGuiScaledHeight = -1;
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;

    @Override
    public void onInitializeClient() {
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
        AudioPlayerService.setGlobalVolumePercent(ChatUpgradeConfig.get().audioVolumePercent);
        VideoPlayerService.setGlobalVolumePercent(ChatUpgradeConfig.get().videoVolumePercent);
        registerCommands();
        registerHudTextureInvalidationOnResize();
        registerMediaCleanupOnDisconnect();
        ServerMediaNetworking.initClient();
    }

    private static void registerHudTextureInvalidationOnResize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
            }
        });
    }

    private static void registerMediaCleanupOnDisconnect() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAllMediaRuntimeState());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clearAllMediaRuntimeState());
    }

    private static void clearAllMediaRuntimeState() {
        AudioLoader.invalidateAudioCache();
        VideoLoader.invalidateVideoCache();
        ImageLoader.invalidateTextureCache();
        AudioFloatingWindow.clear();
        ServerMediaClient.clearRuntimeState();
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("chatupgrade")
                        .then(ClientCommands.literal("send")
                                .then(ClientCommands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> sendImageUrl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                Component.translatable("chatupgrade.type.image").getString()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> sendImageUrl(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "url"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(ClientCommands.literal("sendaudio")
                                .then(ClientCommands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> sendAudioUrl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                Component.translatable("chatupgrade.type.audio").getString()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> sendAudioUrl(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "url"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(ClientCommands.literal("sendvideo")
                                .then(ClientCommands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> sendVideoUrl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                Component.translatable("chatupgrade.type.video").getString()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> sendVideoUrl(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "url"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(ClientCommands.literal("upload")
                                .then(ClientCommands.literal("folder")
                                        .then(ClientCommands.argument("path", StringArgumentType.string())
                                                .executes(ctx -> uploadFromFolderPath(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "path"),
                                                        Optional.empty()))
                                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> uploadFromFolderPath(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "path"),
                                                                Optional.of(
                                                                        StringArgumentType.getString(ctx, "name")))))))
                                .then(ClientCommands.literal("pick")
                                        .executes(ctx -> uploadViaFilePicker(ctx.getSource(), Optional.empty()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> uploadViaFilePicker(
                                                        ctx.getSource(),
                                                        Optional.of(StringArgumentType.getString(ctx, "name"))))))
                                .then(ClientCommands.literal("paste")
                                        .executes(ctx -> uploadFromClipboard(ctx.getSource(), Optional.empty()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> uploadFromClipboard(
                                                        ctx.getSource(),
                                                        Optional.of(StringArgumentType.getString(ctx, "name")))))))
                        .then(ClientCommands.literal("uploadaudio")
                                .then(ClientCommands.literal("folder")
                                        .then(ClientCommands.argument("path", StringArgumentType.string())
                                                .executes(ctx -> uploadAudioFromFolderPath(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "path"),
                                                        Optional.empty()))
                                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> uploadAudioFromFolderPath(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "path"),
                                                                Optional.of(
                                                                        StringArgumentType.getString(ctx, "name")))))))
                                .then(ClientCommands.literal("pick")
                                        .executes(ctx -> uploadAudioViaFilePicker(ctx.getSource(), Optional.empty()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> uploadAudioViaFilePicker(
                                                        ctx.getSource(),
                                                        Optional.of(StringArgumentType.getString(ctx, "name")))))))
                        .then(ClientCommands.literal("uploadvideo")
                                .then(ClientCommands.literal("folder")
                                        .then(ClientCommands.argument("path", StringArgumentType.string())
                                                .executes(ctx -> uploadVideoFromFolderPath(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "path"),
                                                        Optional.empty()))
                                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> uploadVideoFromFolderPath(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "path"),
                                                                Optional.of(
                                                                        StringArgumentType.getString(ctx, "name")))))))
                                .then(ClientCommands.literal("pick")
                                        .executes(ctx -> uploadVideoViaFilePicker(ctx.getSource(), Optional.empty()))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> uploadVideoViaFilePicker(
                                                        ctx.getSource(),
                                                        Optional.of(StringArgumentType.getString(ctx, "name")))))))
                        .then(ClientCommands.literal("config")
                                .then(ClientCommands.literal("uploadmode")
                                        .then(ClientCommands.argument("mode", StringArgumentType.word())
                                                .executes(ctx -> setUploadMode(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "mode")))))
                                .then(ClientCommands.literal("ci")
                                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setCiCompatibility(
                                                        ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(ClientCommands.literal("manual")
                                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setManualImageReveal(
                                                        ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(ClientCommands.literal("manualaudio")
                                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setManualAudioReveal(
                                                        ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(ClientCommands.literal("manualvideo")
                                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setManualVideoReveal(
                                                        ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(ClientCommands.literal("smoothscroll")
                                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setSmoothScrollEnabled(
                                                        ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(ClientCommands.literal("reload")
                                        .executes(ctx -> reloadConfig(ctx.getSource())))
                                .then(ClientCommands.literal("audiovolume")
                                        .then(ClientCommands.argument("percent", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> setAudioVolumePercent(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "percent")))))
                                .then(ClientCommands.literal("videovolume")
                                        .then(ClientCommands.argument("percent", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> setVideoVolumePercent(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "percent")))))
                                .then(ClientCommands.literal("maxreceive")
                                        .then(ClientCommands.argument(
                                                "mebibytes",
                                                IntegerArgumentType.integer(
                                                        1,
                                                        ChatUpgradeConfig.ABSOLUTE_MAX_UPLOAD_BYTES / (1024 * 1024)))
                                                .executes(ctx -> setMaxReceiveMebibytes(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "mebibytes")))))
                                .then(ClientCommands.literal("maxupload")
                                        .then(ClientCommands.argument(
                                                "mebibytes",
                                                IntegerArgumentType.integer(
                                                        1,
                                                        ChatUpgradeConfig.ABSOLUTE_MAX_UPLOAD_BYTES / (1024 * 1024)))
                                                .executes(ctx -> setMaxUploadMebibytes(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "mebibytes")))))
                                .then(ClientCommands.literal("plugin")
                                        .then(ClientCommands.literal("status")
                                                .executes(ctx -> pluginStatus(ctx.getSource())))
                                        .then(ClientCommands.literal("load")
                                                .then(ClientCommands.literal("ffmpeg")
                                                        .executes(ctx -> pluginLoadFfmpeg(ctx.getSource(), false)))
                                                .then(ClientCommands.literal("apng")
                                                        .executes(ctx -> pluginLoadApng(ctx.getSource(), false)))
                                                .then(ClientCommands.literal("all")
                                                        .executes(ctx -> pluginLoadAll(ctx.getSource(), false))))
                                        .then(ClientCommands.literal("download")
                                                .then(ClientCommands.literal("ffmpeg")
                                                        .executes(ctx -> pluginLoadFfmpeg(ctx.getSource(), true)))
                                                .then(ClientCommands.literal("apng")
                                                        .executes(ctx -> pluginLoadApng(ctx.getSource(), true)))
                                                .then(ClientCommands.literal("all")
                                                        .executes(ctx -> pluginLoadAll(ctx.getSource(), true))))))));
    }

    private static int pluginStatus(FabricClientCommandSource source) {
        FfmpegNativeBootstrap.Status ff = FfmpegNativeBootstrap.status();
        String ffState = ff.ready()
                ? Component.translatable("chatupgrade.plugin.status.ready").getString()
                : (ff.attempted()
                        ? Component.translatable("chatupgrade.plugin.status.attempted_not_ready").getString()
                        : Component.translatable("chatupgrade.plugin.status.not_attempted").getString());
        String ffJars = Component.translatable("chatupgrade.plugin.status.ffmpeg.jars",
                ff.javacppPresent()
                        ? Component.translatable("chatupgrade.common.present")
                        : Component.translatable("chatupgrade.common.missing"),
                ff.ffmpegPresent()
                        ? Component.translatable("chatupgrade.common.present")
                        : Component.translatable("chatupgrade.common.missing")).getString();
        boolean apngLoaded = ExternalImageIoPluginLoader.isLoaded();
        boolean apngJar = ExternalImageIoPluginLoader.hasApngJar();
        source.sendFeedback(Component.translatable(
                "chatupgrade.plugin.status.ffmpeg",
                ffState,
                ff.platform(),
                ffJars)
                .withStyle(ChatFormatting.AQUA));
        source.sendFeedback(Component.translatable(
                "chatupgrade.plugin.status.apng",
                apngLoaded
                        ? Component.translatable("chatupgrade.common.loaded")
                        : Component.translatable("chatupgrade.common.not_loaded"),
                apngJar
                        ? Component.translatable("chatupgrade.common.present")
                        : Component.translatable("chatupgrade.common.missing"))
                .withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int pluginLoadFfmpeg(FabricClientCommandSource source, boolean forceDownload) {
        source.sendFeedback(Component.translatable(
                forceDownload ? "chatupgrade.plugin.ffmpeg.loading_force" : "chatupgrade.plugin.ffmpeg.loading")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            boolean ok = FfmpegNativeBootstrap.reload(forceDownload);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (ok) {
                        source.sendFeedback(Component.translatable("chatupgrade.plugin.ffmpeg.ready").withStyle(ChatFormatting.GREEN));
                    } else {
                        source.sendError(Component.translatable("chatupgrade.plugin.ffmpeg.not_ready")
                                .withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
        return 1;
    }

    private static int pluginLoadApng(FabricClientCommandSource source, boolean forceDownload) {
        source.sendFeedback(Component.translatable(
                forceDownload ? "chatupgrade.plugin.apng.loading_force" : "chatupgrade.plugin.apng.loading")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            ExternalImageIoPluginLoader.reload(forceDownload);
            boolean ok = ExternalImageIoPluginLoader.hasApngJar();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (ok) {
                        source.sendFeedback(Component.translatable("chatupgrade.plugin.apng.done")
                                .withStyle(ChatFormatting.GREEN));
                    } else {
                        source.sendError(Component.translatable("chatupgrade.plugin.apng.failed")
                                .withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
        return 1;
    }

    private static int pluginLoadAll(FabricClientCommandSource source, boolean forceDownload) {
        source.sendFeedback(Component.translatable(
                forceDownload ? "chatupgrade.plugin.all.loading_force" : "chatupgrade.plugin.all.loading")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            boolean ffOk = FfmpegNativeBootstrap.reload(forceDownload);
            ExternalImageIoPluginLoader.reload(forceDownload);
            boolean apngOk = ExternalImageIoPluginLoader.hasApngJar();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    source.sendFeedback(Component.translatable(
                            "chatupgrade.plugin.all.result",
                            ffOk
                                    ? Component.translatable("chatupgrade.plugin.status.ready")
                                    : Component.translatable("chatupgrade.plugin.status.not_ready"),
                            apngOk
                                    ? Component.translatable("chatupgrade.common.done")
                                    : Component.translatable("chatupgrade.common.failed"))
                            .withStyle((ffOk && apngOk) ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                });
            }
        });
        return 1;
    }

    private static int setCiCompatibility(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setCiCompatibilityAndSave(enabled);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.ci.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setUploadMode(FabricClientCommandSource source, String modeRaw) {
        ChatUpgradeConfig.UploadMode mode = parseUploadMode(modeRaw);
        if (mode == null) {
            source.sendError(Component.translatable("chatupgrade.config.upload_mode.invalid", modeRaw)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            ChatUpgradeConfig.setUploadModeAndSave(mode);
            source.sendFeedback(Component.translatable("chatupgrade.config.upload_mode.updated", mode.name())
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static @Nullable ChatUpgradeConfig.UploadMode parseUploadMode(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "auto" -> ChatUpgradeConfig.UploadMode.AUTO;
            case "server" -> ChatUpgradeConfig.UploadMode.SERVER;
            case "third", "third_party", "thirdparty", "litterbox", "catbox" ->
                ChatUpgradeConfig.UploadMode.THIRD_PARTY;
            default -> null;
        };
    }

    private static int reloadConfig(FabricClientCommandSource source) {
        ChatUpgradeConfig.load();
        ChatUpgradeConfig cfg = ChatUpgradeConfig.get();
        boolean ci = cfg.ciCompatibility;
        boolean manual = cfg.manualImageReveal;
        boolean manualAudio = cfg.manualAudioReveal;
        boolean manualVideo = cfg.manualVideoReveal;
        AudioPlayerService.setGlobalVolumePercent(cfg.audioVolumePercent);
        VideoPlayerService.setGlobalVolumePercent(cfg.videoVolumePercent);
        source.sendFeedback(Component.translatable(
                "chatupgrade.config.reload.done",
                ci ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                manual ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                manualAudio ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                manualVideo ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                cfg.audioVolumePercent,
                cfg.videoVolumePercent,
                ChatUpgradeConfig.formatBytesHuman(cfg.maxReceiveBytes),
                ChatUpgradeConfig.formatBytesHuman(cfg.maxUploadBytes))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setMaxReceiveMebibytes(FabricClientCommandSource source, int mebibytes) {
        try {
            int bytes = Math.multiplyExact(mebibytes, 1024 * 1024);
            ChatUpgradeConfig.setMaxReceiveBytesAndSave(bytes);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.max_receive.updated",
                    mebibytes,
                    ChatUpgradeConfig.formatBytesHuman(bytes))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (ArithmeticException e) {
            source.sendError(Component.translatable("chatupgrade.error.value_too_large").withStyle(ChatFormatting.RED));
            return 0;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setMaxUploadMebibytes(FabricClientCommandSource source, int mebibytes) {
        try {
            int bytes = Math.multiplyExact(mebibytes, 1024 * 1024);
            ChatUpgradeConfig.setMaxUploadBytesAndSave(bytes);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.max_upload.updated",
                    mebibytes,
                    ChatUpgradeConfig.formatBytesHuman(bytes))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (ArithmeticException e) {
            source.sendError(Component.translatable("chatupgrade.error.value_too_large").withStyle(ChatFormatting.RED));
            return 0;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualImageReveal(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualImageRevealAndSave(enabled);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.manual_image.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualAudioReveal(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualAudioRevealAndSave(enabled);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.manual_audio.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualVideoReveal(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualVideoRevealAndSave(enabled);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.manual_video.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setSmoothScrollEnabled(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setSmoothScrollEnabledAndSave(enabled);
            source.sendFeedback(Component.translatable(
                    "chatupgrade.config.smooth_scroll.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setAudioVolumePercent(FabricClientCommandSource source, int percent) {
        try {
            ChatUpgradeConfig.setAudioVolumePercentAndSave(percent);
            AudioPlayerService.setGlobalVolumePercent(percent);
            source.sendFeedback(Component.translatable("chatupgrade.config.audio_volume.updated", Math.clamp(percent, 1, 100))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setVideoVolumePercent(FabricClientCommandSource source, int percent) {
        try {
            ChatUpgradeConfig.setVideoVolumePercentAndSave(percent);
            VideoPlayerService.setGlobalVolumePercent(percent);
            source.sendFeedback(Component.translatable("chatupgrade.config.video_volume.updated", Math.clamp(percent, 1, 100))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int sendImageUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        String payload = UpgradeBracketCodec.buildSendPayload(url, name);
        source.getPlayer().connection.sendChat(payload);
        return 1;
    }

    private static int sendAudioUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        String payload = UpgradeBracketCodec.buildSendPayload(url, name, InlineResourceType.AUDIO);
        source.getPlayer().connection.sendChat(payload);
        return 1;
    }

    private static int sendVideoUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        String payload = UpgradeBracketCodec.buildSendPayload(url, name, InlineResourceType.VIDEO);
        source.getPlayer().connection.sendChat(payload);
        return 1;
    }

    /**
     * {@code path} 由 Brigadier 的 {@link StringArgumentType#string()}
     * 解析（可引用短语）：含空格的路径用一对 {@code "} 包成<strong>一个</strong>参数，
     * 例如 {@code "D:\My Pictures\a.png"}；无空格时可不写引号。可选的 {@code name}
     * 为第二个参数（greedy，可含空格）。
     */
    private static int uploadFromFolderPath(FabricClientCommandSource source, String path,
            Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            source.sendError(Component.translatable("chatupgrade.error.empty_path").withStyle(ChatFormatting.RED));
            return 0;
        }

        Path root = Path.of(innerPath);
        Optional<Path> image = LocalImageSources.resolveFolderOrFile(root);
        if (image.isEmpty()) {
            source.sendError(Component.literal(
                    Component.translatable("chatupgrade.error.image_not_found").getString())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = image.get();
        try {
            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank())
                .orElseGet(() -> displayNameFromPath(file));
        byte[] bytes = readFileBytesQuiet(file);
        if (bytes == null) {
            source.sendError(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        CompletableFuture<Optional<String>> fut = UploadRouter.uploadBytes(
                InlineResourceType.IMAGE,
                bytes,
                file.getFileName().toString(),
                "application/octet-stream");
        finishUploadAndSend(source, fut, displayName);
        return 1;
    }

    private static int uploadViaFilePicker(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.translatable("chatupgrade.upload.open_image_picker").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickImageWithFileChooser)
                .thenAccept(picked -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (source.getPlayer() == null) {
                            return;
                        }
                        if (picked.isEmpty()) {
                            source.sendFeedback(Component.translatable("chatupgrade.upload.no_file_picked")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        Path file = picked.get();
                        try {
                            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                                return;
                            }
                        } catch (IOException e) {
                            source.sendError(Component.translatable("chatupgrade.error.read_file_size", e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                            return;
                        }
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        byte[] bytes = readFileBytesQuiet(file);
                        if (bytes == null) {
                            source.sendError(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
                            return;
                        }
                        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
                        CompletableFuture<Optional<String>> fut = UploadRouter.uploadBytes(
                                InlineResourceType.IMAGE,
                                bytes,
                                file.getFileName().toString(),
                                "application/octet-stream");
                        finishUploadAndSend(source, fut, displayName);
                    });
                });
        return 1;
    }

    private static int uploadAudioFromFolderPath(FabricClientCommandSource source, String path,
            Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            source.sendError(Component.translatable("chatupgrade.error.empty_path").withStyle(ChatFormatting.RED));
            return 0;
        }
        Path root = Path.of(innerPath);
        Optional<Path> audio = LocalImageSources.resolveAudioFolderOrFile(root);
        if (audio.isEmpty()) {
            source.sendError(Component.translatable("chatupgrade.error.audio_not_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = audio.get();
        try {
            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        byte[] bytes = readFileBytesQuiet(file);
        if (bytes == null) {
            source.sendError(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        CompletableFuture<Optional<String>> fut = UploadRouter.uploadBytes(
                InlineResourceType.AUDIO,
                bytes,
                file.getFileName().toString(),
                "application/octet-stream");
        finishUploadAndSendAudio(source, fut, displayName);
        return 1;
    }

    private static int uploadAudioViaFilePicker(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.translatable("chatupgrade.upload.open_audio_picker").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickAudioWithFileChooser)
                .thenAccept(picked -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (source.getPlayer() == null) {
                            return;
                        }
                        if (picked.isEmpty()) {
                            source.sendFeedback(Component.translatable("chatupgrade.upload.no_file_picked")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        Path file = picked.get();
                        try {
                            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                                return;
                            }
                        } catch (IOException e) {
                            source.sendError(Component.translatable("chatupgrade.error.read_file_size", e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                            return;
                        }
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        byte[] bytes = readFileBytesQuiet(file);
                        if (bytes == null) {
                            source.sendError(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
                            return;
                        }
                        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
                        CompletableFuture<Optional<String>> fut = UploadRouter.uploadBytes(
                                InlineResourceType.AUDIO,
                                bytes,
                                file.getFileName().toString(),
                                "application/octet-stream");
                        finishUploadAndSendAudio(source, fut, displayName);
                    });
                });
        return 1;
    }

    private static int uploadVideoFromFolderPath(FabricClientCommandSource source, String path,
            Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            source.sendError(Component.translatable("chatupgrade.error.empty_path").withStyle(ChatFormatting.RED));
            return 0;
        }
        Path root = Path.of(innerPath);
        Optional<Path> video = LocalImageSources.resolveVideoFolderOrFile(root);
        if (video.isEmpty()) {
            source.sendError(Component.translatable("chatupgrade.error.video_not_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = video.get();
        try {
            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            source.sendError(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        byte[] bytes = readFileBytesQuiet(file);
        if (bytes == null) {
            source.sendError(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        CompletableFuture<Optional<String>> fut = UploadRouter.uploadBytes(
                InlineResourceType.VIDEO,
                bytes,
                file.getFileName().toString(),
                "application/octet-stream");
        finishUploadAndSendVideo(source, fut, displayName);
        return 1;
    }

    private static int uploadVideoViaFilePicker(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.translatable("chatupgrade.upload.open_video_picker").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickVideoWithFileChooser)
                .thenAccept(picked -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (source.getPlayer() == null) {
                            return;
                        }
                        if (picked.isEmpty()) {
                            source.sendFeedback(Component.translatable("chatupgrade.upload.no_file_picked")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        Path file = picked.get();
                        try {
                            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                                return;
                            }
                        } catch (IOException e) {
                            source.sendError(Component.translatable("chatupgrade.error.read_file_size", e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                            return;
                        }
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        byte[] bytes = readFileBytesQuiet(file);
                        if (bytes == null) {
                            source.sendError(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
                            return;
                        }
                        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
                        CompletableFuture<Optional<String>> fut = UploadRouter.uploadBytes(
                                InlineResourceType.VIDEO,
                                bytes,
                                file.getFileName().toString(),
                                "application/octet-stream");
                        finishUploadAndSendVideo(source, fut, displayName);
                    });
                });
        return 1;
    }

    private static int uploadFromClipboard(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return 0;
        }
        Optional<byte[]> png = LocalImageSources.readClipboardImagePngBytes();
        if (png.isEmpty()) {
            source.sendError(Component.translatable("chatupgrade.error.clipboard_no_image")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (rejectIfUploadTooLarge(source, png.get().length)) {
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank())
                .orElse(Component.translatable("chatupgrade.upload.default_name.paste").getString());
        source.sendFeedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        finishUploadAndSend(source, UploadRouter.uploadBytes(InlineResourceType.IMAGE, png.get(), "paste.png",
                "image/png"), displayName);
        return 1;
    }

    private static void finishUploadAndSend(
            FabricClientCommandSource source,
            CompletableFuture<Optional<String>> uploadFuture,
            String displayName) {
        uploadFuture.thenAccept(urlOpt -> Minecraft.getInstance().execute(() -> {
            if (source.getPlayer() == null) {
                return;
            }
            if (urlOpt.isEmpty()) {
                source.sendError(uploadFailedMessage(Component.translatable("chatupgrade.upload.action.upload").getString()));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.translatable("chatupgrade.upload.sent", url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static void finishUploadAndSendAudio(
            FabricClientCommandSource source,
            CompletableFuture<Optional<String>> uploadFuture,
            String displayName) {
        uploadFuture.thenAccept(urlOpt -> Minecraft.getInstance().execute(() -> {
            if (source.getPlayer() == null) {
                return;
            }
            if (urlOpt.isEmpty()) {
                source.sendError(uploadFailedMessage(Component.translatable("chatupgrade.upload.action.audio_upload").getString()));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName, InlineResourceType.AUDIO);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.translatable("chatupgrade.upload.audio_sent", url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static void finishUploadAndSendVideo(
            FabricClientCommandSource source,
            CompletableFuture<Optional<String>> uploadFuture,
            String displayName) {
        uploadFuture.thenAccept(urlOpt -> Minecraft.getInstance().execute(() -> {
            if (source.getPlayer() == null) {
                return;
            }
            if (urlOpt.isEmpty()) {
                source.sendError(uploadFailedMessage(Component.translatable("chatupgrade.upload.action.video_upload").getString()));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName, InlineResourceType.VIDEO);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.translatable("chatupgrade.upload.video_sent", url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static String displayNameFromPath(Path file) {
        String fn = file.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot > 0 ? fn.substring(0, dot) : fn;
    }

    private static boolean rejectIfUploadTooLarge(FabricClientCommandSource source, long sizeBytes) {
        int max = ChatUpgradeConfig.get().maxUploadBytes;
        if (sizeBytes <= max) {
            return false;
        }
        source.sendError(Component.translatable(
                "chatupgrade.upload.too_large",
                ChatUpgradeConfig.formatBytesHuman(max),
                ChatUpgradeConfig.formatBytesHuman(sizeBytes))
                .withStyle(ChatFormatting.RED));
        return true;
    }

    private static String uploadHint() {
        ChatUpgradeConfig.UploadMode mode = ChatUpgradeConfig.get().uploadMode;
        boolean serverCap = ServerMediaClient.capability().enabled();
        return switch (mode) {
            case THIRD_PARTY -> Component.translatable("chatupgrade.upload.hint.third_party").getString();
            case SERVER -> Component.translatable("chatupgrade.upload.hint.server").getString();
            case AUTO -> serverCap
                    ? Component.translatable("chatupgrade.upload.hint.server").getString()
                    : Component.translatable("chatupgrade.upload.hint.third_party").getString();
        };
    }

    private static Component uploadFailedMessage(String actionLabel) {
        ChatUpgradeConfig.UploadMode mode = ChatUpgradeConfig.get().uploadMode;
        boolean serverAttempted = switch (mode) {
            case SERVER -> true;
            case AUTO -> ServerMediaClient.capability().enabled();
            case THIRD_PARTY -> false;
        };
        if (!serverAttempted) {
            return Component.translatable("chatupgrade.upload.failed.third_party", actionLabel)
                    .withStyle(ChatFormatting.RED);
        }
        MutableComponent tail = Component.translatable("chatupgrade.upload.switch_to_third")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.YELLOW)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.SuggestCommand("/chatupgrade config uploadmode third"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chatupgrade.upload.switch_to_third.hover"))));
        return Component.translatable("chatupgrade.upload.failed.server", actionLabel)
                .withStyle(ChatFormatting.RED)
                .append(tail);
    }

    private static @Nullable byte[] readFileBytesQuiet(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (Exception e) {
            return null;
        }
    }
}
