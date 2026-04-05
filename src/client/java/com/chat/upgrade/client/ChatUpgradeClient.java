package com.chat.upgrade.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.BoolArgumentType;
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
        ChatUpgradeConfig.load();
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
                                .then(ClientCommands.literal("reload")
                                        .executes(ctx -> reloadConfig(ctx.getSource()))))));
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
        boolean ci = ChatUpgradeConfig.get().ciCompatibility;
        boolean manual = ChatUpgradeConfig.get().manualImageReveal;
        source.sendFeedback(Component.literal(
                "已重载 config/chat-upgrade.json 。CICode: " + (ci ? "开" : "关")
                        + "；手动渲染: " + (manual ? "开" : "关"))
                .withStyle(ChatFormatting.GREEN));
        return 1;
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

    private static int sendImageUrl(FabricClientCommandSource source, String url, String name) {
        if (source.getPlayer() == null) {
            source.sendError(Component.literal("未连接到服务器，无法发送。").withStyle(ChatFormatting.RED));
            return 0;
        }
        String payload = UpgradeBracketCodec.buildSendPayload(url, name);
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
        String displayName = displayNameArg.filter(s -> !s.isBlank())
                .orElseGet(() -> displayNameFromPath(file));
        source.sendFeedback(Component.literal("正在上传到 Catbox…").withStyle(ChatFormatting.GRAY));
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
                        String displayName = displayNameArg.orElseGet(() -> displayNameFromPath(file));
                        source.sendFeedback(Component.literal("正在上传到 Catbox…").withStyle(ChatFormatting.GRAY));
                        finishUploadAndSend(source, CatboxUploader.uploadFile(file), displayName);
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
        String displayName = displayNameArg.filter(s -> !s.isBlank()).orElse("粘贴");
        source.sendFeedback(Component.literal("正在上传到 Catbox…").withStyle(ChatFormatting.GRAY));
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
                source.sendError(Component.literal("上传失败（网络、文件或 Catbox 返回错误）。")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            String url = urlOpt.get();
            String payload = UpgradeBracketCodec.buildSendPayload(url, displayName);
            source.getPlayer().connection.sendChat(payload);
            source.sendFeedback(Component.literal("已发送: " + url).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static String displayNameFromPath(Path file) {
        String fn = file.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot > 0 ? fn.substring(0, dot) : fn;
    }
}
