package com.chat.upgrade.mixin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.net.ServerMediaPayloads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$rewriteForVanillaReceivers(ServerboundChatPacket packet, CallbackInfo ci) {
        String raw = packet.message();
        String replacement = chatupgrade$replacementForVanilla(raw);
        if (replacement == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        PlayerList playerList = server.getPlayerList();
        String sender = player.getName().getString();
        for (ServerPlayer target : playerList.getPlayers()) {
            boolean hasMod = ServerPlayNetworking.canSend(target, ServerMediaPayloads.S2CCapability.TYPE);
            String payload = hasMod
                    ? raw
                    : replacement;
            target.sendSystemMessage(Component.literal("<" + sender + "> " + payload), false);
        }
        ci.cancel();
    }

    @Unique
    private static final Pattern CHATUPGRADE_PAYLOAD = Pattern.compile(
            "\\[\\[(?:ChatUpgrade|CICode),(.*?)]]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Unique
    private static String chatupgrade$replacementForVanilla(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = CHATUPGRADE_PAYLOAD.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        String fields = matcher.group(1);
        String name = "资源";
        String type = "image";
        for (String part : fields.split(",")) {
            int idx = part.indexOf('=');
            if (idx <= 0 || idx >= part.length() - 1) {
                continue;
            }
            String k = part.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String v = part.substring(idx + 1).trim();
            if ("name".equals(k) && !v.isBlank()) {
                name = v;
            } else if ("type".equals(k) && !v.isBlank()) {
                type = v.toLowerCase(Locale.ROOT);
            }
        }
        String typeLabel = switch (type) {
            case "audio" -> "音频";
            case "video" -> "视频";
            default -> "图片";
        };
        return "[" + typeLabel + "-" + name + "] 需要安装Chat upgrade模组";
    }
}
