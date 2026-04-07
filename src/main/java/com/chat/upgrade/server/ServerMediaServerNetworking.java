package com.chat.upgrade.server;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.ServerMediaPayloads;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ServerMediaServerNetworking {
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 10;
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
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            sendCapability(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.C2SUploadInit.TYPE, (payload, context) -> {
            if (!ServerMediaServerConfig.get().enabled) {
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
                    sendMediaError(context.player(), "upload:" + payload.uploadId(),
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
    }

    private static void sendCapability(ServerPlayer player) {
        ServerMediaServerConfig cfg = ServerMediaServerConfig.get();
        byte storage = (byte) (cfg.storageMode == ServerMediaServerConfig.StorageMode.DISK ? 1 : 0);
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CCapability(
                cfg.enabled, cfg.maxSingleBytes, cfg.maxChunkBytes, storage, cfg.ttlSeconds));
    }

    private static void sendUploadAck(ServerPlayer player, long uploadId, String mediaId, String typeWire) {
        String safeType = typeWire == null ? "image" : typeWire;
        ServerPlayNetworking.send(player, new ServerMediaPayloads.S2CUploadAck(
                uploadId,
                mediaId,
                safeType,
                ServerMediaUrl.format(mediaId, safeType)));
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
}

