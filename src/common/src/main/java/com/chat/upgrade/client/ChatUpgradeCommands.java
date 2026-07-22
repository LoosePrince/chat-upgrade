package com.chat.upgrade.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.net.servermedia.ServerMediaNetworking;
import com.chat.upgrade.client.plugin.ExternalImageIoPluginLoader;
import com.chat.upgrade.client.plugin.FfmpegNativeBootstrap;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.upload.LocalImageSources;
import com.chat.upgrade.client.upload.UploadRouter;
import com.chat.upgrade.platform.command.CommandAdapter;
import com.chat.upgrade.platform.command.CommandSink;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Loader-agnostic {@code /chatupgrade} command tree. The tree is built generically over the
 * brigadier source type {@code S}; each loader supplies a {@link CommandAdapter} that turns its
 * native command source into a {@link CommandSink}. Player access uses {@link Minecraft} directly
 * since these are client commands.
 */
public final class ChatUpgradeCommands {
    private ChatUpgradeCommands() {
    }

    public static <S> LiteralArgumentBuilder<S> build(CommandAdapter<S> adapter) {
        return new Tree<>(adapter).root();
    }

    /** Holds the type variable {@code S} so the brigadier builders compose without explicit witnesses. */
    private static final class Tree<S> {
        private final CommandAdapter<S> adapter;

        Tree(CommandAdapter<S> adapter) {
            this.adapter = adapter;
        }

        private LiteralArgumentBuilder<S> lit(String name) {
            return LiteralArgumentBuilder.literal(name);
        }

        private <T> RequiredArgumentBuilder<S, T> arg(String name, ArgumentType<T> type) {
            return RequiredArgumentBuilder.argument(name, type);
        }

        private CommandSink sink(com.mojang.brigadier.context.CommandContext<S> ctx) {
            return adapter.sink(ctx.getSource());
        }

        LiteralArgumentBuilder<S> root() {
            return lit("chatupgrade")
                    .then(lit("send")
                            .then(arg("url", StringArgumentType.string())
                                    .executes(ctx -> sendImageUrl(sink(ctx),
                                            StringArgumentType.getString(ctx, "url"),
                                            Component.translatable("chatupgrade.type.image").getString()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> sendImageUrl(sink(ctx),
                                                    StringArgumentType.getString(ctx, "url"),
                                                    StringArgumentType.getString(ctx, "name"))))))
                    .then(lit("sendaudio")
                            .then(arg("url", StringArgumentType.string())
                                    .executes(ctx -> sendAudioUrl(sink(ctx),
                                            StringArgumentType.getString(ctx, "url"),
                                            Component.translatable("chatupgrade.type.audio").getString()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> sendAudioUrl(sink(ctx),
                                                    StringArgumentType.getString(ctx, "url"),
                                                    StringArgumentType.getString(ctx, "name"))))))
                    .then(lit("sendvideo")
                            .then(arg("url", StringArgumentType.string())
                                    .executes(ctx -> sendVideoUrl(sink(ctx),
                                            StringArgumentType.getString(ctx, "url"),
                                            Component.translatable("chatupgrade.type.video").getString()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> sendVideoUrl(sink(ctx),
                                                    StringArgumentType.getString(ctx, "url"),
                                                    StringArgumentType.getString(ctx, "name"))))))
                    .then(lit("upload")
                            .then(lit("folder")
                                    .then(arg("path", StringArgumentType.string())
                                            .executes(ctx -> uploadFromFolderPath(sink(ctx),
                                                    StringArgumentType.getString(ctx, "path"),
                                                    Optional.empty()))
                                            .then(arg("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> uploadFromFolderPath(sink(ctx),
                                                            StringArgumentType.getString(ctx, "path"),
                                                            Optional.of(StringArgumentType.getString(ctx, "name")))))))
                            .then(lit("pick")
                                    .executes(ctx -> uploadViaFilePicker(sink(ctx), Optional.empty()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> uploadViaFilePicker(sink(ctx),
                                                    Optional.of(StringArgumentType.getString(ctx, "name"))))))
                            .then(lit("paste")
                                    .executes(ctx -> uploadFromClipboard(sink(ctx), Optional.empty()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> uploadFromClipboard(sink(ctx),
                                                    Optional.of(StringArgumentType.getString(ctx, "name")))))))
                    .then(lit("uploadaudio")
                            .then(lit("folder")
                                    .then(arg("path", StringArgumentType.string())
                                            .executes(ctx -> uploadAudioFromFolderPath(sink(ctx),
                                                    StringArgumentType.getString(ctx, "path"),
                                                    Optional.empty()))
                                            .then(arg("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> uploadAudioFromFolderPath(sink(ctx),
                                                            StringArgumentType.getString(ctx, "path"),
                                                            Optional.of(StringArgumentType.getString(ctx, "name")))))))
                            .then(lit("pick")
                                    .executes(ctx -> uploadAudioViaFilePicker(sink(ctx), Optional.empty()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> uploadAudioViaFilePicker(sink(ctx),
                                                    Optional.of(StringArgumentType.getString(ctx, "name")))))))
                    .then(lit("uploadvideo")
                            .then(lit("folder")
                                    .then(arg("path", StringArgumentType.string())
                                            .executes(ctx -> uploadVideoFromFolderPath(sink(ctx),
                                                    StringArgumentType.getString(ctx, "path"),
                                                    Optional.empty()))
                                            .then(arg("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> uploadVideoFromFolderPath(sink(ctx),
                                                            StringArgumentType.getString(ctx, "path"),
                                                            Optional.of(StringArgumentType.getString(ctx, "name")))))))
                            .then(lit("pick")
                                    .executes(ctx -> uploadVideoViaFilePicker(sink(ctx), Optional.empty()))
                                    .then(arg("name", StringArgumentType.greedyString())
                                            .executes(ctx -> uploadVideoViaFilePicker(sink(ctx),
                                                    Optional.of(StringArgumentType.getString(ctx, "name")))))))
                    .then(lit("config")
                            .then(lit("uploadmode")
                                    .then(arg("mode", StringArgumentType.word())
                                            .executes(ctx -> setUploadMode(sink(ctx),
                                                    StringArgumentType.getString(ctx, "mode")))))
                            .then(lit("inputmode")
                                    .then(lit("takeover")
                                            .executes(ctx -> setChatInputMode(sink(ctx),
                                                    ChatUpgradeConfig.ChatInputMode.TAKEOVER)))
                                    .then(lit("compat")
                                            .executes(ctx -> setChatInputMode(sink(ctx),
                                                    ChatUpgradeConfig.ChatInputMode.COMPAT_TEXT_VANILLA))))
                            .then(lit("ci")
                                    .then(arg("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> setCiCompatibility(sink(ctx),
                                                    BoolArgumentType.getBool(ctx, "enabled")))))
                            .then(lit("manual")
                                    .then(arg("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> setManualImageReveal(sink(ctx),
                                                    BoolArgumentType.getBool(ctx, "enabled")))))
                            .then(lit("manualaudio")
                                    .then(arg("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> setManualAudioReveal(sink(ctx),
                                                    BoolArgumentType.getBool(ctx, "enabled")))))
                            .then(lit("manualvideo")
                                    .then(arg("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> setManualVideoReveal(sink(ctx),
                                                    BoolArgumentType.getBool(ctx, "enabled")))))
                            .then(lit("smoothscroll")
                                    .then(arg("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> setSmoothScrollEnabled(sink(ctx),
                                                    BoolArgumentType.getBool(ctx, "enabled")))))
                            .then(lit("modbuttonarrownavigation")
                                    .then(arg("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> setModButtonArrowNavigation(sink(ctx),
                                                    BoolArgumentType.getBool(ctx, "enabled")))))
                            .then(lit("reload")
                                    .executes(ctx -> reloadConfig(sink(ctx))))
                            .then(lit("audiovolume")
                                    .then(arg("percent", IntegerArgumentType.integer(1, 100))
                                            .executes(ctx -> setAudioVolumePercent(sink(ctx),
                                                    IntegerArgumentType.getInteger(ctx, "percent")))))
                            .then(lit("videovolume")
                                    .then(arg("percent", IntegerArgumentType.integer(1, 100))
                                            .executes(ctx -> setVideoVolumePercent(sink(ctx),
                                                    IntegerArgumentType.getInteger(ctx, "percent")))))
                            .then(lit("maxreceive")
                                    .then(arg("mebibytes", IntegerArgumentType.integer(
                                            1, ChatUpgradeConfig.ABSOLUTE_MAX_UPLOAD_BYTES / (1024 * 1024)))
                                            .executes(ctx -> setMaxReceiveMebibytes(sink(ctx),
                                                    IntegerArgumentType.getInteger(ctx, "mebibytes")))))
                            .then(lit("maxupload")
                                    .then(arg("mebibytes", IntegerArgumentType.integer(
                                            1, ChatUpgradeConfig.ABSOLUTE_MAX_UPLOAD_BYTES / (1024 * 1024)))
                                            .executes(ctx -> setMaxUploadMebibytes(sink(ctx),
                                                    IntegerArgumentType.getInteger(ctx, "mebibytes")))))
                            .then(lit("plugin")
                                    .then(lit("status")
                                            .executes(ctx -> pluginStatus(sink(ctx))))
                                    .then(lit("load")
                                            .then(lit("ffmpeg")
                                                    .executes(ctx -> pluginLoadFfmpeg(sink(ctx), false)))
                                            .then(lit("apng")
                                                    .executes(ctx -> pluginLoadApng(sink(ctx), false)))
                                            .then(lit("all")
                                                    .executes(ctx -> pluginLoadAll(sink(ctx), false))))
                                    .then(lit("download")
                                            .then(lit("ffmpeg")
                                                    .executes(ctx -> pluginLoadFfmpeg(sink(ctx), true)))
                                            .then(lit("apng")
                                                    .executes(ctx -> pluginLoadApng(sink(ctx), true)))
                                            .then(lit("all")
                                                    .executes(ctx -> pluginLoadAll(sink(ctx), true))))));
        }
    }

    private static @Nullable net.minecraft.client.player.LocalPlayer player() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.player;
    }

    private static boolean notConnected(CommandSink sink) {
        if (player() == null) {
            sink.error(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    private static int pluginStatus(CommandSink sink) {
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
        sink.feedback(Component.translatable(
                "chatupgrade.plugin.status.ffmpeg",
                ffState,
                ff.platform(),
                ffJars)
                .withStyle(ChatFormatting.AQUA));
        sink.feedback(Component.translatable(
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

    private static int pluginLoadFfmpeg(CommandSink sink, boolean forceDownload) {
        sink.feedback(Component.translatable(
                forceDownload ? "chatupgrade.plugin.ffmpeg.loading_force" : "chatupgrade.plugin.ffmpeg.loading")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            boolean ok = FfmpegNativeBootstrap.reload(forceDownload);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (ok) {
                        sink.feedback(Component.translatable("chatupgrade.plugin.ffmpeg.ready").withStyle(ChatFormatting.GREEN));
                    } else {
                        sink.error(Component.translatable("chatupgrade.plugin.ffmpeg.not_ready").withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
        return 1;
    }

    private static int pluginLoadApng(CommandSink sink, boolean forceDownload) {
        sink.feedback(Component.translatable(
                forceDownload ? "chatupgrade.plugin.apng.loading_force" : "chatupgrade.plugin.apng.loading")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            ExternalImageIoPluginLoader.reload(forceDownload);
            boolean ok = ExternalImageIoPluginLoader.hasApngJar();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (ok) {
                        sink.feedback(Component.translatable("chatupgrade.plugin.apng.done").withStyle(ChatFormatting.GREEN));
                    } else {
                        sink.error(Component.translatable("chatupgrade.plugin.apng.failed").withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
        return 1;
    }

    private static int pluginLoadAll(CommandSink sink, boolean forceDownload) {
        sink.feedback(Component.translatable(
                forceDownload ? "chatupgrade.plugin.all.loading_force" : "chatupgrade.plugin.all.loading")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            boolean ffOk = FfmpegNativeBootstrap.reload(forceDownload);
            ExternalImageIoPluginLoader.reload(forceDownload);
            boolean apngOk = ExternalImageIoPluginLoader.hasApngJar();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> sink.feedback(Component.translatable(
                        "chatupgrade.plugin.all.result",
                        ffOk
                                ? Component.translatable("chatupgrade.plugin.status.ready")
                                : Component.translatable("chatupgrade.plugin.status.not_ready"),
                        apngOk
                                ? Component.translatable("chatupgrade.common.done")
                                : Component.translatable("chatupgrade.common.failed"))
                        .withStyle((ffOk && apngOk) ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
            }
        });
        return 1;
    }

    private static int setCiCompatibility(CommandSink sink, boolean enabled) {
        try {
            ChatUpgradeConfig.setCiCompatibilityAndSave(enabled);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.ci.updated",
                    enabled ? Component.literal(UpgradeBracketCodec.WIRE_TAG_CI_COMPAT)
                            : Component.literal(UpgradeBracketCodec.WIRE_TAG_NATIVE))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setUploadMode(CommandSink sink, String modeRaw) {
        ChatUpgradeConfig.UploadMode mode = parseUploadMode(modeRaw);
        if (mode == null) {
            sink.error(Component.translatable("chatupgrade.config.upload_mode.invalid", modeRaw).withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            ChatUpgradeConfig.setUploadModeAndSave(mode);
            sink.feedback(Component.translatable("chatupgrade.config.upload_mode.updated", mode.name())
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setChatInputMode(CommandSink sink, ChatUpgradeConfig.ChatInputMode mode) {
        try {
            ChatUpgradeConfig.setChatInputModeAndSave(mode);
            ServerMediaNetworking.sendChatInputMode();
            sink.feedback(Component.translatable(
                    "chatupgrade.config.input_mode.updated",
                    chatInputModeLabel(mode))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static Component chatInputModeLabel(ChatUpgradeConfig.ChatInputMode mode) {
        return switch (mode) {
            case TAKEOVER -> Component.translatable("chatupgrade.config.input_mode.takeover");
            case COMPAT_TEXT_VANILLA -> Component.translatable("chatupgrade.config.input_mode.compat");
        };
    }

    private static @Nullable ChatUpgradeConfig.UploadMode parseUploadMode(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "auto" -> ChatUpgradeConfig.UploadMode.AUTO;
            case "server" -> ChatUpgradeConfig.UploadMode.SERVER;
            case "third", "third_party", "thirdparty", "litterbox", "catbox" -> ChatUpgradeConfig.UploadMode.THIRD_PARTY;
            default -> null;
        };
    }

    private static int reloadConfig(CommandSink sink) {
        ChatUpgradeConfig.load();
        ChatUpgradeConfig cfg = ChatUpgradeConfig.get();
        AudioPlayerService.setGlobalVolumePercent(cfg.audioVolumePercent);
        VideoPlayerService.setGlobalVolumePercent(cfg.videoVolumePercent);
        ServerMediaNetworking.sendChatInputMode();
        sink.feedback(Component.translatable(
                "chatupgrade.config.reload.done",
                cfg.ciCompatibility ? Component.literal(UpgradeBracketCodec.WIRE_TAG_CI_COMPAT)
                        : Component.literal(UpgradeBracketCodec.WIRE_TAG_NATIVE),
                cfg.manualImageReveal ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                cfg.manualAudioReveal ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                cfg.manualVideoReveal ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                cfg.modButtonArrowNavigation ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"),
                cfg.audioVolumePercent,
                cfg.videoVolumePercent,
                ChatUpgradeConfig.formatBytesHuman(cfg.maxReceiveBytes),
                ChatUpgradeConfig.formatBytesHuman(cfg.maxUploadBytes),
                chatInputModeLabel(cfg.chatInputMode))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setMaxReceiveMebibytes(CommandSink sink, int mebibytes) {
        try {
            int bytes = Math.multiplyExact(mebibytes, 1024 * 1024);
            ChatUpgradeConfig.setMaxReceiveBytesAndSave(bytes);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.max_receive.updated",
                    mebibytes,
                    ChatUpgradeConfig.formatBytesHuman(bytes))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (ArithmeticException e) {
            sink.error(Component.translatable("chatupgrade.error.value_too_large").withStyle(ChatFormatting.RED));
            return 0;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setMaxUploadMebibytes(CommandSink sink, int mebibytes) {
        try {
            int bytes = Math.multiplyExact(mebibytes, 1024 * 1024);
            ChatUpgradeConfig.setMaxUploadBytesAndSave(bytes);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.max_upload.updated",
                    mebibytes,
                    ChatUpgradeConfig.formatBytesHuman(bytes))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (ArithmeticException e) {
            sink.error(Component.translatable("chatupgrade.error.value_too_large").withStyle(ChatFormatting.RED));
            return 0;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualImageReveal(CommandSink sink, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualImageRevealAndSave(enabled);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.manual_image.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualAudioReveal(CommandSink sink, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualAudioRevealAndSave(enabled);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.manual_audio.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualVideoReveal(CommandSink sink, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualVideoRevealAndSave(enabled);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.manual_video.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setSmoothScrollEnabled(CommandSink sink, boolean enabled) {
        try {
            ChatUpgradeConfig.setSmoothScrollEnabledAndSave(enabled);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.smooth_scroll.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setModButtonArrowNavigation(CommandSink sink, boolean enabled) {
        try {
            ChatUpgradeConfig.setModButtonArrowNavigationAndSave(enabled);
            sink.feedback(Component.translatable(
                    "chatupgrade.config.mod_button_arrow_navigation.updated",
                    enabled ? Component.translatable("chatupgrade.common.on") : Component.translatable("chatupgrade.common.off"))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setAudioVolumePercent(CommandSink sink, int percent) {
        try {
            ChatUpgradeConfig.setAudioVolumePercentAndSave(percent);
            AudioPlayerService.setGlobalVolumePercent(percent);
            sink.feedback(Component.translatable("chatupgrade.config.audio_volume.updated", Math.clamp(percent, 1, 100))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setVideoVolumePercent(CommandSink sink, int percent) {
        try {
            ChatUpgradeConfig.setVideoVolumePercentAndSave(percent);
            VideoPlayerService.setGlobalVolumePercent(percent);
            sink.feedback(Component.translatable("chatupgrade.config.video_volume.updated", Math.clamp(percent, 1, 100))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.write_config", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int sendImageUrl(CommandSink sink, String url, String name) {
        if (notConnected(sink)) {
            return 0;
        }
        player().connection.sendChat(UpgradeBracketCodec.buildSendPayload(url, name));
        return 1;
    }

    private static int sendAudioUrl(CommandSink sink, String url, String name) {
        if (notConnected(sink)) {
            return 0;
        }
        player().connection.sendChat(UpgradeBracketCodec.buildSendPayload(url, name, InlineResourceType.AUDIO));
        return 1;
    }

    private static int sendVideoUrl(CommandSink sink, String url, String name) {
        if (notConnected(sink)) {
            return 0;
        }
        player().connection.sendChat(UpgradeBracketCodec.buildSendPayload(url, name, InlineResourceType.VIDEO));
        return 1;
    }

    private static int uploadFromFolderPath(CommandSink sink, String path, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            sink.error(Component.translatable("chatupgrade.error.empty_path").withStyle(ChatFormatting.RED));
            return 0;
        }
        Optional<Path> image = LocalImageSources.resolveFolderOrFile(Path.of(innerPath));
        if (image.isEmpty()) {
            sink.error(Component.literal(Component.translatable("chatupgrade.error.image_not_found").getString())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = image.get();
        try {
            if (rejectIfUploadTooLarge(sink, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        byte[] bytes = readFileBytesQuiet(file);
        if (bytes == null) {
            sink.error(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
            return 0;
        }
        sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        finishUploadAndSend(sink, UploadRouter.uploadBytes(
                InlineResourceType.IMAGE, bytes, file.getFileName().toString(), "application/octet-stream"), displayName);
        return 1;
    }

    private static int uploadViaFilePicker(CommandSink sink, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        sink.feedback(Component.translatable("chatupgrade.upload.open_image_picker").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickImageWithFileChooser)
                .thenAccept(picked -> Minecraft.getInstance().execute(() -> {
                    if (player() == null) {
                        return;
                    }
                    if (picked.isEmpty()) {
                        sink.feedback(Component.translatable("chatupgrade.upload.no_file_picked").withStyle(ChatFormatting.GRAY));
                        return;
                    }
                    Path file = picked.get();
                    try {
                        if (rejectIfUploadTooLarge(sink, Files.size(file))) {
                            return;
                        }
                    } catch (IOException e) {
                        sink.error(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
                        return;
                    }
                    String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                    byte[] bytes = readFileBytesQuiet(file);
                    if (bytes == null) {
                        sink.error(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
                        return;
                    }
                    sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
                    finishUploadAndSend(sink, UploadRouter.uploadBytes(
                            InlineResourceType.IMAGE, bytes, file.getFileName().toString(), "application/octet-stream"), displayName);
                }));
        return 1;
    }

    private static int uploadAudioFromFolderPath(CommandSink sink, String path, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            sink.error(Component.translatable("chatupgrade.error.empty_path").withStyle(ChatFormatting.RED));
            return 0;
        }
        Optional<Path> audio = LocalImageSources.resolveAudioFolderOrFile(Path.of(innerPath));
        if (audio.isEmpty()) {
            sink.error(Component.translatable("chatupgrade.error.audio_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = audio.get();
        try {
            if (rejectIfUploadTooLarge(sink, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        byte[] bytes = readFileBytesQuiet(file);
        if (bytes == null) {
            sink.error(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
            return 0;
        }
        sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        finishUploadAndSendAudio(sink, UploadRouter.uploadBytes(
                InlineResourceType.AUDIO, bytes, file.getFileName().toString(), "application/octet-stream"), displayName);
        return 1;
    }

    private static int uploadAudioViaFilePicker(CommandSink sink, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        sink.feedback(Component.translatable("chatupgrade.upload.open_audio_picker").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickAudioWithFileChooser)
                .thenAccept(picked -> Minecraft.getInstance().execute(() -> {
                    if (player() == null) {
                        return;
                    }
                    if (picked.isEmpty()) {
                        sink.feedback(Component.translatable("chatupgrade.upload.no_file_picked").withStyle(ChatFormatting.GRAY));
                        return;
                    }
                    Path file = picked.get();
                    try {
                        if (rejectIfUploadTooLarge(sink, Files.size(file))) {
                            return;
                        }
                    } catch (IOException e) {
                        sink.error(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
                        return;
                    }
                    String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                    byte[] bytes = readFileBytesQuiet(file);
                    if (bytes == null) {
                        sink.error(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
                        return;
                    }
                    sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
                    finishUploadAndSendAudio(sink, UploadRouter.uploadBytes(
                            InlineResourceType.AUDIO, bytes, file.getFileName().toString(), "application/octet-stream"), displayName);
                }));
        return 1;
    }

    private static int uploadVideoFromFolderPath(CommandSink sink, String path, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            sink.error(Component.translatable("chatupgrade.error.empty_path").withStyle(ChatFormatting.RED));
            return 0;
        }
        Optional<Path> video = LocalImageSources.resolveVideoFolderOrFile(Path.of(innerPath));
        if (video.isEmpty()) {
            sink.error(Component.translatable("chatupgrade.error.video_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = video.get();
        try {
            if (rejectIfUploadTooLarge(sink, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            sink.error(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        byte[] bytes = readFileBytesQuiet(file);
        if (bytes == null) {
            sink.error(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
            return 0;
        }
        sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        finishUploadAndSendVideo(sink, UploadRouter.uploadBytes(
                InlineResourceType.VIDEO, bytes, file.getFileName().toString(), "application/octet-stream"), displayName);
        return 1;
    }

    private static int uploadVideoViaFilePicker(CommandSink sink, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        sink.feedback(Component.translatable("chatupgrade.upload.open_video_picker").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickVideoWithFileChooser)
                .thenAccept(picked -> Minecraft.getInstance().execute(() -> {
                    if (player() == null) {
                        return;
                    }
                    if (picked.isEmpty()) {
                        sink.feedback(Component.translatable("chatupgrade.upload.no_file_picked").withStyle(ChatFormatting.GRAY));
                        return;
                    }
                    Path file = picked.get();
                    try {
                        if (rejectIfUploadTooLarge(sink, Files.size(file))) {
                            return;
                        }
                    } catch (IOException e) {
                        sink.error(Component.translatable("chatupgrade.error.read_file_size", e.getMessage()).withStyle(ChatFormatting.RED));
                        return;
                    }
                    String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                    byte[] bytes = readFileBytesQuiet(file);
                    if (bytes == null) {
                        sink.error(Component.translatable("chatupgrade.error.read_file_content").withStyle(ChatFormatting.RED));
                        return;
                    }
                    sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
                    finishUploadAndSendVideo(sink, UploadRouter.uploadBytes(
                            InlineResourceType.VIDEO, bytes, file.getFileName().toString(), "application/octet-stream"), displayName);
                }));
        return 1;
    }

    private static int uploadFromClipboard(CommandSink sink, Optional<String> displayNameArg) {
        if (notConnected(sink)) {
            return 0;
        }
        Optional<byte[]> png = LocalImageSources.readClipboardImagePngBytes();
        if (png.isEmpty()) {
            sink.error(Component.translatable("chatupgrade.error.clipboard_no_image").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (rejectIfUploadTooLarge(sink, png.get().length)) {
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank())
                .orElse(Component.translatable("chatupgrade.upload.default_name.paste").getString());
        sink.feedback(Component.literal(uploadHint()).withStyle(ChatFormatting.GRAY));
        finishUploadAndSend(sink, UploadRouter.uploadBytes(InlineResourceType.IMAGE, png.get(), "paste.png", "image/png"), displayName);
        return 1;
    }

    private static void finishUploadAndSend(CommandSink sink, CompletableFuture<Optional<String>> uploadFuture, String displayName) {
        uploadFuture.thenAccept(urlOpt -> Minecraft.getInstance().execute(() -> {
            if (player() == null) {
                return;
            }
            if (urlOpt.isEmpty()) {
                sink.error(uploadFailedMessage(Component.translatable("chatupgrade.upload.action.upload").getString()));
                return;
            }
            String url = urlOpt.get();
            player().connection.sendChat(UpgradeBracketCodec.buildSendPayload(url, displayName));
            sink.feedback(Component.translatable("chatupgrade.upload.sent", url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static void finishUploadAndSendAudio(CommandSink sink, CompletableFuture<Optional<String>> uploadFuture, String displayName) {
        uploadFuture.thenAccept(urlOpt -> Minecraft.getInstance().execute(() -> {
            if (player() == null) {
                return;
            }
            if (urlOpt.isEmpty()) {
                sink.error(uploadFailedMessage(Component.translatable("chatupgrade.upload.action.audio_upload").getString()));
                return;
            }
            String url = urlOpt.get();
            player().connection.sendChat(UpgradeBracketCodec.buildSendPayload(url, displayName, InlineResourceType.AUDIO));
            sink.feedback(Component.translatable("chatupgrade.upload.audio_sent", url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static void finishUploadAndSendVideo(CommandSink sink, CompletableFuture<Optional<String>> uploadFuture, String displayName) {
        uploadFuture.thenAccept(urlOpt -> Minecraft.getInstance().execute(() -> {
            if (player() == null) {
                return;
            }
            if (urlOpt.isEmpty()) {
                sink.error(uploadFailedMessage(Component.translatable("chatupgrade.upload.action.video_upload").getString()));
                return;
            }
            String url = urlOpt.get();
            player().connection.sendChat(UpgradeBracketCodec.buildSendPayload(url, displayName, InlineResourceType.VIDEO));
            sink.feedback(Component.translatable("chatupgrade.upload.video_sent", url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static String displayNameFromPath(Path file) {
        String fn = file.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot > 0 ? fn.substring(0, dot) : fn;
    }

    private static boolean rejectIfUploadTooLarge(CommandSink sink, long sizeBytes) {
        int max = ChatUpgradeConfig.get().maxUploadBytes;
        if (sizeBytes <= max) {
            return false;
        }
        sink.error(Component.translatable(
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
            return Component.translatable("chatupgrade.upload.failed.third_party", actionLabel).withStyle(ChatFormatting.RED);
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
