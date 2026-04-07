package com.chat.upgrade.client;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ServerMediaNetworking {
    private static final ConcurrentHashMap<String, IncomingMediaAssembly> INCOMING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CompletableFuture<Optional<String>>> UPLOADS = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();
    private static volatile boolean capabilityAnnounced = false;

    private ServerMediaNetworking() {
    }

    public static void initClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            INCOMING.clear();
            ServerMediaClient.clearRuntimeState();
            capabilityAnnounced = false;
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.S2CCapability.TYPE, (payload, context) -> {
            ServerMediaCapability.StorageMode mode = payload.storageMode() == 1
                    ? ServerMediaCapability.StorageMode.DISK
                    : ServerMediaCapability.StorageMode.MEMORY;
            context.client().execute(() -> {
                ServerMediaClient.setCapability(
                        new ServerMediaCapability(payload.enabled(), payload.maxSingleBytes(), payload.maxChunkBytes(), mode,
                                payload.ttlSeconds()));
                boolean uploadReady = payload.enabled() && payload.maxSingleBytes() > 0 && payload.maxChunkBytes() > 0;
                if (uploadReady && !capabilityAnnounced) {
                    capabilityAnnounced = true;
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                                Component.translatable("chatupgrade.server_media.upload_ready").withStyle(ChatFormatting.GREEN));
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.S2CMediaInit.TYPE, (payload, context) -> {
            InlineResourceType type = InlineResourceType.fromWire(payload.typeWire());
            INCOMING.put(payload.mediaId(), new IncomingMediaAssembly(
                    payload.mediaId(),
                    type,
                    payload.contentType(),
                    payload.md5Hex(),
                    payload.totalLen(),
                    payload.totalChunks()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.S2CMediaChunk.TYPE, (payload, context) -> {
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
            context.client().execute(() -> ServerMediaClient.acceptMediaBytes(
                    asm.mediaId(),
                    asm.type(),
                    asm.contentType(),
                    asm.md5Hex(),
                    body));
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.S2CMediaError.TYPE, (payload, context) -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: server media error mediaId={} msg={}", payload.mediaId(),
                    payload.message());
            INCOMING.remove(payload.mediaId());
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerMediaPayloads.S2CUploadAck.TYPE, (payload, context) -> {
            CompletableFuture<Optional<String>> fut = UPLOADS.remove(payload.uploadId());
            if (fut != null) {
                fut.complete(Optional.ofNullable(payload.specialUrl()).filter(s -> !s.isBlank()));
            }
        });
    }

    public static void sendRequest(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(new ServerMediaPayloads.C2SRequestMedia(mediaId));
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

        ClientPlayNetworking.send(new ServerMediaPayloads.C2SUploadInit(
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
            ClientPlayNetworking.send(new ServerMediaPayloads.C2SUploadChunk(uploadId, i, chunk));
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

