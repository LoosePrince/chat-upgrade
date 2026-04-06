package com.chat.upgrade.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.client.Minecraft;

public final class AudioLoader {
    private static final ConcurrentHashMap<String, AudioEntry> CACHE = new ConcurrentHashMap<>();

    private AudioLoader() {
    }

    public static void invalidateAudioCache() {
        CACHE.clear();
        AudioPlayerService.clearAll();
    }

    public static AudioEntry getOrLoad(String url) {
        return CACHE.computeIfAbsent(url, u -> {
            AudioEntry e = new AudioEntry();
            startLoad(u, e);
            return e;
        });
    }

    public static AudioEntry getIfPresent(String url) {
        return CACHE.get(url);
    }

    private static void startLoad(String url, AudioEntry entry) {
        CompletableFuture.supplyAsync(() -> {
            return MediaFetchSupport.sendGet(url, 20, "audio");
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            try {
                MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, maxReceive);
                byte[] body = payload.body();
                entry.setTransferMetadata(body.length, payload.contentType(), payload.md5Hex());
                entry.setLoadPhase(AudioEntry.LoadPhase.DECODE);
                long durationMs;
                try {
                    durationMs = AudioPlayerService.prepare(url, body);
                } catch (Exception ex) {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: unsupported audio {}: {}", url, ex.getMessage());
                    markFailed(url, entry, AudioEntry.FailureKind.UNSUPPORTED_AUDIO_FORMAT);
                    return;
                }
                entry.setLoaded(durationMs);
                notifyChanged(url);
            } catch (MediaFetchSupport.ResponseBodyTooLarge e) {
                markFailed(url, entry, AudioEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode audio {}: {}", url, e.getMessage());
                markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
            }
        }).exceptionally(e -> {
            markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
            return null;
        });
    }

    private static void markFailed(String url, AudioEntry entry, AudioEntry.FailureKind kind) {
        entry.setFailed(kind);
        notifyChanged(url);
    }

    private static void notifyChanged(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> UpgradePhantomHudLayout.notifyAudioEntryChanged(url));
    }
}
