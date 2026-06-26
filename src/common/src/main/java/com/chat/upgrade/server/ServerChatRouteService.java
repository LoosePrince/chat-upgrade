package com.chat.upgrade.server;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.net.StructuredChatMessage;
import com.chat.upgrade.server.store.StoredMedia;

import com.chat.upgrade.platform.net.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

public final class ServerChatRouteService {
    private static final int ROUTE_STRUCTURED_MESSAGE = 0;
    private static final int ROUTE_STRUCTURED_ATTACHMENT = 1;
    private static final int ROUTE_BRACKET_COMPAT = 2;
    private static final int ROUTE_VANILLA = 3;

    private static final Pattern CHATUPGRADE_PAYLOAD = Pattern.compile(
            "\\[\\[(?:ChatUpgrade|CICode),(.*?)]]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ServerChatRouteService() {
    }

    public static boolean routeBracketProtocol(ServerPlayer senderPlayer, String raw) {
        AttachmentRouteDescriptor descriptor = descriptorForBracketProtocol(raw);
        if (descriptor == null) {
            return false;
        }
        MinecraftServer server = senderPlayer.level().getServer();
        if (server == null) {
            return false;
        }
        String sender = senderPlayer.getName().getString();
        PlayerList playerList = server.getPlayerList();
        for (ServerPlayer target : playerList.getPlayers()) {
            int route = routeFor(target);
            if (route == ROUTE_STRUCTURED_MESSAGE
                    && !shouldKeepVanillaPlainText(target, descriptor)
                    && sendStructuredMessage(target, structuredFromDescriptor(sender, descriptor))) {
                continue;
            }
            if ((route == ROUTE_STRUCTURED_MESSAGE || route == ROUTE_STRUCTURED_ATTACHMENT)
                    && sendStructuredAttachment(target, sender, descriptor)) {
                continue;
            }
            Component out = switch (route) {
                case ROUTE_STRUCTURED_MESSAGE, ROUTE_STRUCTURED_ATTACHMENT, ROUTE_BRACKET_COMPAT ->
                    buildBracketProtocolMessage(sender, descriptor.bracketMessage());
                default -> buildVanillaMessage(sender, descriptor);
            };
            target.sendSystemMessage(out, false);
        }
        return true;
    }

    public static void routeStructured(ServerPlayer senderPlayer, StructuredChatMessage message) {
        MinecraftServer server = senderPlayer.level().getServer();
        if (server == null || message == null) {
            return;
        }
        String sender = senderPlayer.getName().getString();
        StructuredChatMessage routedMessage = message.withSenderName(sender);
        AttachmentRouteDescriptor firstAttachment = firstAttachmentDescriptor(routedMessage);
        PlayerList playerList = server.getPlayerList();
        for (ServerPlayer target : playerList.getPlayers()) {
            int route = routeFor(target);
            if (route == ROUTE_STRUCTURED_MESSAGE
                    && !shouldKeepVanillaPlainText(target, routedMessage)
                    && sendStructuredMessage(target, routedMessage)) {
                continue;
            }
            if (firstAttachment != null
                    && route == ROUTE_STRUCTURED_ATTACHMENT
                    && sendStructuredAttachment(target, sender, firstAttachment)) {
                continue;
            }
            Component out = switch (route) {
                case ROUTE_STRUCTURED_MESSAGE, ROUTE_STRUCTURED_ATTACHMENT, ROUTE_BRACKET_COMPAT ->
                    buildBracketProtocolMessage(sender,
                            firstAttachment == null ? textFallback(routedMessage) : firstAttachment.bracketMessage());
                default -> firstAttachment == null
                        ? Component.literal("<" + sender + "> " + routedMessage.fallbackText())
                        : buildVanillaMessage(sender, firstAttachment);
            };
            target.sendSystemMessage(out, false);
        }
    }

    private static int routeFor(ServerPlayer target) {
        if (Net.canSendToClient(target, ServerMediaPayloads.S2CStructuredChatMessage.TYPE)) {
            return ROUTE_STRUCTURED_MESSAGE;
        }
        if (Net.canSendToClient(target, ServerMediaPayloads.S2CStructuredChatAttachment.TYPE)) {
            return ROUTE_STRUCTURED_ATTACHMENT;
        }
        if (Net.canSendToClient(target, ServerMediaPayloads.S2CCapability.TYPE)) {
            return ROUTE_BRACKET_COMPAT;
        }
        return ROUTE_VANILLA;
    }

    private static boolean shouldKeepVanillaPlainText(ServerPlayer target, StructuredChatMessage message) {
        return ServerMediaServerNetworking.isCompatTextVanillaPlayer(target) && !message.hasAttachments();
    }

    private static boolean shouldKeepVanillaPlainText(ServerPlayer target, AttachmentRouteDescriptor descriptor) {
        return ServerMediaServerNetworking.isCompatTextVanillaPlayer(target)
                && descriptor.structuredAttachment().isEmpty();
    }

    private static boolean sendStructuredMessage(ServerPlayer target, StructuredChatMessage message) {
        Net.sendToClient(target, ServerMediaPayloads.S2CStructuredChatMessage.fromMessage(message));
        return true;
    }

    private static boolean sendStructuredAttachment(
            ServerPlayer target,
            String sender,
            AttachmentRouteDescriptor descriptor) {
        Optional<StructuredAttachment> structuredOpt = descriptor.structuredAttachment();
        if (structuredOpt.isEmpty()) {
            return false;
        }
        StructuredAttachment attachment = structuredOpt.get();
        Net.sendToClient(target, new ServerMediaPayloads.S2CStructuredChatAttachment(
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

    private static StructuredChatMessage structuredFromDescriptor(String sender, AttachmentRouteDescriptor descriptor) {
        Optional<StructuredAttachment> attachmentOpt = descriptor.structuredAttachment();
        if (attachmentOpt.isEmpty()) {
            return StructuredChatMessage.textOnly("", descriptor.visibleText()).withSenderName(sender);
        }
        return StructuredChatMessage.withSingleAttachment(
                "",
                descriptor.visibleText(),
                attachmentOpt.get(),
                descriptor.bracketMessage()).withSenderName(sender);
    }

    private static AttachmentRouteDescriptor firstAttachmentDescriptor(StructuredChatMessage message) {
        if (message.attachments().isEmpty()) {
            return null;
        }
        StructuredAttachment attachment = message.attachments().getFirst();
        String url = attachment.fallbackUrl() == null ? "" : attachment.fallbackUrl();
        String text = message.plainText() == null ? "" : message.plainText();
        String bracketMessage = buildBracketFallbackPayload(text, attachment);
        return new AttachmentRouteDescriptor(
                bracketMessage,
                text,
                attachment.typeWire(),
                typeLabel(attachment.typeWire()),
                attachment.displayName(),
                url);
    }

    private static String buildBracketFallbackPayload(String text, StructuredAttachment attachment) {
        String url = attachment.fallbackUrl() == null ? "" : attachment.fallbackUrl();
        if (url.isBlank()) {
            return text == null ? "" : text;
        }
        StringBuilder payload = new StringBuilder();
        if (text != null && !text.isBlank()) {
            payload.append(text.trim()).append(' ');
        }
        payload.append("[[ChatUpgrade,url=").append(url);
        String name = attachment.displayName();
        if (name != null && !name.isBlank()) {
            payload.append(",name=").append(name.trim());
        }
        String type = normalizeType(attachment.typeWire());
        if (!"image".equals(type)) {
            payload.append(",type=").append(type);
        }
        payload.append("]] ");
        return payload.toString().trim();
    }

    private static String textFallback(StructuredChatMessage message) {
        if (message.fallbackText() != null && !message.fallbackText().isBlank()) {
            return message.fallbackText();
        }
        return message.plainText() == null ? "" : message.plainText();
    }

    private static Component buildBracketProtocolMessage(String sender, String raw) {
        return Component.literal("<" + sender + "> " + (raw == null ? "" : raw));
    }

    private static Component buildVanillaMessage(String sender, AttachmentRouteDescriptor descriptor) {
        String visibleText = descriptor.visibleText() == null ? "" : descriptor.visibleText().trim();
        MutableComponent out = Component.literal("<" + sender + "> ");
        if (!visibleText.isBlank()) {
            out.append(Component.literal(visibleText + " "));
        }
        return out.append(buildVanillaComponent(descriptor));
    }

    private static AttachmentRouteDescriptor descriptorForBracketProtocol(String raw) {
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
        String normalizedType = normalizeType(type);
        String visibleText = stripPayload(raw, matcher.start(), matcher.end()).trim();
        return new AttachmentRouteDescriptor(raw, visibleText, normalizedType, typeLabel(normalizedType), name, url);
    }

    private static String stripPayload(String raw, int start, int end) {
        if (raw == null || start < 0 || end < start || end > raw.length()) {
            return "";
        }
        String stripped = (raw.substring(0, start) + raw.substring(end)).trim();
        return stripped.replaceAll("\\s+", " ");
    }

    private static String normalizeType(String type) {
        if ("audio".equalsIgnoreCase(type)) {
            return "audio";
        }
        if ("video".equalsIgnoreCase(type)) {
            return "video";
        }
        return "image";
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "audio" -> Component.translatable("chatupgrade.type.audio").getString();
            case "video" -> Component.translatable("chatupgrade.type.video").getString();
            default -> Component.translatable("chatupgrade.type.image").getString();
        };
    }

    private static Component buildVanillaComponent(AttachmentRouteDescriptor descriptor) {
        String labelText = "[" + descriptor.typeLabel() + "：" + descriptor.name() + "]";
        Style labelStyle = Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(buildHoverText(descriptor))))
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

    private static String buildHoverText(AttachmentRouteDescriptor descriptor) {
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