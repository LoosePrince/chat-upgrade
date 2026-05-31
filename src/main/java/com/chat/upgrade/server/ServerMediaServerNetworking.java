package com.chat.upgrade.server;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.server.store.StoredAttachment;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ServerMediaServerNetworking {
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 10;
    private static final Set<UUID> COMPAT_TEXT_VANILLA_PLAYERS = ConcurrentHashMap.newKeySet();
    private static int ticks = 0;

    private ServerMediaServerNetworking() {
    }

    public static void init() {
        ServerMediaServerConfig.load();
        ServerMediaService.initFromConfig();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % CLEANUP_INTERVAL_TICKS == 0) {
                ServerMediaService.cleanup();
                ServerAttachmentService.cleanup();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            COMPAT_TEXT_VANILLA_PLAYERS.remove(player.getUUID());
            sendCapability(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                COMPAT_TEXT_VANILLA_PLAYERS.remove(handler.player.getUUID()));

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SUploadInit.TYPE, (payload, context) -> {
            if (!ServerMediaServerConfig.get().enabled) {
                context.server().execute(() -> sendUploadError(context.player(), payload.uploadId(), "server_media_disabled"));
                return;
            }
            context.server().execute(() -> ServerMediaService.beginUpload(
                    payload.uploadId(),
                    payload.typeWire(),
                    payload.contentType(),
                    payload.totalLen(),
                    payload.totalChunks()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SUploadChunk.TYPE, (payload, context) -> {
            if (!ServerMediaServerConfig.get().enabled) {
                context.server().execute(() -> sendUploadError(context.player(), payload.uploadId(), "server_media_disabled"));
                return;
            }
            int maxSingle = ServerMediaServerConfig.get().maxSingleBytes;
            context.server().execute(() -> {
                Optional<ServerMediaService.UploadCompleted> completedOpt = ServerMediaService.acceptUploadChunk(
                        payload.uploadId(), payload.idx(), payload.chunk(), maxSingle);
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
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SRequestMedia.TYPE, (payload, context) -> {
            if (!ServerMediaServerConfig.get().enabled) {
                context.server().execute(() -> sendMediaError(context.player(), payload.mediaId(), "server_media_disabled"));
                return;
            }
            context.server().execute(() -> {
                Optional<com.chat.upgrade.server.store.StoredMedia> mediaOpt = ServerMediaService.get(payload.mediaId());
                if (mediaOpt.isEmpty()) {
                    sendMediaError(context.player(), payload.mediaId(), "not_found");
                    return;
                }
                sendMedia(context.player(), mediaOpt.get());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SAttachMetadata.TYPE, (payload, context) -> {
            if (!ServerMediaServerConfig.get().enabled) {
                context.server().execute(() -> sendAttachmentError(
                        context.player(),
                        payload.requestId(),
                        payload.attachmentId(),
                        payload.mediaId(),
                        "server_media_disabled"));
                return;
            }
            context.server().execute(() -> handleAttachMetadata(context.player(), payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SRequestAttachmentMeta.TYPE, (payload, context) -> {
            if (!ServerMediaServerConfig.get().enabled) {
                context.server().execute(() -> sendAttachmentError(
                        context.player(),
                        payload.requestId(),
                        payload.attachmentId(),
                        payload.mediaId(),
                        "server_media_disabled"));
                return;
            }
            context.server().execute(() -> handleRequestAttachmentMeta(context.player(), payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SChatInputMode.TYPE, (payload, context) -> {
            context.server().execute(() -> updateChatInputMode(context.player(), payload.mode()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SStructuredChatMessage.TYPE, (payload, context) -> {
            context.server().execute(() -> ServerChatRouteService.routeStructured(context.player(), payload.toMessage()));
        });
    }

    public static boolean isCompatTextVanillaPlayer(ServerPlayer player) {
        return player != null && COMPAT_TEXT_VANILLA_PLAYERS.contains(player.getUUID());
    }

    private static void updateChatInputMode(ServerPlayer player, String mode) {
        if (player == null) {
            return;
        }
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if ("COMPAT_TEXT_VANILLA".equals(normalized)) {
            COMPAT_TEXT_VANILLA_PLAYERS.add(player.getUUID());
        } else {
            COMPAT_TEXT_VANILLA_PLAYERS.remove(player.getUUID());
        }
    }

    private static void sendCapability(ServerPlayer player) {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        byte storage = (byte) (cfg.storageMode == ServerMediaServerConfig.StorageMode.DISK ? 1 : 0);
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CCapability(
                cfg.enabled, cfg.maxSingleBytes, cfg.maxChunkBytes, storage, cfg.ttlSeconds));
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CAttachmentCapability(
                cfg.enabled,
                StructuredAttachment.CURRENT_SCHEMA_VERSION,
                cfg.ttlSeconds));
    }

    private static void sendUploadAck(ServerPlayer player, long uploadId, String mediaId, String typeWire) {
        String safeType = typeWire == null ? "image" : typeWire;
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CUploadAck(
                uploadId,
                mediaId,
                safeType,
                ServerMediaUrl.format(mediaId, safeType)));
    }

    private static void sendUploadError(ServerPlayer player, long uploadId, String message) {
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CUploadAck(
                uploadId,
                "",
                "",
                ""));
        ChatUpgrade.LOGGER.warn("chat-upgrade: server upload error uploadId={} msg={}", uploadId, message);
    }

    private static void handleAttachMetadata(ServerPlayer player, ServerMediaPayloads.C2SAttachMetadata payload) {
        try {
            StructuredAttachment descriptor = new StructuredAttachment(
                    payload.schemaVersion(),
                    normalizeOptional(payload.attachmentId()),
                    normalizeOptional(payload.mediaId()),
                    payload.typeWire(),
                    payload.displayName(),
                    normalizeOptional(payload.fallbackUrl()));
            if (descriptor.hasMedia() && ServerMediaService.get(descriptor.mediaId()).isEmpty()) {
                sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "media_not_found");
                return;
            }
            sendAttachmentAck(player, payload.requestId(), ServerAttachmentService.put(descriptor));
        } catch (IllegalArgumentException ex) {
            sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "invalid_metadata");
        }
    }

    private static void handleRequestAttachmentMeta(
            ServerPlayer player,
            ServerMediaPayloads.C2SRequestAttachmentMeta payload) {
        Optional<StoredAttachment> attachmentOpt = ServerAttachmentService.get(payload.attachmentId());
        if (attachmentOpt.isEmpty()) {
            attachmentOpt = ServerAttachmentService.findByMediaId(payload.mediaId());
        }
        if (attachmentOpt.isEmpty()) {
            sendAttachmentError(player, payload.requestId(), payload.attachmentId(), payload.mediaId(), "not_found");
            return;
        }
        sendAttachmentMeta(player, payload.requestId(), attachmentOpt.get());
    }

    private static void sendAttachmentAck(ServerPlayer player, long requestId, StoredAttachment attachment) {
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CAttachmentAck(
                requestId,
                attachment.schemaVersion(),
                attachment.attachmentId(),
                attachment.mediaId(),
                attachment.typeWire(),
                attachment.displayName(),
                attachment.fallbackUrl()));
    }

    private static void sendAttachmentMeta(ServerPlayer player, long requestId, StoredAttachment attachment) {
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CAttachmentMeta(
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
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CAttachmentError(
                requestId,
                attachmentId,
                mediaId,
                message == null ? "error" : message));
        ChatUpgrade.LOGGER.warn("chat-upgrade: server attachment error attachmentId={} mediaId={} msg={}",
                attachmentId, mediaId, message);
    }

    private static void sendMedia(ServerPlayer player, com.chat.upgrade.server.store.StoredMedia media) {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        int maxChunk = Math.max(1024, cfg.maxChunkBytes);
        byte[] body = media.body();
        int totalLen = body.length;
        int totalChunks = (int) Math.ceil(totalLen / (double) maxChunk);

        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CMediaInit(
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
            ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CMediaChunk(media.mediaId(), i, chunk));
        }
    }

    private static void sendMediaError(ServerPlayer player, String mediaId, String message) {
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CMediaError(
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

