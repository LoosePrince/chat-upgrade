package com.chat.upgrade.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("chatupgrade")
                        .then(ClientCommands.literal("send")
                                .then(ClientCommands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> sendImageUrl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                "图片"))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> sendImageUrl(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "url"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(ClientCommands.literal("sendaudio")
                                .then(ClientCommands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> sendAudioUrl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                "音频"))
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> sendAudioUrl(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "url"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(ClientCommands.literal("sendvideo")
                                .then(ClientCommands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> sendVideoUrl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                "视频"))
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
        String ffState = ff.ready() ? "就绪" : (ff.attempted() ? "已尝试但未就绪" : "未尝试");
        String ffJars = "javacpp=" + (ff.javacppPresent() ? "存在" : "缺失")
                + "，ffmpeg=" + (ff.ffmpegPresent() ? "存在" : "缺失");
        boolean apngLoaded = ExternalImageIoPluginLoader.isLoaded();
        boolean apngJar = ExternalImageIoPluginLoader.hasApngJar();
        source.sendFeedback(Component.literal(
                "FFmpeg: " + ffState
                        + "（平台: " + ff.platform()
                        + "；" + ffJars + "）")
                .withStyle(ChatFormatting.AQUA));
        source.sendFeedback(Component.literal(
                "APNG: " + (apngLoaded ? "已加载" : "未加载")
                        + "（jar: " + (apngJar ? "存在" : "缺失") + "）")
                .withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int pluginLoadFfmpeg(FabricClientCommandSource source, boolean forceDownload) {
        source.sendFeedback(Component.literal(
                forceDownload ? "正在强制下载并加载 FFmpeg..." : "正在加载 FFmpeg...")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            boolean ok = FfmpegNativeBootstrap.reload(forceDownload);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (ok) {
                        source.sendFeedback(Component.literal("FFmpeg 已就绪。").withStyle(ChatFormatting.GREEN));
                    } else {
                        source.sendError(Component.literal("FFmpeg 仍未就绪，请查看 latest.log。")
                                .withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
        return 1;
    }

    private static int pluginLoadApng(FabricClientCommandSource source, boolean forceDownload) {
        source.sendFeedback(Component.literal(
                forceDownload ? "正在强制下载并加载 APNG 插件..." : "正在加载 APNG 插件...")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            ExternalImageIoPluginLoader.reload(forceDownload);
            boolean ok = ExternalImageIoPluginLoader.hasApngJar();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (ok) {
                        source.sendFeedback(Component.literal("APNG 插件已处理完成（已写入 libs 并尝试加载）。")
                                .withStyle(ChatFormatting.GREEN));
                    } else {
                        source.sendError(Component.literal("APNG 插件下载/加载失败，请查看 latest.log。")
                                .withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
        return 1;
    }

    private static int pluginLoadAll(FabricClientCommandSource source, boolean forceDownload) {
        source.sendFeedback(Component.literal(
                forceDownload ? "正在强制下载并加载 FFmpeg + APNG..." : "正在加载 FFmpeg + APNG...")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.runAsync(() -> {
            boolean ffOk = FfmpegNativeBootstrap.reload(forceDownload);
            ExternalImageIoPluginLoader.reload(forceDownload);
            boolean apngOk = ExternalImageIoPluginLoader.hasApngJar();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    source.sendFeedback(Component.literal(
                            "结果：FFmpeg=" + (ffOk ? "就绪" : "未就绪")
                                    + "，APNG=" + (apngOk ? "已处理" : "失败"))
                            .withStyle((ffOk && apngOk) ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                });
            }
        });
        return 1;
    }

    private static int setCiCompatibility(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setCiCompatibilityAndSave(enabled);
            source.sendFeedback(Component.literal(
                    "CICode 格式已" + (enabled ? "开启" : "关闭") + "。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
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
        source.sendFeedback(Component.literal(
                "已重载 config/chat-upgrade/chat-upgrade.json 。CICode: " + (ci ? "开" : "关")
                        + "；手动渲染: " + (manual ? "开" : "关")
                        + "；音频手动渲染: " + (manualAudio ? "开" : "关")
                        + "；视频手动渲染: " + (manualVideo ? "开" : "关")
                        + "；音频音量: " + cfg.audioVolumePercent + "%"
                        + "；视频音量: " + cfg.videoVolumePercent + "%"
                        + "；接收上限: " + ChatUpgradeConfig.formatBytesHuman(cfg.maxReceiveBytes)
                        + "；上传上限: " + ChatUpgradeConfig.formatBytesHuman(cfg.maxUploadBytes))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setMaxReceiveMebibytes(FabricClientCommandSource source, int mebibytes) {
        try {
            int bytes = Math.multiplyExact(mebibytes, 1024 * 1024);
            ChatUpgradeConfig.setMaxReceiveBytesAndSave(bytes);
            source.sendFeedback(Component.literal(
                    "接收体积上限已设为 " + mebibytes + " MiB（" + ChatUpgradeConfig.formatBytesHuman(bytes)
                            + "）；配置项最高不超过 10 MiB。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (ArithmeticException e) {
            source.sendError(Component.literal("数值过大。").withStyle(ChatFormatting.RED));
            return 0;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setMaxUploadMebibytes(FabricClientCommandSource source, int mebibytes) {
        try {
            int bytes = Math.multiplyExact(mebibytes, 1024 * 1024);
            ChatUpgradeConfig.setMaxUploadBytesAndSave(bytes);
            source.sendFeedback(Component.literal(
                    "上传体积上限已设为 " + mebibytes + " MiB（" + ChatUpgradeConfig.formatBytesHuman(bytes)
                            + "）；配置项最高不超过 10 MiB。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (ArithmeticException e) {
            source.sendError(Component.literal("数值过大。").withStyle(ChatFormatting.RED));
            return 0;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualImageReveal(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualImageRevealAndSave(enabled);
            source.sendFeedback(Component.literal(
                    "手动渲染（点击 [图片: …] 后再加载预览）已" + (enabled ? "开启" : "关闭") + "。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualAudioReveal(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualAudioRevealAndSave(enabled);
            source.sendFeedback(Component.literal(
                    "音频手动渲染（点击 [音频: …] 后再加载预览）已" + (enabled ? "开启" : "关闭") + "。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setManualVideoReveal(FabricClientCommandSource source, boolean enabled) {
        try {
            ChatUpgradeConfig.setManualVideoRevealAndSave(enabled);
            source.sendFeedback(Component.literal(
                    "视频手动渲染（点击 [视频: …] 后再加载预览）已" + (enabled ? "开启" : "关闭") + "。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setAudioVolumePercent(FabricClientCommandSource source, int percent) {
        try {
            ChatUpgradeConfig.setAudioVolumePercentAndSave(percent);
            AudioPlayerService.setGlobalVolumePercent(percent);
            source.sendFeedback(Component.literal("音频音量已设为 " + Math.clamp(percent, 1, 100) + "%。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setVideoVolumePercent(FabricClientCommandSource source, int percent) {
        try {
            ChatUpgradeConfig.setVideoVolumePercentAndSave(percent);
            VideoPlayerService.setGlobalVolumePercent(percent);
            source.sendFeedback(Component.literal(
                    "视频音量已设为 " + Math.clamp(percent, 1, 100) + "%（当前视频预览不播放音轨，配置已保存）。")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("无法写入配置: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int sendImageUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        String payload = UpgradeBracketCodec.buildSendPayload(url, name);
        source.getPlayer().connection.sendChat(payload);
        return 1;
    }

    private static int sendAudioUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        String payload = UpgradeBracketCodec.buildSendPayload(url, name, InlineResourceType.AUDIO);
        source.getPlayer().connection.sendChat(payload);
        return 1;
    }

    private static int sendVideoUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
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
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            source.sendError(Component.literal("路径不能为空。").withStyle(ChatFormatting.RED));
            return 0;
        }

        Path root = Path.of(innerPath);
        Optional<Path> image = LocalImageSources.resolveFolderOrFile(root);
        if (image.isEmpty()) {
            source.sendError(Component.literal(
                    "未找到可用图片：请提供图片文件路径，或包含常见图片扩展名的文件夹（取最新修改的一个）。")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = image.get();
        try {
            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            source.sendError(Component.literal("无法读取文件大小: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank())
                .orElseGet(() -> displayNameFromPath(file));
        source.sendFeedback(Component.literal("正在上传到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
        finishUploadAndSend(source, CatboxUploader.uploadFile(file), displayName);
        return 1;
    }

    private static int uploadViaFilePicker(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.literal("正在打开文件选择器…").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickImageWithFileChooser)
                .thenAccept(picked -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (source.getPlayer() == null) {
                            return;
                        }
                        if (picked.isEmpty()) {
                            source.sendFeedback(Component.literal("未选择文件或无法打开对话框。")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        Path file = picked.get();
                        try {
                            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                                return;
                            }
                        } catch (IOException e) {
                            source.sendError(Component.literal("无法读取文件大小: " + e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                            return;
                        }
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        source.sendFeedback(
                                Component.literal("正在上传到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
                        finishUploadAndSend(source, CatboxUploader.uploadFile(file), displayName);
                    });
                });
        return 1;
    }

    private static int uploadAudioFromFolderPath(FabricClientCommandSource source, String path,
            Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            source.sendError(Component.literal("路径不能为空。").withStyle(ChatFormatting.RED));
            return 0;
        }
        Path root = Path.of(innerPath);
        Optional<Path> audio = LocalImageSources.resolveAudioFolderOrFile(root);
        if (audio.isEmpty()) {
            source.sendError(Component.literal("未找到可用音频文件（支持 ogg/wav/mp3/flac/m4a/aac/opus/webm）。")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = audio.get();
        try {
            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            source.sendError(Component.literal("无法读取文件大小: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        source.sendFeedback(Component.literal("正在上传音频到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
        finishUploadAndSendAudio(source, CatboxUploader.uploadFile(file), displayName);
        return 1;
    }

    private static int uploadAudioViaFilePicker(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.literal("正在打开音频文件选择器…").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickAudioWithFileChooser)
                .thenAccept(picked -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (source.getPlayer() == null) {
                            return;
                        }
                        if (picked.isEmpty()) {
                            source.sendFeedback(Component.literal("未选择文件或无法打开对话框。")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        Path file = picked.get();
                        try {
                            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                                return;
                            }
                        } catch (IOException e) {
                            source.sendError(Component.literal("无法读取文件大小: " + e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                            return;
                        }
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        source.sendFeedback(
                                Component.literal("正在上传音频到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
                        finishUploadAndSendAudio(source, CatboxUploader.uploadFile(file), displayName);
                    });
                });
        return 1;
    }

    private static int uploadVideoFromFolderPath(FabricClientCommandSource source, String path,
            Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        String innerPath = path.trim();
        if (innerPath.isEmpty()) {
            source.sendError(Component.literal("路径不能为空。").withStyle(ChatFormatting.RED));
            return 0;
        }
        Path root = Path.of(innerPath);
        Optional<Path> video = LocalImageSources.resolveVideoFolderOrFile(root);
        if (video.isEmpty()) {
            source.sendError(Component.literal("未找到可用视频文件（至少支持 mp4，其他尽可能支持）。")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        Path file = video.get();
        try {
            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                return 0;
            }
        } catch (IOException e) {
            source.sendError(Component.literal("无法读取文件大小: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElseGet(() -> displayNameFromPath(file));
        source.sendFeedback(Component.literal("正在上传视频到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
        finishUploadAndSendVideo(source, CatboxUploader.uploadFile(file), displayName);
        return 1;
    }

    private static int uploadVideoViaFilePicker(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendFeedback(Component.literal("正在打开视频文件选择器…").withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(LocalImageSources::pickVideoWithFileChooser)
                .thenAccept(picked -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (source.getPlayer() == null) {
                            return;
                        }
                        if (picked.isEmpty()) {
                            source.sendFeedback(Component.literal("未选择文件或无法打开对话框。")
                                    .withStyle(ChatFormatting.GRAY));
                            return;
                        }
                        Path file = picked.get();
                        try {
                            if (rejectIfUploadTooLarge(source, Files.size(file))) {
                                return;
                            }
                        } catch (IOException e) {
                            source.sendError(Component.literal("无法读取文件大小: " + e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                            return;
                        }
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        source.sendFeedback(
                                Component.literal("正在上传视频到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
                        finishUploadAndSendVideo(source, CatboxUploader.uploadFile(file), displayName);
                    });
                });
        return 1;
    }

    private static int uploadFromClipboard(FabricClientCommandSource source, Optional<String> displayNameArg) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        Optional<byte[]> png = LocalImageSources.readClipboardImagePngBytes();
        if (png.isEmpty()) {
            source.sendError(Component.literal("剪贴板里没有可用的图片（可尝试在画图/浏览器中复制后再试）。")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (rejectIfUploadTooLarge(source, png.get().length)) {
            return 0;
        }
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElse("粘贴");
        source.sendFeedback(Component.literal("正在上传到 Litterbox（1 小时有效）…").withStyle(ChatFormatting.GRAY));
        finishUploadAndSend(source, CatboxUploader.uploadBytes(png.get(), "paste.png"), displayName);
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
                source.sendError(Component.literal("上传失败（网络、文件或 Litterbox 返回错误）。")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.literal("已发送: " + url).withStyle(ChatFormatting.GREEN));
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
                source.sendError(Component.literal("音频上传失败（网络、文件或 Litterbox 返回错误）。")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName, InlineResourceType.AUDIO);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.literal("已发送音频: " + url).withStyle(ChatFormatting.GREEN));
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
                source.sendError(Component.literal("视频上传失败（网络、文件或 Litterbox 返回错误）。")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName, InlineResourceType.VIDEO);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.literal("已发送视频: " + url).withStyle(ChatFormatting.GREEN));
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
        source.sendError(Component.literal(
                "文件超过上传体积限制（上限 "
                        + ChatUpgradeConfig.formatBytesHuman(max)
                        + "，当前 "
                        + ChatUpgradeConfig.formatBytesHuman(sizeBytes)
                        + "）。可用 /chatupgrade config maxupload <1-10> 调整。")
                .withStyle(ChatFormatting.RED));
        return true;
    }
}
