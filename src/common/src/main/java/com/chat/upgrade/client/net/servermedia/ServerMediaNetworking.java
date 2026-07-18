package com.chat.upgrade.client.net.servermedia;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeClientBootstrap;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.InlineEmojiCodec;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.ui.chat.UpgradePhantomCoordinator;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.state.ChatAuthor;
import com.chat.upgrade.client.ui.chat.state.ChatMessageKind;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.state.ChatTeamSnapshot;
import com.chat.upgrade.client.ui.chat.state.RichChatIngress;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageSource;
import com.chat.upgrade.client.ui.chat.state.RichChatProjection;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionCoordinator;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionService;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.net.StructuredChatAuthor;
import com.chat.upgrade.net.StructuredChatEnvelope;
import com.chat.upgrade.net.StructuredChatMessage;
import com.chat.upgrade.net.StructuredReplySummary;

import com.chat.upgrade.platform.net.Net;
import com.chat.upgrade.platform.net.NetworkRegistrar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

public final class ServerMediaNetworking {
    private static final ConcurrentHashMap<String, IncomingMediaAssembly> INCOMING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CompletableFuture<Optional<String>>> UPLOADS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CompletableFuture<Optional<StructuredAttachment>>> ATTACHMENTS = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();
    private static final AtomicBoolean SESSION_OPEN = new AtomicBoolean(false);
    private static volatile boolean capabilityAnnounced = false;

    private ServerMediaNetworking() {
    }

    public static void onClientDisconnect() {
        if (!SESSION_OPEN.compareAndSet(true, false)) {
            return;
        }
        INCOMING.clear();
        UPLOADS.forEach((id, fut) -> fut.complete(Optional.empty()));
        UPLOADS.clear();
        ATTACHMENTS.forEach((id, fut) -> fut.complete(Optional.empty()));
        ATTACHMENTS.clear();
        capabilityAnnounced = false;
        ChatUpgradeClientBootstrap.clearAllRuntimeState();
    }

    public static void onClientJoin() {
        ChatUpgradeClientBootstrap.clearAllRuntimeState();
        SESSION_OPEN.set(true);
        sendChatInputMode();
    }

    public static void registerClientHandlers(NetworkRegistrar r) {
        r.clientHandler(ServerMediaPayloads.S2CCapability.TYPE, (payload, context) -> {
            ServerMediaCapability.StorageMode mode = payload.storageMode() == 1
                    ? ServerMediaCapability.StorageMode.DISK
                    : ServerMediaCapability.StorageMode.MEMORY;
            context.execute(() -> {
                ServerMediaClient.setCapability(
                        new ServerMediaCapability(payload.enabled(), payload.maxSingleBytes(), payload.maxChunkBytes(), mode,
                                payload.ttlSeconds(), false, 0));
                boolean uploadReady = payload.enabled() && payload.maxSingleBytes() > 0 && payload.maxChunkBytes() > 0;
                if (uploadReady && !capabilityAnnounced) {
                    capabilityAnnounced = true;
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(
                                Component.translatable("chatupgrade.server_media.upload_ready").withStyle(ChatFormatting.GREEN));
                    }
                }
            });
        });

        r.clientHandler(ServerMediaPayloads.S2CAttachmentCapability.TYPE, (payload, context) -> {
            context.execute(() -> ServerMediaClient.setAttachmentCapability(
                    payload.enabled(),
                    payload.schemaVersion()));
        });

        r.clientHandler(ServerMediaPayloads.S2CStructuredChatAttachment.TYPE, (payload, context) -> {
            context.execute(() -> handleStructuredChatAttachment(payload));
        });

        r.clientHandler(ServerMediaPayloads.S2CStructuredChatMessage.TYPE, (payload, context) -> {
            payload.toMessage().ifPresent(message -> context.execute(() -> handleStructuredChatMessage(message)));
        });

        r.clientHandler(ServerMediaPayloads.S2CStructuredChatV2.TYPE, (payload, context) -> {
            payload.toEnvelope().ifPresent(envelope -> context.execute(() -> handleStructuredChatV2(envelope)));
        });

        r.clientHandler(ServerMediaPayloads.S2CChatMutation.TYPE, (payload, context) -> {
            payload.toMutation().ifPresent(mutation -> context.execute(() -> {
                if (com.chat.upgrade.net.StructuredChatMutation.RETRACTED.equals(mutation.mutation())) {
                    RichChatStateStore.delete(mutation.messageId());
                    ChatTextSelectionState.clearIfMessage(mutation.messageId());
                }
            }));
        });

        r.clientHandler(ServerMediaPayloads.S2CMediaInit.TYPE, (payload, context) -> {
            InlineResourceType type = InlineResourceType.fromWire(payload.typeWire());
            INCOMING.put(payload.mediaId(), new IncomingMediaAssembly(
                    payload.mediaId(),
                    type,
                    payload.contentType(),
                    payload.md5Hex(),
                    payload.totalLen(),
                    payload.totalChunks()));
        });

        r.clientHandler(ServerMediaPayloads.S2CMediaChunk.TYPE, (payload, context) -> {
            IncomingMediaAssembly asm = INCOMING.get(payload.mediaId());
            if (asm == null) {
                return;
            }
            boolean done = asm.acceptChunk(payload.idx(), payload.chunk());
            if (!done) {
                return;
            }
            INCOMING.remove(payload.mediaId(), asm);
            byte[] body = asm.build();
            context.execute(() -> ServerMediaClient.acceptMediaBytes(
                    asm.mediaId(),
                    asm.type(),
                    asm.contentType(),
                    asm.md5Hex(),
                    body));
        });

        r.clientHandler(ServerMediaPayloads.S2CMediaError.TYPE, (payload, context) -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: server media error mediaId={} msg={}", payload.mediaId(),
                    payload.message());
            INCOMING.remove(payload.mediaId());
        });

        r.clientHandler(ServerMediaPayloads.S2CUploadAck.TYPE, (payload, context) -> {
            CompletableFuture<Optional<String>> fut = UPLOADS.remove(payload.uploadId());
            if (fut != null) {
                fut.complete(Optional.ofNullable(payload.specialUrl()).filter(s -> !s.isBlank()));
            }
        });

        r.clientHandler(ServerMediaPayloads.S2CAttachmentAck.TYPE, (payload, context) -> {
            completeAttachment(payload.requestId(), toStructuredAttachment(
                    payload.schemaVersion(),
                    payload.attachmentId(),
                    payload.mediaId(),
                    payload.typeWire(),
                    payload.displayName(),
                    payload.fallbackUrl()));
        });

        r.clientHandler(ServerMediaPayloads.S2CAttachmentMeta.TYPE, (payload, context) -> {
            completeAttachment(payload.requestId(), toStructuredAttachment(
                    payload.schemaVersion(),
                    payload.attachmentId(),
                    payload.mediaId(),
                    payload.typeWire(),
                    payload.displayName(),
                    payload.fallbackUrl()));
        });

        r.clientHandler(ServerMediaPayloads.S2CAttachmentError.TYPE, (payload, context) -> {
            CompletableFuture<Optional<StructuredAttachment>> fut = ATTACHMENTS.remove(payload.requestId());
            if (fut != null) {
                fut.complete(Optional.empty());
            }
            ChatUpgrade.LOGGER.warn("chat-upgrade: server attachment error attachmentId={} mediaId={} msg={}",
                    payload.attachmentId(), payload.mediaId(), payload.message());
        });
    }

    public static void sendRequest(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        Net.sendToServer(new ServerMediaPayloads.C2SRequestMedia(mediaId));
    }

    public static void sendChatInputMode() {
        try {
            if (!Net.canSendToServer(ServerMediaPayloads.C2SChatInputMode.TYPE)) {
                return;
            }
            Net.sendToServer(new ServerMediaPayloads.C2SChatInputMode(
                    ChatUpgradeConfig.get().chatInputMode.name()));
        } catch (Exception ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to send chat input mode: {}", ex.getMessage());
        }
    }

    private static void handleStructuredChatAttachment(ServerMediaPayloads.S2CStructuredChatAttachment payload) {
        Optional<StructuredAttachment> structuredOpt = toStructuredAttachment(
                payload.schemaVersion(),
                payload.attachmentId(),
                payload.mediaId(),
                payload.typeWire(),
                payload.displayName(),
                payload.fallbackUrl());
        if (structuredOpt.isEmpty()) {
            return;
        }
        StructuredAttachment structured = structuredOpt.get();
        ServerMediaClient.rememberAttachment(structured);
        RichAttachment attachment = RichAttachment.fromStructured(structured);
        if (!attachment.hasRenderableUrl()) {
            return;
        }
        Component message = buildStructuredChatMessage(payload.senderName(), payload.text(), List.of(attachment));
        renderStructuredProjection(
                "",
                payload.senderName(),
                message,
                message.getString(),
                List.of(attachment),
                RichChatMessageSource.STRUCTURED_PACKET);
    }

    private static void handleStructuredChatMessage(StructuredChatMessage message) {
        List<RichAttachment> attachments = message.attachments().stream()
                .peek(ServerMediaClient::rememberAttachment)
                .map(RichAttachment::fromStructured)
                .filter(RichAttachment::hasRenderableUrl)
                .toList();
        Component component = buildStructuredChatMessage(
                message.senderName(),
                message.plainText(),
                attachments);
        if (attachments.isEmpty() && !ChatUpgradeChatPipelineGate.shouldEnhancePlainTextChat()) {
            renderStructuredPlainText(component);
            return;
        }
        renderStructuredProjection(
                message.clientNonce(),
                message.senderName(),
                component,
                message.fallbackText(),
                attachments,
                RichChatMessageSource.STRUCTURED_PACKET);
    }

    private static void handleStructuredChatV2(StructuredChatEnvelope envelope) {
        List<RichAttachment> attachments = envelope.attachments().stream()
                .peek(ServerMediaClient::rememberAttachment)
                .map(RichAttachment::fromStructured)
                .filter(RichAttachment::hasRenderableUrl)
                .toList();
        boolean takeover = ChatUpgradeChatPipelineGate.isTakeoverMode();
        Component component = takeover
                ? Component.literal(envelope.plainText())
                : buildStructuredChatMessage(
                        envelope.author().displayName(),
                        envelope.plainText(),
                        attachments);
        if (!takeover) {
            renderStructuredPlainText(component);
            return;
        }
        InlineEmojiCodec.DecodedEmoji emojiDecoded = InlineEmojiCodec.decodeIncoming(component);
        RichChatIngress.recordStructured(
                envelope.messageId(),
                toClientAuthor(envelope.author()),
                toClientKind(envelope.kind()),
                envelope.serverTimestampMs(),
                toClientReply(envelope.replyTo()),
                emojiDecoded.modified(),
                envelope.plainText(),
                envelope.fallbackText(),
                attachments,
                emojiDecoded.slots(),
                RichChatMessageSource.STRUCTURED_PACKET);
        attachments.forEach(ServerMediaNetworking::preloadStructuredAttachmentMedia);
    }

    private static ChatAuthor toClientAuthor(StructuredChatAuthor author) {
        UUID playerId = null;
        try {
            if (author != null && !author.playerUuid().isBlank()) {
                playerId = UUID.fromString(author.playerUuid());
            }
        } catch (IllegalArgumentException ignored) {
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean localPlayer = playerId != null
                && minecraft != null
                && minecraft.player != null
                && playerId.equals(minecraft.player.getUUID());
        StructuredChatAuthor safeAuthor = author == null ? StructuredChatAuthor.legacy("") : author;
        return new ChatAuthor(
                playerId,
                Component.literal(safeAuthor.displayName()),
                safeAuthor.displayName(),
                new ChatTeamSnapshot(
                        safeAuthor.teamName(),
                        safeAuthor.teamPrefix(),
                        safeAuthor.teamSuffix(),
                        safeAuthor.teamColorRgb()),
                localPlayer);
    }

    private static ChatMessageKind toClientKind(String kind) {
        if (kind == null) {
            return ChatMessageKind.PLAYER;
        }
        return switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "system" -> ChatMessageKind.SYSTEM;
            case "game" -> ChatMessageKind.GAME;
            case "announcement" -> ChatMessageKind.ANNOUNCEMENT;
            case "error" -> ChatMessageKind.ERROR;
            default -> ChatMessageKind.PLAYER;
        };
    }

    private static @Nullable ChatReplySummary toClientReply(@Nullable StructuredReplySummary reply) {
        if (reply == null) {
            return null;
        }
        return new ChatReplySummary(
                reply.messageId(),
                ChatAuthor.legacy(reply.authorDisplayName()),
                reply.excerpt());
    }

    private static void renderStructuredPlainText(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        ChatComponent chat = MinecraftGuiBridge.chat(minecraft);
        if (chat == null) {
            return;
        }
        chat.addServerSystemMessage(component);
    }

    private static void renderStructuredProjection(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            RichChatMessageSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        ChatComponent chat = MinecraftGuiBridge.chat(minecraft);
        if (chat == null) {
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            InlineEmojiCodec.DecodedEmoji emojiDecoded = InlineEmojiCodec.decodeIncoming(component);
            RichChatIngress.recordLegacy(
                    messageId,
                    senderName,
                    ChatMessageKind.PLAYER,
                    emojiDecoded.modified(),
                    fallbackText,
                    attachments,
                    emojiDecoded.slots(),
                    source);
            attachments.stream()
                    .filter(RichAttachment::hasRenderableUrl)
                    .forEach(ServerMediaNetworking::preloadStructuredAttachmentMedia);
            return;
        }
        RichChatProjection projection = RichChatProjectionService.recordAndProject(
                messageId,
                senderName,
                component,
                fallbackText,
                attachments,
                source);
        RichChatProjectionCoordinator.prepareNext(projection);
        if (projection.hasMediaBlock()) {
            beginStructuredAttachmentRender(projection.mediaAttachment());
        }
        chat.addServerSystemMessage(projection.textProjection());
    }

    private static void beginStructuredAttachmentRender(RichAttachment attachment) {
        UpgradePhantomCoordinator.setPendingDecoded(attachment);
        preloadStructuredAttachmentMedia(attachment);
    }

    private static void preloadStructuredAttachmentMedia(RichAttachment attachment) {
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return;
        }
        String url = attachment.requireRenderableUrl();
        switch (attachment.type()) {
            case IMAGE -> {
                if (!ChatUpgradeConfig.get().manualImageReveal) {
                    ImageLoader.getOrLoad(url);
                }
            }
            case AUDIO -> {
                if (!ChatUpgradeConfig.get().manualAudioReveal) {
                    AudioLoader.getOrLoad(url);
                }
            }
            case VIDEO -> {
                if (!ChatUpgradeConfig.get().manualVideoReveal) {
                    VideoLoader.getOrLoad(url);
                }
            }
        }
    }

    private static Component buildStructuredChatMessage(String senderName, String text, List<RichAttachment> attachments) {
        String prefix = "<" + (senderName == null || senderName.isBlank() ? "?" : senderName) + "> ";
        String body = text == null ? "" : text.trim();
        Component base = body.isEmpty() ? Component.literal(prefix) : Component.literal(prefix + body + " ");
        for (RichAttachment attachment : attachments) {
            if (attachment == null || !attachment.hasRenderableUrl()) {
                continue;
            }
            base = base.copy().append(UpgradeBracketCodec.buildPlaceholderComponent(
                    attachment.type(),
                    attachment.displayName(),
                    attachment.requireRenderableUrl()));
        }
        return base;
    }

    public static CompletableFuture<Optional<StructuredAttachment>> submitAttachment(StructuredAttachment attachment) {
        if (attachment == null || !ServerMediaClient.capability().attachmentMetadataEnabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long requestId = nextUploadId();
        CompletableFuture<Optional<StructuredAttachment>> fut = new CompletableFuture<>();
        ATTACHMENTS.put(requestId, fut);
        try {
            Net.sendToServer(new ServerMediaPayloads.C2SAttachMetadata(
                    requestId,
                    attachment.schemaVersion(),
                    wire(attachment.attachmentId()),
                    wire(attachment.mediaId()),
                    attachment.typeWire(),
                    attachment.displayName(),
                    wire(attachment.fallbackUrl())));
        } catch (Exception ex) {
            ATTACHMENTS.remove(requestId);
            fut.complete(Optional.empty());
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to send attachment metadata: {}", ex.getMessage());
        }
        return fut;
    }

    public static CompletableFuture<Optional<StructuredAttachment>> requestAttachment(
            @Nullable String attachmentId,
            @Nullable String mediaId) {
        if (!ServerMediaClient.capability().attachmentMetadataEnabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if ((attachmentId == null || attachmentId.isBlank()) && (mediaId == null || mediaId.isBlank())) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long requestId = nextUploadId();
        CompletableFuture<Optional<StructuredAttachment>> fut = new CompletableFuture<>();
        ATTACHMENTS.put(requestId, fut);
        try {
            Net.sendToServer(new ServerMediaPayloads.C2SRequestAttachmentMeta(
                    requestId,
                    wire(attachmentId),
                    wire(mediaId)));
        } catch (Exception ex) {
            ATTACHMENTS.remove(requestId);
            fut.complete(Optional.empty());
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to request attachment metadata: {}", ex.getMessage());
        }
        return fut;
    }

    public static CompletableFuture<Optional<String>> uploadBytes(
            InlineResourceType type,
            byte[] body,
            String fileName,
            @Nullable String contentType) {
        if (type == null || body == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        ServerMediaCapability cap = ServerMediaClient.capability();
        if (!cap.enabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (cap.maxSingleBytes() > 0 && body.length > cap.maxSingleBytes()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        int chunkSize = cap.maxChunkBytes() > 0 ? cap.maxChunkBytes() : 32 * 1024;
        chunkSize = Math.clamp(chunkSize, 1024, 256 * 1024);
        int totalChunks = (int) Math.ceil(body.length / (double) chunkSize);

        long uploadId = nextUploadId();
        CompletableFuture<Optional<String>> fut = new CompletableFuture<>();
        UPLOADS.put(uploadId, fut);

        try {
            Net.sendToServer(new ServerMediaPayloads.C2SUploadInit(
                    uploadId,
                    type.toWire(),
                    fileName == null ? "" : fileName,
                    contentType == null ? "application/octet-stream" : contentType,
                    body.length,
                    totalChunks));

            for (int i = 0; i < totalChunks; i++) {
                int from = i * chunkSize;
                int to = Math.min(body.length, from + chunkSize);
                byte[] chunk = new byte[to - from];
                System.arraycopy(body, from, chunk, 0, chunk.length);
                Net.sendToServer(new ServerMediaPayloads.C2SUploadChunk(uploadId, i, chunk));
            }
        } catch (Exception ex) {
            UPLOADS.remove(uploadId);
            fut.complete(Optional.empty());
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to upload server media: {}", ex.getMessage());
        }

        return fut;
    }

    private static long nextUploadId() {
        long v = 0L;
        while (v == 0L) {
            v = RNG.nextLong();
        }
        return v;
    }

    private static void completeAttachment(long requestId, Optional<StructuredAttachment> attachmentOpt) {
        CompletableFuture<Optional<StructuredAttachment>> fut = ATTACHMENTS.remove(requestId);
        if (fut != null) {
            fut.complete(attachmentOpt);
        }
    }

    private static Optional<StructuredAttachment> toStructuredAttachment(
            int schemaVersion,
            String attachmentId,
            String mediaId,
            String typeWire,
            String displayName,
            String fallbackUrl) {
        try {
            return Optional.of(new StructuredAttachment(
                    schemaVersion,
                    normalizeOptional(attachmentId),
                    normalizeOptional(mediaId),
                    typeWire,
                    displayName,
                    normalizeOptional(fallbackUrl)));
        } catch (IllegalArgumentException ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: invalid attachment metadata from server: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String wire(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class IncomingMediaAssembly {
        private final String mediaId;
        private final InlineResourceType type;
        private final String contentType;
        private final @Nullable String md5Hex;
        private final int totalLen;
        private final int totalChunks;
        private final byte[][] chunks;
        private int receivedChunks = 0;
        private int receivedBytes = 0;

        IncomingMediaAssembly(
                String mediaId,
                InlineResourceType type,
                String contentType,
                @Nullable String md5Hex,
                int totalLen,
                int totalChunks) {
            this.mediaId = mediaId;
            this.type = type;
            this.contentType = contentType == null ? "unknown" : contentType;
            this.md5Hex = md5Hex == null || md5Hex.isBlank() ? null : md5Hex;
            this.totalLen = Math.max(0, totalLen);
            this.totalChunks = Math.max(0, totalChunks);
            this.chunks = new byte[this.totalChunks][];
        }

        String mediaId() {
            return mediaId;
        }

        InlineResourceType type() {
            return type;
        }

        String contentType() {
            return contentType;
        }

        @Nullable
        String md5Hex() {
            return md5Hex;
        }

        synchronized boolean acceptChunk(int idx, byte[] chunk) {
            if (idx < 0 || idx >= totalChunks) {
                return false;
            }
            if (chunks[idx] != null) {
                return false;
            }
            chunks[idx] = chunk;
            receivedChunks++;
            receivedBytes += (chunk == null ? 0 : chunk.length);
            return receivedChunks == totalChunks;
        }

        synchronized byte[] build() {
            if (totalChunks == 0) {
                return new byte[0];
            }
            int len = totalLen > 0 ? totalLen : receivedBytes;
            byte[] out = new byte[len];
            int offset = 0;
            for (byte[] c : chunks) {
                if (c == null || c.length == 0) {
                    continue;
                }
                int copy = Math.min(c.length, out.length - offset);
                if (copy <= 0) {
                    break;
                }
                System.arraycopy(c, 0, out, offset, copy);
                offset += copy;
            }
            if (offset != out.length) {
                return Arrays.copyOf(out, offset);
            }
            return out;
        }
    }
}

