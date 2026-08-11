package com.chat.upgrade.server;

import java.util.List;
import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.platform.net.Net;
import com.chat.upgrade.platform.net.NetworkRegistrar;
import com.chat.upgrade.platform.net.ServerPlayContext;
import com.chat.upgrade.server.store.StoredAttachment;
import com.chat.upgrade.server.store.StoredMedia;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side media networking. Loader entry points call {@link #init()} once, register the
 * payload handlers via {@link #registerServerHandlers(NetworkRegistrar)} and wire the lifecycle
 * hooks ({@link #onServerTick}, {@link #onPlayerJoin}, {@link #onPlayerDisconnect}) to their
 * native events.
 */
public final class ServerMediaServerNetworking {
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 10;
    private static int ticks = 0;

    private ServerMediaServerNetworking() {
    }

    public static void init() {
        ServerMediaServerConfig.load();
        ServerMediaService.initFromConfig();
    }

    public static void registerServerHandlers(NetworkRegistrar r) {
        r.serverHandler(ServerMediaPayloads.C2SUploadInit.TYPE, ServerMediaServerNetworking::handleUploadInit);
        r.serverHandler(ServerMediaPayloads.C2SUploadChunk.TYPE, ServerMediaServerNetworking::handleUploadChunk);
        r.serverHandler(ServerMediaPayloads.C2SRequestMedia.TYPE, ServerMediaServerNetworking::handleRequestMedia);
        r.serverHandler(ServerMediaPayloads.C2SAttachMetadata.TYPE, ServerMediaServerNetworking::handleAttachMetadataPacket);
        r.serverHandler(ServerMediaPayloads.C2SRequestAttachmentMeta.TYPE,
                ServerMediaServerNetworking::handleRequestAttachmentMetaPacket);
        r.serverHandler(ServerMediaPayloads.C2SChatInputMode.TYPE, ServerMediaServerNetworking::handleChatInputMode);
        r.serverHandler(ServerMediaPayloads.C2SStructuredChatMessage.TYPE,
                ServerMediaServerNetworking::handleStructuredChatMessagePacket);
        r.serverHandler(ServerMediaPayloads.C2SStructuredChatV2.TYPE,
                ServerMediaServerNetworking::handleStructuredChatV2Packet);
        r.serverHandler(ServerMediaPayloads.C2SRetractChatMessage.TYPE,
                ServerMediaServerNetworking::handleRetractChatMessagePacket);
        r.serverHandler(ServerMediaPayloads.C2SRequestChatHistory.TYPE,
                ServerMediaServerNetworking::handleRequestChatHistoryPacket);
    }

    // --- Lifecycle hooks (wired by each loader to its native events) ---

    public static void onServerTick(MinecraftServer server) {
        ticks++;
        if (ticks % CLEANUP_INTERVAL_TICKS == 0) {
            ServerMediaService.cleanup();
            ServerAttachmentService.cleanup();
            ServerRequestLimiter.cleanup(System.currentTimeMillis());
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        sendCapability(player);
        ServerChatRouteService.replayRecentMutations(player);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        ServerMediaService.discardUploads(player.getUUID());
        ServerChatRouteService.onPlayerDisconnect(player.getUUID());
    }

    // --- Payload handlers ---

    private static void handleUploadInit(ServerMediaPayloads.C2SUploadInit payload, ServerPlayContext context) {
        if (!ServerMediaServerConfig.get().enabled) {
            context.execute(() -> sendUploadError(context.player(), payload.uploadId(), "server_media_disabled"));
            return;
        }
        context.execute(() -> {
            if (!ServerRequestLimiter.allow(
                    context.player().getUUID(),
                    ServerRequestLimiter.Kind.UPLOAD_PACKET,
                    System.currentTimeMillis())) {
                sendUploadError(context.player(), payload.uploadId(), "rate_limited");
                return;
            }
            ServerMediaService.beginUpload(
                    context.player().getUUID(),
                    payload.uploadId(),
                    payload.typeWire(),
                    payload.contentType(),
                    payload.totalLen(),
                    payload.totalChunks()).ifPresent(error ->
                            sendUploadError(context.player(), payload.uploadId(), error));
        });
    }

    private static void handleUploadChunk(ServerMediaPayloads.C2SUploadChunk payload, ServerPlayContext context) {
        if (!ServerMediaServerConfig.get().enabled) {
            context.execute(() -> sendUploadError(context.player(), payload.uploadId(), "server_media_disabled"));
            return;
        }
        context.execute(() -> {
            if (!ServerRequestLimiter.allow(
                    context.player().getUUID(),
                    ServerRequestLimiter.Kind.UPLOAD_PACKET,
                    System.currentTimeMillis())) {
                ServerMediaService.discardUpload(context.player().getUUID(), payload.uploadId());
                sendUploadError(context.player(), payload.uploadId(), "rate_limited");
                return;
            }
            Optional<ServerMediaService.UploadCompleted> completedOpt = ServerMediaService.acceptUploadChunk(
                    context.player().getUUID(), payload.uploadId(), payload.idx(), payload.chunk());
            if (completedOpt.isEmpty()) {
                return;
            }
            ServerMediaService.UploadCompleted completed = completedOpt.get();
            if (completed.mediaId() == null) {
                sendUploadError(context.player(), payload.uploadId(),
                        completed.error() == null ? "upload_failed" : completed.error());
                return;
            }
            String mediaId = completed.mediaId();
            ServerMediaService.get(mediaId).ifPresent(stored -> sendUploadAck(
                    context.player(),
                    payload.uploadId(),
                    stored.mediaId(),
                    stored.typeWire()));
        });
    }

    private static void handleRequestMedia(ServerMediaPayloads.C2SRequestMedia payload, ServerPlayContext context) {
        if (!ServerMediaServerConfig.get().enabled) {
            context.execute(() -> sendMediaError(context.player(), payload.mediaId(), "server_media_disabled"));
            return;
        }
        context.execute(() -> {
            if (!ServerRequestLimiter.allow(
                    context.player().getUUID(),
                    ServerRequestLimiter.Kind.MEDIA_READ,
                    System.currentTimeMillis())) {
                sendMediaError(context.player(), payload.mediaId(), "rate_limited");
                return;
            }
            ServerMediaService.MediaReadResult media = ServerMediaService.readForPlayer(
                    context.player().getUUID(), payload.mediaId());
            if (!media.found()) {
                sendMediaError(context.player(), payload.mediaId(), switch (media.failure()) {
                    case EXPIRED -> "expired";
                    case ACCESS_DENIED -> "access_denied";
                    case NOT_FOUND, NONE -> "not_found";
                });
                return;
            }
            sendMedia(context.player(), media.media());
        });
    }

    private static void handleAttachMetadataPacket(
            ServerMediaPayloads.C2SAttachMetadata payload, ServerPlayContext context) {
        if (!ServerMediaServerConfig.get().enabled) {
            context.execute(() -> sendAttachmentError(
                    context.player(), payload.requestId(), payload.attachmentId(), payload.mediaId(),
                    "server_media_disabled"));
            return;
        }
        context.execute(() -> handleAttachMetadata(context.player(), payload));
    }

    private static void handleRequestAttachmentMetaPacket(
            ServerMediaPayloads.C2SRequestAttachmentMeta payload, ServerPlayContext context) {
        if (!ServerMediaServerConfig.get().enabled) {
            context.execute(() -> sendAttachmentError(
                    context.player(), payload.requestId(), payload.attachmentId(), payload.mediaId(),
                    "server_media_disabled"));
            return;
        }
        context.execute(() -> handleRequestAttachmentMeta(context.player(), payload));
    }

    private static void handleChatInputMode(
            ServerMediaPayloads.C2SChatInputMode payload, ServerPlayContext context) {
        // Kept as a wire-compatible no-op for older clients. Rendering mode is client-local.
    }

    private static void handleStructuredChatMessagePacket(
            ServerMediaPayloads.C2SStructuredChatMessage payload, ServerPlayContext context) {
        payload.toMessage().ifPresent(message ->
                context.execute(() -> ServerChatRouteService.routeStructured(context.player(), message)));
    }

    private static void handleStructuredChatV2Packet(
            ServerMediaPayloads.C2SStructuredChatV2 payload, ServerPlayContext context) {
        payload.toSubmission().ifPresent(submission ->
                context.execute(() -> ServerChatRouteService.routeStructuredV2(context.player(), submission)));
    }

    private static void handleRetractChatMessagePacket(
            ServerMediaPayloads.C2SRetractChatMessage payload, ServerPlayContext context) {
        context.execute(() -> {
            if (!ServerRequestLimiter.allow(
                    context.player().getUUID(),
                    ServerRequestLimiter.Kind.CHAT,
                    System.currentTimeMillis())
                    || !ServerChatRouteService.retract(context.player(), payload.messageId())) {
                context.player().sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("chatupgrade.retract.denied")
                                .withStyle(net.minecraft.ChatFormatting.RED),
                        false);
            }
        });
    }

    private static void handleRequestChatHistoryPacket(
            ServerMediaPayloads.C2SRequestChatHistory payload,
            ServerPlayContext context) {
        context.execute(() -> {
            if (!ServerRequestLimiter.allow(
                    context.player().getUUID(),
                    ServerRequestLimiter.Kind.HISTORY_READ,
                    System.currentTimeMillis())) {
                Net.sendToClient(context.player(), new ServerMediaPayloads.S2CChatHistoryComplete(0));
                return;
            }
            if (!ServerMediaServerConfig.get().chatHistoryEnabled) {
                Net.sendToClient(context.player(), new ServerMediaPayloads.S2CChatHistoryComplete(0));
                return;
            }
            List<com.chat.upgrade.net.StructuredChatEnvelope> messages = ServerChatRouteService.historyAfter(
                    context.player(),
                    payload.afterTimestampMs(),
                    payload.limit());
            int sent = 0;
            for (com.chat.upgrade.net.StructuredChatEnvelope message : messages) {
                if (!Net.canSendToClient(context.player(), ServerMediaPayloads.S2CChatHistoryEntry.TYPE)) {
                    break;
                }
                Net.sendToClient(context.player(), ServerMediaPayloads.S2CChatHistoryEntry.fromEnvelope(message));
                sent++;
            }
            Net.sendToClient(context.player(), new ServerMediaPayloads.S2CChatHistoryComplete(sent));
        });
    }

    private static void sendCapability(ServerPlayer player) {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        byte storage = (byte) (cfg.storageMode == ServerMediaServerConfig.StorageMode.DISK ? 1 : 0);
        Net.sendToClient(player, new ServerMediaPayloads.S2CCapability(
                cfg.enabled, cfg.maxSingleBytes, cfg.maxChunkBytes, storage, cfg.ttlSeconds));
        Net.sendToClient(player, new ServerMediaPayloads.S2CAttachmentCapability(
                cfg.enabled,
                StructuredAttachment.CURRENT_SCHEMA_VERSION,
                cfg.ttlSeconds));
    }

    private static void sendUploadAck(ServerPlayer player, long uploadId, String mediaId, String typeWire) {
        String safeType = typeWire == null ? "image" : typeWire;
        Net.sendToClient(player, new ServerMediaPayloads.S2CUploadAck(
                uploadId,
                mediaId,
                safeType,
                ServerMediaUrl.format(mediaId, safeType)));
    }

    private static void sendUploadError(ServerPlayer player, long uploadId, String message) {
        Net.sendToClient(player, new ServerMediaPayloads.S2CUploadAck(
                uploadId,
                "",
                "",
                ""));
        ChatUpgrade.LOGGER.warn("chat-upgrade: server upload error uploadId={} msg={}", uploadId, message);
    }

    private static void handleAttachMetadata(ServerPlayer player, ServerMediaPayloads.C2SAttachMetadata payload) {
        if (!ServerRequestLimiter.allow(
                player.getUUID(),
                ServerRequestLimiter.Kind.ATTACHMENT_WRITE,
                System.currentTimeMillis())) {
            sendAttachmentError(
                    player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "rate_limited");
            return;
        }
        try {
            StructuredAttachment descriptor = new StructuredAttachment(
                    payload.schemaVersion(),
                    normalizeOptional(payload.attachmentId()),
                    normalizeOptional(payload.mediaId()),
                    payload.typeWire(),
                    payload.displayName(),
                    normalizeOptional(payload.fallbackUrl()));
            if (descriptor.hasMedia()
                    && !ServerMediaService.isOwner(player.getUUID(), descriptor.mediaId())) {
                sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "media_not_owned");
                return;
            }
            if (descriptor.fallbackUrl() != null
                    && !ServerMediaUrl.isServerMediaUrl(descriptor.fallbackUrl())
                    && !ServerMediaServerConfig.get().allowExternalAttachmentUrls) {
                sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "external_url_disabled");
                return;
            }
            Optional<StoredAttachment> stored = ServerAttachmentService.put(player.getUUID(), descriptor);
            if (stored.isEmpty()) {
                sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "attachment_id_conflict");
                return;
            }
            sendAttachmentAck(player, payload.requestId(), stored.get());
        } catch (IllegalArgumentException ex) {
            sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "invalid_metadata");
        }
    }

    private static void handleRequestAttachmentMeta(
            ServerPlayer player,
            ServerMediaPayloads.C2SRequestAttachmentMeta payload) {
        if (!ServerRequestLimiter.allow(
                player.getUUID(),
                ServerRequestLimiter.Kind.MEDIA_READ,
                System.currentTimeMillis())) {
            sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "rate_limited");
            return;
        }
        Optional<StoredAttachment> attachmentOpt = ServerAttachmentService.getForPlayer(
                player.getUUID(), payload.attachmentId());
        if (attachmentOpt.isEmpty()) {
            attachmentOpt = ServerAttachmentService.findByMediaIdForPlayer(
                    player.getUUID(), payload.mediaId());
        }
        if (attachmentOpt.isEmpty()) {
            sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "not_found");
            return;
        }
        sendAttachmentMeta(player, payload.requestId(), attachmentOpt.get());
    }

    private static void sendAttachmentAck(ServerPlayer player, long requestId, StoredAttachment attachment) {
        Net.sendToClient(player, new ServerMediaPayloads.S2CAttachmentAck(
                requestId,
                attachment.schemaVersion(),
                attachment.attachmentId(),
                attachment.mediaId(),
                attachment.typeWire(),
                attachment.displayName(),
                attachment.fallbackUrl()));
    }

    private static void sendAttachmentMeta(ServerPlayer player, long requestId, StoredAttachment attachment) {
        Net.sendToClient(player, new ServerMediaPayloads.S2CAttachmentMeta(
                requestId,
                attachment.schemaVersion(),
                attachment.attachmentId(),
                attachment.mediaId(),
                attachment.typeWire(),
                attachment.displayName(),
                attachment.fallbackUrl()));
    }

    private static void sendAttachmentError(
            ServerPlayer player,
            long requestId,
            String attachmentId,
            String mediaId,
            String message) {
        Net.sendToClient(player, new ServerMediaPayloads.S2CAttachmentError(
                requestId,
                attachmentId,
                mediaId,
                message == null ? "error" : message));
        ChatUpgrade.LOGGER.warn("chat-upgrade: server attachment error attachmentId={} mediaId={} msg={}",
                attachmentId, mediaId, message);
    }

    private static void sendMedia(ServerPlayer player, StoredMedia media) {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        int maxChunk = Math.max(1024, cfg.maxChunkBytes);
        byte[] body = media.body();
        int totalLen = body.length;
        int totalChunks = (int) Math.ceil(totalLen / (double) maxChunk);

        Net.sendToClient(player, new ServerMediaPayloads.S2CMediaInit(
                media.mediaId(),
                media.typeWire(),
                media.contentType(),
                media.fingerprint() == null ? "" : media.fingerprint(),
                totalLen,
                totalChunks));

        for (int i = 0; i < totalChunks; i++) {
            int from = i * maxChunk;
            int to = Math.min(totalLen, from + maxChunk);
            byte[] chunk = new byte[to - from];
            System.arraycopy(body, from, chunk, 0, chunk.length);
            Net.sendToClient(player, new ServerMediaPayloads.S2CMediaChunk(media.mediaId(), i, chunk));
        }
    }

    private static void sendMediaError(ServerPlayer player, String mediaId, String message) {
        Net.sendToClient(player, new ServerMediaPayloads.S2CMediaError(
                mediaId == null ? "" : mediaId,
                message == null ? "error" : message));
        ChatUpgrade.LOGGER.warn("chat-upgrade: server media error mediaId={} msg={}", mediaId, message);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
