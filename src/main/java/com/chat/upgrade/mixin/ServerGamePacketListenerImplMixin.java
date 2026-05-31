package com.chat.upgrade.mixin;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.server.AttachmentRouteDescriptor;
import com.chat.upgrade.server.ServerMediaService;
import com.chat.upgrade.server.store.StoredMedia;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Unique
    private static final int CHATUPGRADE_ROUTE_STRUCTURED_ATTACHMENT = 0;
    @Unique
    private static final int CHATUPGRADE_ROUTE_LEGACY_MOD = 1;
    @Unique
    private static final int CHATUPGRADE_ROUTE_VANILLA = 2;

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$rewriteForVanillaReceivers(ServerboundChatPacket packet, CallbackInfo ci) {
        String raw = packet.message();
        AttachmentRouteDescriptor descriptor = chatupgrade$descriptorFor(raw);
        if (descriptor == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        PlayerList playerList = server.getPlayerList();
        String sender = player.getName().getString();
        for (ServerPlayer target : playerList.getPlayers()) {
            int route = chatupgrade$routeFor(target);
            if (route == CHATUPGRADE_ROUTE_STRUCTURED_ATTACHMENT && chatupgrade$sendStructuredAttachment(target, sender, descriptor)) {
                continue;
            }
            Component out = switch (route) {
                case CHATUPGRADE_ROUTE_STRUCTURED_ATTACHMENT, CHATUPGRADE_ROUTE_LEGACY_MOD -> chatupgrade$buildLegacyModMessage(sender, descriptor.legacyMessage());
                default -> chatupgrade$buildVanillaMessage(sender, descriptor);
            };
            target.sendSystemMessage(out, false);
        }
        ci.cancel();
    }

    @Unique
    private static int chatupgrade$routeFor(ServerPlayer target) {
        if (ServerPlayNetworking.canSend(target, ServerMediaPayloads.S2CAttachmentCapability.TYPE)) {
            return CHATUPGRADE_ROUTE_STRUCTURED_ATTACHMENT;
        }
        if (ServerPlayNetworking.canSend(target, ServerMediaPayloads.S2CCapability.TYPE)) {
            return CHATUPGRADE_ROUTE_LEGACY_MOD;
        }
        return CHATUPGRADE_ROUTE_VANILLA;
    }

    @Unique
    private static boolean chatupgrade$sendStructuredAttachment(
            ServerPlayer target,
            String sender,
            AttachmentRouteDescriptor descriptor) {
        Optional<StructuredAttachment> structuredOpt = descriptor.structuredAttachment();
        if (structuredOpt.isEmpty()) {
            return false;
        }
        StructuredAttachment attachment = structuredOpt.get();
        ServerPlayNetworking.send(target, new ServerMediaPayloads.S2CStructuredChatAttachment(
                attachment.schemaVersion(),
                sender,
                descriptor.visibleText(),
                attachment.attachmentId() == null ? "" : attachment.attachmentId(),
                attachment.mediaId() == null ? "" : attachment.mediaId(),
                attachment.typeWire(),
                attachment.displayName(),
                attachment.fallbackUrl() == null ? "" : attachment.fallbackUrl()));
        return true;
    }

    @Unique
    private static Component chatupgrade$buildLegacyModMessage(String sender, String raw) {
        return Component.literal("<" + sender + "> " + raw);
    }

    @Unique
    private static Component chatupgrade$buildVanillaMessage(String sender, AttachmentRouteDescriptor descriptor) {
        return Component.literal("<" + sender + "> ").append(chatupgrade$buildVanillaComponent(descriptor));
    }

    @Unique
    private static final Pattern CHATUPGRADE_PAYLOAD = Pattern.compile(
            "\\[\\[(?:ChatUpgrade|CICode),(.*?)]]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Unique
    private static AttachmentRouteDescriptor chatupgrade$descriptorFor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = CHATUPGRADE_PAYLOAD.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        String fields = matcher.group(1);
        String name = Component.translatable("chatupgrade.vanilla.default_name").getString();
        String type = "image";
        String url = "";
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
            } else if ("url".equals(k) && !v.isBlank()) {
                url = v;
            }
        }
        String normalizedType = chatupgrade$normalizeType(type);
        String visibleText = chatupgrade$stripPayload(raw, matcher.start(), matcher.end()).trim();
        return new AttachmentRouteDescriptor(raw, visibleText, normalizedType, chatupgrade$typeLabel(normalizedType), name, url);
    }

    @Unique
    private static String chatupgrade$stripPayload(String raw, int start, int end) {
        if (raw == null || start < 0 || end < start || end > raw.length()) {
            return "";
        }
        String stripped = (raw.substring(0, start) + raw.substring(end)).trim();
        return stripped.replaceAll("\\s+", " ");
    }

    @Unique
    private static String chatupgrade$normalizeType(String type) {
        if ("audio".equalsIgnoreCase(type)) {
            return "audio";
        }
        if ("video".equalsIgnoreCase(type)) {
            return "video";
        }
        return "image";
    }

    @Unique
    private static String chatupgrade$typeLabel(String type) {
        return switch (type) {
            case "audio" -> Component.translatable("chatupgrade.type.audio").getString();
            case "video" -> Component.translatable("chatupgrade.type.video").getString();
            default -> Component.translatable("chatupgrade.type.image").getString();
        };
    }

    @Unique
    private static Component chatupgrade$buildVanillaComponent(AttachmentRouteDescriptor descriptor) {
        String labelText = "[" + descriptor.typeLabel() + "：" + descriptor.name() + "]";
        Style labelStyle = Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(chatupgrade$buildHoverText(descriptor))))
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/LoosePrince/chat-upgrade")));

        MutableComponent out = Component.literal(labelText).withStyle(labelStyle);
        if (descriptor.url().isBlank()) {
            return out;
        }

        boolean thirdParty = !ServerMediaUrl.isServerMediaUrl(descriptor.url());
        Style urlStyle = Style.EMPTY
                .withColor(ChatFormatting.GRAY)
                .withUnderlined(thirdParty)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                        thirdParty
                                ? Component.translatable("chatupgrade.vanilla.url.open_external").getString()
                                : Component.translatable("chatupgrade.vanilla.url.internal_only").getString())));
        if (thirdParty) {
            try {
                urlStyle = urlStyle.withClickEvent(new ClickEvent.OpenUrl(URI.create(descriptor.url())));
            } catch (Exception ignored) {
            }
        }
        return out.append(Component.literal(" [url]").withStyle(urlStyle));
    }

    @Unique
    private static String chatupgrade$buildHoverText(AttachmentRouteDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append(Component.translatable("chatupgrade.hover.resource_type").getString()).append(": ")
                .append(descriptor.typeLabel()).append('\n');
        sb.append(Component.translatable("chatupgrade.hover.display_name").getString()).append(": ")
                .append(descriptor.name()).append('\n');
        sb.append(Component.translatable("chatupgrade.hover.url").getString()).append(": ")
                .append(descriptor.url().isBlank() ? Component.translatable("chatupgrade.common.na").getString() : descriptor.url()).append('\n');

        if (descriptor.url().isBlank()) {
            sb.append(Component.translatable("chatupgrade.vanilla.source.no_link").getString());
            return sb.toString();
        }

        Optional<ServerMediaUrl.Parsed> parsed = ServerMediaUrl.parse(descriptor.url());
        if (parsed.isEmpty()) {
            sb.append(Component.translatable("chatupgrade.vanilla.source.third_party").getString());
            return sb.toString();
        }

        ServerMediaUrl.Parsed p = parsed.get();
        sb.append(Component.translatable("chatupgrade.vanilla.source.server_internal").getString()).append('\n');
        sb.append(Component.translatable("chatupgrade.vanilla.media_id").getString()).append(": ").append(p.mediaId()).append('\n');
        sb.append(Component.translatable("chatupgrade.vanilla.declared_type").getString()).append(": ").append(p.typeWire());

        Optional<StoredMedia> mediaOpt = ServerMediaService.get(p.mediaId());
        if (mediaOpt.isEmpty()) {
            sb.append('\n').append(Component.translatable("chatupgrade.vanilla.server_status.miss_or_expired").getString());
            return sb.toString();
        }

        StoredMedia media = mediaOpt.get();
        sb.append('\n').append(Component.translatable("chatupgrade.vanilla.server_status.available").getString());
        if (media.contentType() != null && !media.contentType().isBlank()) {
            sb.append('\n').append(Component.translatable("chatupgrade.vanilla.content_type").getString()).append(": ").append(media.contentType());
        }
        sb.append('\n').append(Component.translatable("chatupgrade.vanilla.file_size").getString()).append(": ").append(media.byteLength()).append(" B");
        if (media.fingerprint() != null && !media.fingerprint().isBlank()) {
            sb.append('\n').append(Component.translatable("chatupgrade.vanilla.fingerprint_md5").getString()).append(": ").append(media.fingerprint());
        }
        if (media.expiresAtMs() > 0L) {
            sb.append('\n').append(Component.translatable("chatupgrade.vanilla.expires_at_ms").getString()).append(": ").append(media.expiresAtMs());
        } else {
            sb.append('\n').append(Component.translatable("chatupgrade.vanilla.never_expires").getString());
        }
        return sb.toString();
    }
}
