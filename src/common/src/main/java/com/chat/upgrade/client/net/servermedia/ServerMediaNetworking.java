package com.chat.upgrade.client.net.servermedia;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
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
import com.chat.upgrade.client.history.ChatHistorySession;
import com.chat.upgrade.client.history.ChatHistoryStore;
import com.chat.upgrade.client.history.ChatHistoryStore.HistorySnapshot;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.ui.chat.InlineEmojiCodec;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.UpgradePhantomCoordinator;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.state.ChatAuthor;
import com.chat.upgrade.client.ui.chat.state.ChatMessageKind;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.state.ChatTeamSnapshot;
import com.chat.upgrade.client.ui.chat.state.RichChatIngress;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageSource;
import com.chat.upgrade.client.ui.chat.state.RichChatProjection;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionCoordinator;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionService;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;
import com.chat.upgrade.net.ServerMediaId;
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
    private static final int MAX_CONCURRENT_INCOMING_MEDIA = 4;
    private static final long INCOMING_MEDIA_TIMEOUT_MS = 30_000L;
    private static final long PENDING_REQUEST_TIMEOUT_MS = 30_000L;
    private static final ConcurrentHashMap<String, IncomingMediaAssembly> INCOMING = new ConcurrentHashMap<>();
    private static final PendingClientRequestRegistry<String> UPLOADS = new PendingClientRequestRegistry<>(
            4,
            PENDING_REQUEST_TIMEOUT_MS);
    private static final PendingClientRequestRegistry<StructuredAttachment> ATTACHMENTS =
            new PendingClientRequestRegistry<>(16, PENDING_REQUEST_TIMEOUT_MS);
    private static final SecureRandom RNG = new SecureRandom();
    private static final AtomicBoolean SESSION_OPEN = new AtomicBoolean(false);
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile boolean capabilityAnnounced = false;
    private static volatile String historySessionKey = "";
    private static volatile long historyAfterTimestampMs;
    private static volatile boolean historyRecoveryPending;
    private static volatile int historyRecoveredCount;

    private ServerMediaNetworking() {
    }

    public static void onClientDisconnect() {
        if (!SESSION_OPEN.compareAndSet(true, false)) {
            return;
        }
        saveClientHistory();
        clearHistoryRecovery();
        historySessionKey = "";
        INCOMING.clear();
        UPLOADS.clear();
        ATTACHMENTS.clear();
        capabilityAnnounced = false;
        ChatUpgradeClientBootstrap.clearAllRuntimeState();
    }

    public static void onClientJoin() {
        ChatUpgradeClientBootstrap.clearAllRuntimeState();
        SESSION_OPEN.set(true);
        restoreClientHistory();
    }

    public static void onClientTick() {
        long nowMs = System.currentTimeMillis();
        cleanupExpiredIncoming(nowMs);
        UPLOADS.cleanup(nowMs);
        ATTACHMENTS.cleanup(nowMs);
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

        r.clientHandler(ServerMediaPayloads.S2CChatHistoryEntry.TYPE, (payload, context) -> {
            payload.toEnvelope().ifPresent(envelope -> context.execute(() -> handleChatHistoryEntry(envelope)));
        });

        r.clientHandler(ServerMediaPayloads.S2CChatHistoryComplete.TYPE, (payload, context) ->
                context.execute(() -> finishChatHistoryRecovery()));

        r.clientHandler(ServerMediaPayloads.S2CMediaInit.TYPE, (payload, context) ->
                handleIncomingMediaInit(payload));

        r.clientHandler(ServerMediaPayloads.S2CMediaChunk.TYPE, (payload, context) -> {
            String mediaId = normalizeMediaId(payload.mediaId());
            if (mediaId == null) {
                return;
            }
            IncomingMediaAssembly assembly = INCOMING.get(mediaId);
            if (assembly == null) {
                return;
            }
            IncomingMediaAssembly.AcceptStatus status = assembly.acceptChunk(
                    payload.idx(), payload.chunk(), System.currentTimeMillis());
            if (status == IncomingMediaAssembly.AcceptStatus.PENDING) {
                return;
            }
            INCOMING.remove(mediaId, assembly);
            if (status == IncomingMediaAssembly.AcceptStatus.REJECTED) {
                ServerMediaClient.forgetRequest(mediaId);
                ChatUpgrade.LOGGER.warn("chat-upgrade: rejected malformed server media chunk for {}", mediaId);
                return;
            }
            byte[] body = assembly.completedBody();
            context.execute(() -> ServerMediaClient.acceptMediaBytes(
                    assembly.mediaId(),
                    assembly.type(),
                    assembly.contentType(),
                    assembly.fingerprint(),
                    body));
        });

        r.clientHandler(ServerMediaPayloads.S2CMediaError.TYPE, (payload, context) -> {
            String mediaId = normalizeMediaId(payload.mediaId());
            ChatUpgrade.LOGGER.warn("chat-upgrade: server media error mediaId={} msg={}", mediaId,
                    payload.message());
            if (mediaId != null) {
                INCOMING.remove(mediaId);
                ServerMediaClient.forgetRequest(mediaId);
            }
        });

        r.clientHandler(ServerMediaPayloads.S2CUploadAck.TYPE, (payload, context) ->
                UPLOADS.complete(
                        payload.uploadId(),
                        Optional.ofNullable(payload.specialUrl()).filter(s -> !s.isBlank())));

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
            ATTACHMENTS.fail(payload.requestId());
            ChatUpgrade.LOGGER.warn("chat-upgrade: server attachment error attachmentId={} mediaId={} msg={}",
                    payload.attachmentId(), payload.mediaId(), payload.message());
        });
    }

    private static void handleIncomingMediaInit(ServerMediaPayloads.S2CMediaInit payload) {
        long nowMs = System.currentTimeMillis();
        cleanupExpiredIncoming(nowMs);
        if (!ServerMediaId.isValid(payload.mediaId())
                || !("image".equals(payload.typeWire())
                        || "audio".equals(payload.typeWire())
                        || "video".equals(payload.typeWire()))) {
            rejectIncoming(payload.mediaId(), "invalid metadata");
            return;
        }
        String mediaId = payload.mediaId().toLowerCase(java.util.Locale.ROOT);
        String expectedType = ServerMediaClient.expectedType(mediaId);
        if (expectedType == null || !expectedType.equals(payload.typeWire())) {
            rejectIncoming(mediaId, "unsolicited or mismatched response");
            return;
        }
        synchronized (INCOMING) {
            if (INCOMING.containsKey(mediaId)) {
                return;
            }
            long pendingBytes = INCOMING.values().stream()
                    .mapToLong(IncomingMediaAssembly::declaredBytes)
                    .sum();
            long pendingLimit = 2L * ChatUpgradeConfig.ABSOLUTE_MAX_RECEIVE_BYTES;
            if (INCOMING.size() >= MAX_CONCURRENT_INCOMING_MEDIA
                    || payload.totalLen() <= 0
                    || payload.totalLen() > pendingLimit - pendingBytes) {
                rejectIncoming(mediaId, "too many pending responses");
                return;
            }
            Optional<IncomingMediaAssembly> candidate = IncomingMediaAssembly.create(
                    mediaId,
                    InlineResourceType.fromWire(payload.typeWire()),
                    payload.contentType(),
                    payload.md5Hex(),
                    payload.totalLen(),
                    payload.totalChunks(),
                    ServerMediaClient.capability(),
                    ChatUpgradeConfig.get().maxReceiveBytes,
                    nowMs);
            if (candidate.isEmpty()) {
                rejectIncoming(mediaId, "allocation limits exceeded");
                return;
            }
            INCOMING.put(mediaId, candidate.get());
        }
    }

    private static void cleanupExpiredIncoming(long nowMs) {
        INCOMING.entrySet().removeIf(entry -> {
            if (!entry.getValue().isExpired(nowMs, INCOMING_MEDIA_TIMEOUT_MS)) {
                return false;
            }
            ServerMediaClient.forgetRequest(entry.getKey());
            return true;
        });
    }

    private static void rejectIncoming(String mediaId, String reason) {
        if (mediaId != null) {
            INCOMING.remove(mediaId);
            ServerMediaClient.forgetRequest(mediaId);
        }
        ChatUpgrade.LOGGER.warn("chat-upgrade: rejected server media response for {}: {}", mediaId, reason);
    }

    private static void handleChatHistoryEntry(StructuredChatEnvelope envelope) {
        if (!historyRecoveryPending
                || envelope.serverTimestampMs() <= historyAfterTimestampMs
                || RichChatStateStore.containsMessage(envelope.messageId())) {
            return;
        }
        List<RichAttachment> attachments = envelope.attachments().stream()
                .peek(ServerMediaClient::rememberAttachment)
                .map(RichAttachment::fromStructured)
                .filter(RichAttachment::hasRenderableUrl)
                .toList();
        InlineEmojiCodec.DecodedEmoji emojiDecoded = InlineEmojiCodec.decodeIncoming(Component.literal(envelope.plainText()));
        RichChatMessage restored = new RichChatMessage(
                envelope.messageId(),
                toClientAuthor(envelope.author()),
                toClientKind(envelope.kind()),
                null,
                null,
                envelope.serverTimestampMs(),
                toClientReply(envelope.replyTo()),
                0,
                emojiDecoded.modified(),
                emojiDecoded.modified(),
                envelope.plainText(),
                envelope.fallbackText(),
                attachments,
                emojiDecoded.slots(),
                RichChatMessageSource.STRUCTURED_PACKET,
                null,
                com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus.VISIBLE);
        RichChatStateStore.restoreNewestFirst(List.of(restored));
        projectStoredMessage(restored);
        historyRecoveredCount++;
    }

    private static void finishChatHistoryRecovery() {
        if (!historyRecoveryPending) {
            return;
        }
        int recovered = historyRecoveredCount;
        clearHistoryRecovery();
        if (recovered > 0) {
            displayHistoryMarker(Component.translatable("chatupgrade.history.server_restored", recovered));
        }
    }

    private static void restoreClientHistory() {
        clearHistoryRecovery();
        historySessionKey = ChatHistorySession.resolve(Minecraft.getInstance());
        if (Boolean.FALSE.equals(ChatUpgradeConfig.get().chatHistoryEnabled)) {
            return;
        }
        HistorySnapshot snapshot = ChatHistoryStore.load(historySessionKey);
        List<RichChatMessage> restored = snapshot.messages().stream()
                .map(ChatHistoryStore.HistoryMessage::toMessage)
                .toList();
        RichChatStateStore.restoreNewestFirst(restored);
        for (int index = restored.size() - 1; index >= 0; index--) {
            projectStoredMessage(restored.get(index));
        }
        historyAfterTimestampMs = snapshot.lastExitAtMs();
        if (!restored.isEmpty() && historyAfterTimestampMs > 0L) {
            String leftAt = HISTORY_TIME_FORMAT.format(Instant.ofEpochMilli(historyAfterTimestampMs)
                    .atZone(ZoneId.systemDefault()));
            displayHistoryMarker(Component.translatable("chatupgrade.history.left_at", leftAt));
        }
        if (Net.canSendToServer(ServerMediaPayloads.C2SRequestChatHistory.TYPE)) {
            historyRecoveryPending = true;
            Net.sendToServer(new ServerMediaPayloads.C2SRequestChatHistory(
                    historyAfterTimestampMs,
                    ChatUpgradeConfig.get().chatHistoryMaxMessages));
        }
    }

    private static void saveClientHistory() {
        if (Boolean.FALSE.equals(ChatUpgradeConfig.get().chatHistoryEnabled) || historySessionKey.isBlank()) {
            return;
        }
        ChatHistoryStore.save(
                historySessionKey,
                System.currentTimeMillis(),
                RichChatStateStore.snapshotNewestFirst(),
                ChatUpgradeConfig.get().chatHistoryMaxMessages);
    }

    private static void displayHistoryMarker(Component text) {
        RichChatMessage marker = new RichChatMessage(
                "",
                ChatAuthor.system(),
                ChatMessageKind.SYSTEM,
                null,
                null,
                System.currentTimeMillis(),
                null,
                0,
                text,
                text,
                text.getString(),
                text.getString(),
                List.of(),
                List.of(),
                RichChatMessageSource.LOCAL_SYSTEM,
                null,
                com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus.VISIBLE);
        RichChatStateStore.restoreNewestFirst(List.of(marker));
        projectStoredMessage(marker);
    }

    private static void clearHistoryRecovery() {
        historyRecoveryPending = false;
        historyRecoveredCount = 0;
        historyAfterTimestampMs = 0L;
    }

    public static void sendRequest(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        Net.sendToServer(new ServerMediaPayloads.C2SRequestMedia(mediaId));
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
        RichChatMessage stored = recordLegacyStructuredMessage(
                "",
                payload.senderName(),
                payload.text(),
                payload.text(),
                List.of(attachment));
        projectStoredMessage(stored);
    }

    private static void handleStructuredChatMessage(StructuredChatMessage message) {
        List<RichAttachment> attachments = message.attachments().stream()
                .peek(ServerMediaClient::rememberAttachment)
                .map(RichAttachment::fromStructured)
                .filter(RichAttachment::hasRenderableUrl)
                .toList();
        RichChatMessage stored = recordLegacyStructuredMessage(
                message.clientNonce(),
                message.senderName(),
                message.plainText(),
                message.fallbackText(),
                attachments);
        projectStoredMessage(stored);
    }

    private static void handleStructuredChatV2(StructuredChatEnvelope envelope) {
        List<RichAttachment> attachments = envelope.attachments().stream()
                .peek(ServerMediaClient::rememberAttachment)
                .map(RichAttachment::fromStructured)
                .filter(RichAttachment::hasRenderableUrl)
                .toList();
        Component component = Component.literal(envelope.plainText());
        InlineEmojiCodec.DecodedEmoji emojiDecoded = InlineEmojiCodec.decodeIncoming(component);
        RichChatMessage stored = RichChatIngress.recordStructured(
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
        projectStoredMessage(stored);
    }

    private static ChatAuthor toClientAuthor(StructuredChatAuthor author) {
        StructuredChatAuthor safeAuthor = author == null ? StructuredChatAuthor.legacy("") : author;
        UUID playerId = null;
        try {
            if (!safeAuthor.playerUuid().isBlank()) {
                playerId = UUID.fromString(safeAuthor.playerUuid());
            }
        } catch (IllegalArgumentException ignored) {
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean localPlayer = playerId != null
                && minecraft != null
                && minecraft.player != null
                && playerId.equals(minecraft.player.getUUID());
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

    private static RichChatMessage recordLegacyStructuredMessage(
            String messageId,
            String senderName,
            String plainText,
            String fallbackText,
            List<RichAttachment> attachments) {
        String visibleSender = senderName == null || senderName.isBlank() ? "?" : senderName;
        String body = plainText == null ? "" : plainText;
        InlineEmojiCodec.DecodedEmoji emojiDecoded = InlineEmojiCodec.decodeIncoming(
                Component.literal("<" + visibleSender + "> " + body));
        return RichChatIngress.recordLegacy(
                messageId,
                senderName,
                ChatMessageKind.PLAYER,
                emojiDecoded.modified(),
                fallbackText,
                attachments,
                emojiDecoded.slots(),
                RichChatMessageSource.STRUCTURED_PACKET);
    }

    private static void projectStoredMessage(RichChatMessage message) {
        message.attachments().stream()
                .filter(RichAttachment::hasRenderableUrl)
                .forEach(ServerMediaNetworking::preloadStructuredAttachmentMedia);
        Minecraft minecraft = Minecraft.getInstance();
        ChatComponent chat = MinecraftGuiBridge.chat(minecraft);
        if (chat == null) {
            return;
        }
        RichChatProjection projection = RichChatProjectionService.project(message);
        RichChatProjectionCoordinator.prepareNext(projection);
        InlineEmojiCoordinator.setPendingSlots(message.inlineEmojiSlots());
        if (projection.hasMediaBlock()) {
            UpgradePhantomCoordinator.setPendingDecoded(projection.mediaAttachment());
        }
        chat.addServerSystemMessage(projection.textProjection());
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

    public static CompletableFuture<Optional<StructuredAttachment>> submitAttachment(StructuredAttachment attachment) {
        if (attachment == null || !ServerMediaClient.capability().attachmentMetadataEnabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        PendingClientRequestRegistry.Registration<StructuredAttachment> registration = ATTACHMENTS.begin(
                ServerMediaNetworking::nextRequestId,
                System.currentTimeMillis());
        if (registration == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long requestId = registration.requestId();
        CompletableFuture<Optional<StructuredAttachment>> fut = registration.future();
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
            ATTACHMENTS.fail(requestId);
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
        PendingClientRequestRegistry.Registration<StructuredAttachment> registration = ATTACHMENTS.begin(
                ServerMediaNetworking::nextRequestId,
                System.currentTimeMillis());
        if (registration == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long requestId = registration.requestId();
        CompletableFuture<Optional<StructuredAttachment>> fut = registration.future();
        try {
            Net.sendToServer(new ServerMediaPayloads.C2SRequestAttachmentMeta(
                    requestId,
                    wire(attachmentId),
                    wire(mediaId)));
        } catch (Exception ex) {
            ATTACHMENTS.fail(requestId);
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

        PendingClientRequestRegistry.Registration<String> registration = UPLOADS.begin(
                ServerMediaNetworking::nextRequestId,
                System.currentTimeMillis());
        if (registration == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long uploadId = registration.requestId();
        CompletableFuture<Optional<String>> fut = registration.future();

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
            UPLOADS.fail(uploadId);
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to upload server media: {}", ex.getMessage());
        }

        return fut;
    }

    private static long nextRequestId() {
        long value = 0L;
        while (value == 0L) {
            value = RNG.nextLong();
        }
        return value;
    }

    private static void completeAttachment(long requestId, Optional<StructuredAttachment> attachmentOpt) {
        ATTACHMENTS.complete(requestId, attachmentOpt);
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

    private static @Nullable String normalizeMediaId(@Nullable String value) {
        if (!ServerMediaId.isValid(value)) {
            return null;
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

