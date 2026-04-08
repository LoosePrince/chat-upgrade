package com.chat.upgrade.client.media.audio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.MediaFetchSupport;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.plugin.FfmpegNativeBootstrap;
import com.chat.upgrade.client.ui.chat.UpgradePhantomHudLayout;

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

    /**
     * Completes a load from an already-available payload (e.g. resolved via server packets).
     * The cache key is still the provided {@code url}.
     */
    public static void loadFromBytes(String url, byte[] body, String contentType, String md5Hex) {
        if (url == null || url.isBlank() || body == null) {
            return;
        }
        AudioEntry entry = CACHE.computeIfAbsent(url, u -> new AudioEntry());
        int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
        if (body.length > maxReceive) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: audio bytes exceed limit ({}) for {}", maxReceive, url);
            markFailed(url, entry, AudioEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
            return;
        }
        entry.setTransferMetadata(body.length, contentType == null ? "unknown" : contentType, md5Hex);
        entry.setLoadPhase(AudioEntry.LoadPhase.DECODE);
        CompletableFuture.runAsync(() -> {
            if (!FfmpegNativeBootstrap.ensureReady()) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: audio runtime not ready for {}, FFmpeg natives unavailable", url);
                markFailed(url, entry, AudioEntry.FailureKind.UNSUPPORTED_AUDIO_FORMAT);
                return;
            }
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
        });
    }

    public static AudioEntry getIfPresent(String url) {
        return CACHE.get(url);
    }

    public static void forceReload(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (ServerMediaClient.isServerMediaUrl(url)) {
            ServerMediaClient.forgetRequestForUrl(url);
        }
        CACHE.remove(url);
        AudioPlayerService.stopAndRemove(url);
        notifyChanged(url);
        getOrLoad(url);
    }

    private static void startLoad(String url, AudioEntry entry) {
        if (ServerMediaClient.isServerMediaUrl(url)) {
            if (!ServerMediaClient.capability().enabled()) {
                markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
                return;
            }
            ServerMediaClient.requestIfNeeded(url);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            return MediaFetchSupport.sendGet(url, 20, "audio");
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response == null ? -1 : response.statusCode();
                ChatUpgrade.LOGGER.warn("chat-upgrade: audio fetch failed url={} status={}", url, status);
                markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            ChatUpgrade.LOGGER.info(
                    "chat-upgrade: audio fetch start url={} status={} contentType={} contentLength={} maxReceive={}",
                    url,
                    response.statusCode(),
                    contentType,
                    declaredLength,
                    maxReceive);
            try {
                MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, maxReceive);
                byte[] body = payload.body();
                entry.setTransferMetadata(body.length, payload.contentType(), payload.md5Hex());
                entry.setLoadPhase(AudioEntry.LoadPhase.DECODE);
                if (!FfmpegNativeBootstrap.ensureReady()) {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: audio runtime not ready for {}, FFmpeg natives unavailable",
                            url);
                    markFailed(url, entry, AudioEntry.FailureKind.UNSUPPORTED_AUDIO_FORMAT);
                    return;
                }
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
                ChatUpgrade.LOGGER.warn(
                        "chat-upgrade: audio body too large url={} contentLength={} maxReceive={}",
                        url,
                        declaredLength,
                        maxReceive);
                markFailed(url, entry, AudioEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode audio {}: {}", url, e.getMessage());
                markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
            }
        }).exceptionally(e -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: audio load pipeline failed {}: {}", url, e.toString());
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
