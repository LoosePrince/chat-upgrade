package com.chat.upgrade.server;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chat.upgrade.net.ExternalMediaUrlPolicy;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.net.StructuredChatAuthor;
import com.chat.upgrade.net.StructuredChatEnvelope;
import com.chat.upgrade.net.StructuredChatMessage;
import com.chat.upgrade.net.StructuredChatMutation;
import com.chat.upgrade.net.StructuredChatProtocolLimits;
import com.chat.upgrade.net.StructuredChatSegment;
import com.chat.upgrade.net.StructuredChatSubmission;
import com.chat.upgrade.net.StructuredReplySummary;
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
import net.minecraft.world.scores.PlayerTeam;

public final class ServerChatRouteService {
    private static final int ROUTE_STRUCTURED_V2 = -1;
    private static final int ROUTE_STRUCTURED_MESSAGE = 0;
    private static final int ROUTE_STRUCTURED_ATTACHMENT = 1;
    private static final int ROUTE_BRACKET_COMPAT = 2;
    private static final int ROUTE_VANILLA = 3;
    private static final int MAX_RECENT_MESSAGES = 512;
    private static final int MAX_REPLY_EXCERPT_CHARS = 96;
    private static final long RETRACTED_TOMBSTONE_TTL_MS = 60L * 60L * 1000L;
    private static final Map<String, RecentMessage> RECENT_MESSAGES = new LinkedHashMap<>();
    private static final Map<String, Long> RETRACTED_MESSAGES = new LinkedHashMap<>();
    private static final ServerChatHistoryStore CHAT_HISTORY = new ServerChatHistoryStore();
    private static MinecraftServer activeServer;

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
        if (!externalAttachmentAllowed(descriptor.url())) {
            senderPlayer.sendSystemMessage(
                    Component.literal("External media links are disabled by the server.")
                            .withStyle(ChatFormatting.RED),
                    false);
            return true;
        }
        StructuredChatMessage legacy = structuredFromDescriptor("", descriptor);
        return routeStructuredV2(senderPlayer, StructuredChatSubmission.fromLegacy(legacy));
    }

    public static void routeStructured(ServerPlayer senderPlayer, StructuredChatMessage message) {
        if (message != null) {
            routeStructuredV2(senderPlayer, StructuredChatSubmission.fromLegacy(message));
        }
    }

    public static boolean routeStructuredV2(ServerPlayer senderPlayer, StructuredChatSubmission submission) {
        MinecraftServer server = senderPlayer.level().getServer();
        if (server == null || !StructuredChatProtocolLimits.accepts(submission)) {
            return false;
        }
        ensureServerScope(server);
        if (!ServerRequestLimiter.allow(
                senderPlayer.getUUID(),
                ServerRequestLimiter.Kind.CHAT,
                System.currentTimeMillis())) {
            senderPlayer.sendSystemMessage(
                    Component.literal("Structured chat rate limit exceeded.").withStyle(ChatFormatting.RED),
                    false);
            return false;
        }
        if (!attachmentsAllowed(senderPlayer, submission)) {
            senderPlayer.sendSystemMessage(
                    Component.translatable("chatupgrade.message.attachment_unavailable").withStyle(ChatFormatting.RED),
                    false);
            return false;
        }
        StructuredReplySummary reply = resolveReply(submission.replyToMessageId()).orElse(null);
        if (!submission.replyToMessageId().isBlank() && reply == null) {
            senderPlayer.sendSystemMessage(
                    Component.translatable("chatupgrade.reply.target_unavailable").withStyle(ChatFormatting.RED),
                    false);
            return false;
        }
        StructuredChatEnvelope envelope = createEnvelope(senderPlayer, submission, reply);
        grantAttachmentReads(server.getPlayerList(), envelope.attachments());
        remember(envelope, senderPlayer.getUUID());
        CHAT_HISTORY.append(server, envelope);
        broadcast(server.getPlayerList(), envelope);
        return true;
    }

    public static void rememberVanillaChat(ServerPlayer senderPlayer, String raw) {
        if (senderPlayer == null || raw == null || raw.isBlank()) {
            return;
        }
        MinecraftServer server = senderPlayer.level().getServer();
        if (server == null) {
            return;
        }
        ensureServerScope(server);
        String text = raw.trim();
        StructuredChatEnvelope envelope = new StructuredChatEnvelope(
                StructuredChatEnvelope.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                "",
                System.currentTimeMillis(),
                authorSnapshot(senderPlayer),
                "player",
                text,
                java.util.List.of(StructuredChatSegment.text(text)),
                java.util.List.of(),
                text,
                StructuredChatMessage.COMPAT_VANILLA_SAFE_TEXT,
                null);
        if (!StructuredChatProtocolLimits.accepts(envelope)) {
            return;
        }
        remember(envelope, senderPlayer.getUUID());
        CHAT_HISTORY.append(server, envelope);
    }

    public static boolean retract(ServerPlayer senderPlayer, String messageId) {
        String normalizedId = messageId == null ? "" : messageId.trim();
        if (!StructuredChatProtocolLimits.validMessageId(normalizedId)) {
            return false;
        }
        MinecraftServer server = senderPlayer.level().getServer();
        if (server == null) {
            return false;
        }
        ensureServerScope(server);
        RecentMessage recent;
        synchronized (RECENT_MESSAGES) {
            recent = RECENT_MESSAGES.get(normalizedId);
            if (recent == null || !recent.authorId().equals(senderPlayer.getUUID())) {
                return false;
            }
            RECENT_MESSAGES.remove(normalizedId);
        }
        StructuredChatMutation mutation = StructuredChatMutation.retracted(normalizedId, System.currentTimeMillis());
        synchronized (RECENT_MESSAGES) {
            rememberRetraction(normalizedId, mutation.serverTimestampMs());
        }
        CHAT_HISTORY.retract(server, normalizedId);
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (Net.canSendToClient(target, ServerMediaPayloads.S2CChatMutation.TYPE)) {
                Net.sendToClient(target, ServerMediaPayloads.S2CChatMutation.fromMutation(mutation));
            }
        }
        return true;
    }

    public static void replayRecentMutations(ServerPlayer player) {
        if (player == null || !Net.canSendToClient(player, ServerMediaPayloads.S2CChatMutation.TYPE)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ensureServerScope(server);
        long now = System.currentTimeMillis();
        synchronized (RECENT_MESSAGES) {
            pruneRetractions(now);
            RETRACTED_MESSAGES.forEach((messageId, timestampMs) ->
                    Net.sendToClient(player, ServerMediaPayloads.S2CChatMutation.fromMutation(
                            StructuredChatMutation.retracted(messageId, timestampMs))));
        }
    }

    public static void onPlayerDisconnect(UUID playerId) {
        ServerRequestLimiter.discard(playerId);
    }

    public static List<StructuredChatEnvelope> historyAfter(ServerPlayer player, long afterTimestampMs, int limit) {
        if (player == null || !ServerMediaServerConfig.get().chatHistoryEnabled) {
            return List.of();
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return List.of();
        }
        ensureServerScope(server);
        List<StructuredChatEnvelope> messages = CHAT_HISTORY.after(server, afterTimestampMs, limit);
        for (StructuredChatEnvelope message : messages) {
            grantAttachmentReads(player.getUUID(), message.attachments());
        }
        return messages;
    }

    private static void broadcast(PlayerList playerList, StructuredChatEnvelope envelope) {
        StructuredChatMessage legacyMessage = legacyProjection(envelope);
        AttachmentRouteDescriptor firstAttachment = firstAttachmentDescriptor(legacyMessage);
        for (ServerPlayer target : playerList.getPlayers()) {
            int route = routeFor(target);
            if (route == ROUTE_STRUCTURED_V2) {
                Net.sendToClient(target, ServerMediaPayloads.S2CStructuredChatV2.fromEnvelope(envelope));
                continue;
            }
            if (route == ROUTE_STRUCTURED_MESSAGE
                    && sendStructuredMessage(target, legacyMessage)) {
                continue;
            }
            if (firstAttachment != null
                    && route == ROUTE_STRUCTURED_ATTACHMENT
                    && sendStructuredAttachment(target, envelope.author().displayName(), firstAttachment)) {
                continue;
            }
            Component out = switch (route) {
                case ROUTE_STRUCTURED_V2, ROUTE_STRUCTURED_MESSAGE, ROUTE_STRUCTURED_ATTACHMENT, ROUTE_BRACKET_COMPAT ->
                    buildBracketProtocolMessage(
                            envelope.author().displayName(),
                            firstAttachment == null ? textFallback(legacyMessage) : firstAttachment.bracketMessage());
                default -> firstAttachment == null
                        ? Component.literal("<" + envelope.author().displayName() + "> " + legacyMessage.fallbackText())
                        : buildVanillaMessage(envelope.author().displayName(), firstAttachment);
            };
            target.sendSystemMessage(out, false);
        }
    }

    private static int routeFor(ServerPlayer target) {
        if (Net.canSendToClient(target, ServerMediaPayloads.S2CStructuredChatV2.TYPE)) {
            return ROUTE_STRUCTURED_V2;
        }
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

    private static StructuredChatEnvelope createEnvelope(
            ServerPlayer sender,
            StructuredChatSubmission submission,
            StructuredReplySummary reply) {
        long timestamp = System.currentTimeMillis();
        int compatFlags = StructuredChatMessage.COMPAT_VANILLA_SAFE_TEXT;
        if (submission.hasAttachments()) {
            compatFlags |= StructuredChatMessage.COMPAT_BRACKET_PROTOCOL;
        }
        return new StructuredChatEnvelope(
                StructuredChatEnvelope.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                submission.clientNonce(),
                timestamp,
                authorSnapshot(sender),
                "player",
                submission.plainText(),
                submission.segments(),
                submission.attachments(),
                submissionFallback(submission),
                compatFlags,
                reply);
    }

    private static StructuredChatAuthor authorSnapshot(ServerPlayer sender) {
        PlayerTeam team = sender.getTeam();
        String teamName = team == null ? "" : team.getName();
        String prefix = team == null ? "" : team.getPlayerPrefix().getString();
        String suffix = team == null ? "" : team.getPlayerSuffix().getString();
        //? if >=26.2 {
        int color = team == null
                ? StructuredChatAuthor.NO_TEAM_COLOR
                : team.getColor()
                        .map(net.minecraft.world.scores.TeamColor::rgb)
                        .orElse(StructuredChatAuthor.NO_TEAM_COLOR);
        //? } else {
        /* int color = team == null || team.getColor().getColor() == null */
        /*         ? StructuredChatAuthor.NO_TEAM_COLOR */
        /*         : team.getColor().getColor(); */
        //? }
        return new StructuredChatAuthor(
                sender.getUUID().toString(),
                sender.getDisplayName().getString(),
                teamName,
                prefix,
                suffix,
                color);
    }

    private static StructuredChatMessage legacyProjection(StructuredChatEnvelope envelope) {
        String readableText = withReplyFallback(envelope.replyTo(), envelope.plainText());
        String readableFallback = withReplyFallback(envelope.replyTo(), envelope.fallbackText());
        return new StructuredChatMessage(
                StructuredChatMessage.CURRENT_SCHEMA_VERSION,
                envelope.clientNonce(),
                envelope.author().displayName(),
                readableText,
                envelope.segments(),
                envelope.attachments(),
                readableFallback,
                envelope.compatFlags());
    }

    private static String submissionFallback(StructuredChatSubmission submission) {
        if (submission.attachments().isEmpty()) {
            return submission.plainText();
        }
        return buildBracketFallbackPayload(submission.plainText(), submission.attachments().getFirst());
    }

    private static String withReplyFallback(StructuredReplySummary reply, String body) {
        String safeBody = body == null ? "" : body;
        if (reply == null) {
            return safeBody;
        }
        String quote = "↪ " + reply.authorDisplayName() + ": " + reply.excerpt();
        return safeBody.isBlank() ? quote : quote + "\n" + safeBody;
    }

    private static Optional<StructuredReplySummary> resolveReply(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        synchronized (RECENT_MESSAGES) {
            RecentMessage message = RECENT_MESSAGES.get(messageId.trim());
            return message == null
                    ? Optional.empty()
                    : Optional.of(new StructuredReplySummary(
                            messageId,
                            message.authorDisplayName(),
                            message.excerpt()));
        }
    }

    private static void remember(StructuredChatEnvelope envelope, UUID authorId) {
        String excerpt = envelope.plainText().replaceAll("\\s+", " ").trim();
        if (excerpt.isBlank() && !envelope.attachments().isEmpty()) {
            excerpt = "[" + typeLabel(envelope.attachments().getFirst().typeWire()) + "]";
        }
        if (excerpt.length() > MAX_REPLY_EXCERPT_CHARS) {
            excerpt = excerpt.substring(0, MAX_REPLY_EXCERPT_CHARS - 1) + "…";
        }
        synchronized (RECENT_MESSAGES) {
            RECENT_MESSAGES.put(envelope.messageId(), new RecentMessage(
                    authorId,
                    envelope.author().displayName(),
                    excerpt));
            while (RECENT_MESSAGES.size() > MAX_RECENT_MESSAGES) {
                String eldest = RECENT_MESSAGES.keySet().iterator().next();
                RECENT_MESSAGES.remove(eldest);
            }
        }
    }

    private static void ensureServerScope(MinecraftServer server) {
        synchronized (RECENT_MESSAGES) {
            if (activeServer == server) {
                return;
            }
            RECENT_MESSAGES.clear();
            RETRACTED_MESSAGES.clear();
            ServerRequestLimiter.clear();
            activeServer = server;
            CHAT_HISTORY.bind(server);
        }
    }

    private static boolean attachmentsAllowed(ServerPlayer sender, StructuredChatSubmission submission) {
        for (StructuredAttachment attachment : submission.attachments()) {
            if (attachment.hasMedia()) {
                Optional<StoredMedia> stored = ServerMediaService.get(attachment.mediaId());
                if (stored.isEmpty()
                        || !normalizeType(attachment.typeWire()).equals(stored.get().typeWire())
                        || !ServerMediaService.isOwner(sender.getUUID(), attachment.mediaId())) {
                    return false;
                }
            }
            if (attachment.fallbackUrl() != null
                    && !externalAttachmentAllowed(attachment.fallbackUrl())) {
                return false;
            }
        }
        return true;
    }

    private static boolean externalAttachmentAllowed(String url) {
        return url == null
                || url.isBlank()
                || ServerMediaUrl.isServerMediaUrl(url)
                || (ServerMediaServerConfig.get().allowExternalAttachmentUrls
                        && ExternalMediaUrlPolicy.isAllowed(url));
    }

    private static void grantAttachmentReads(PlayerList playerList, List<StructuredAttachment> attachments) {
        List<UUID> playerIds = playerList.getPlayers().stream().map(ServerPlayer::getUUID).toList();
        for (StructuredAttachment attachment : attachments) {
            if (attachment.hasMedia()) {
                ServerMediaService.grantReadAccess(attachment.mediaId(), playerIds);
            }
        }
    }

    private static void grantAttachmentReads(UUID playerId, List<StructuredAttachment> attachments) {
        for (StructuredAttachment attachment : attachments) {
            if (attachment.hasMedia()) {
                ServerMediaService.grantReadAccess(attachment.mediaId(), List.of(playerId));
            }
        }
    }

    private static void rememberRetraction(String messageId, long timestampMs) {
        RETRACTED_MESSAGES.put(messageId, timestampMs);
        pruneRetractions(timestampMs);
        while (RETRACTED_MESSAGES.size() > MAX_RECENT_MESSAGES) {
            String eldest = RETRACTED_MESSAGES.keySet().iterator().next();
            RETRACTED_MESSAGES.remove(eldest);
        }
    }

    private static void pruneRetractions(long nowMs) {
        long cutoff = Math.max(0L, nowMs - RETRACTED_TOMBSTONE_TTL_MS);
        RETRACTED_MESSAGES.entrySet().removeIf(entry -> entry.getValue() < cutoff);
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

    static AttachmentRouteDescriptor descriptorForBracketProtocol(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = CHATUPGRADE_PAYLOAD.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        int payloadStart = matcher.start();
        int payloadEnd = matcher.end();
        String fields = matcher.group(1);
        if (matcher.find()) {
            return null;
        }
        String name = Component.translatable("chatupgrade.vanilla.default_name").getString();
        String type = "image";
        String url = null;
        boolean sawName = false;
        boolean sawType = false;
        for (String part : fields.split(",", -1)) {
            int idx = part.indexOf('=');
            if (idx <= 0 || idx >= part.length() - 1) {
                return null;
            }
            String key = part.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(idx + 1).trim();
            if (value.isBlank()) {
                return null;
            }
            if ("name".equals(key)) {
                if (sawName) {
                    return null;
                }
                name = value;
                sawName = true;
            } else if ("type".equals(key)) {
                if (sawType || !("image".equalsIgnoreCase(value)
                        || "audio".equalsIgnoreCase(value)
                        || "video".equalsIgnoreCase(value))) {
                    return null;
                }
                type = value.toLowerCase(Locale.ROOT);
                sawType = true;
            } else if ("url".equals(key)) {
                if (url != null) {
                    return null;
                }
                url = value;
            } else {
                return null;
            }
        }
        if (url == null
                || !(ServerMediaUrl.isServerMediaUrl(url) || ExternalMediaUrlPolicy.isAllowed(url))) {
            return null;
        }
        String normalizedType = normalizeType(type);
        String visibleText = stripPayload(raw, payloadStart, payloadEnd).trim();
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

    private record RecentMessage(UUID authorId, String authorDisplayName, String excerpt) {
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