package com.chat.upgrade.client.media.video;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.MediaFetchSupport;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.plugin.FfmpegNativeBootstrap;
import com.chat.upgrade.client.ui.chat.UpgradePhantomHudLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;

import net.minecraft.client.Minecraft;

public final class VideoLoader {
    private static final int VIDEO_PREVIEW_HEIGHT = 63;
    private static final int MAX_PREVIEW_WIDTH = 320;
    private static final ConcurrentHashMap<String, VideoEntry> CACHE = new ConcurrentHashMap<>();

    private VideoLoader() {
    }

    public static void invalidateVideoCache() {
        for (String url : CACHE.keySet()) {
            VideoPlayerService.remove(url);
        }
        CACHE.clear();
        RichChatViewport.invalidateAll();
    }

    public static VideoEntry getOrLoad(String url) {
        return CACHE.computeIfAbsent(url, u -> {
            VideoEntry e = new VideoEntry();
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
        VideoEntry entry = CACHE.computeIfAbsent(url, u -> new VideoEntry());
        int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
        if (body.length > maxReceive) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: video bytes exceed limit ({}) for {}", maxReceive, url);
            markFailed(url, entry, VideoEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
            return;
        }
        entry.setTransferMetadata(body.length, contentType == null ? "unknown" : contentType, md5Hex);
        entry.setLoadPhase(VideoEntry.LoadPhase.DECODE);
        CompletableFuture.runAsync(() -> {
            if (!FfmpegNativeBootstrap.ensureReady()) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: video runtime not ready for {}, FFmpeg natives unavailable", url);
                markFailed(url, entry, VideoEntry.FailureKind.UNSUPPORTED_VIDEO_FORMAT);
                return;
            }
            VideoPlayerService.Prepared meta;
            try {
                meta = VideoPlayerService.prepare(url, body);
            } catch (Exception ex) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: unsupported video {}: {}", url, ex.getMessage());
                markFailed(url, entry, VideoEntry.FailureKind.UNSUPPORTED_VIDEO_FORMAT);
                return;
            }
            PreviewLayout layout = computePreviewLayout(meta.rawWidth(), meta.rawHeight());
            entry.setLoaded(meta.durationMs(), meta.rawWidth(), meta.rawHeight(), layout.displayW(), layout.displayH());
            notifyChanged(url);
        });
    }

    public static VideoEntry getIfPresent(String url) {
        return CACHE.get(url);
    }

    public static void forceReload(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (ServerMediaClient.isServerMediaUrl(url)) {
            ServerMediaClient.forgetRequestForUrl(url);
        }
        VideoPlayerService.remove(url);
        CACHE.remove(url);
        notifyChanged(url);
        getOrLoad(url);
    }

    private static void startLoad(String url, VideoEntry entry) {
        if (ServerMediaClient.isServerMediaUrl(url)) {
            if (!ServerMediaClient.capability().enabled()) {
                markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
                return;
            }
            ServerMediaClient.requestIfNeeded(url);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            return MediaFetchSupport.sendGet(url, 20, "video");
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response == null ? -1 : response.statusCode();
                ChatUpgrade.LOGGER.warn("chat-upgrade: video fetch failed url={} status={}", url, status);
                markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            ChatUpgrade.LOGGER.info(
                    "chat-upgrade: video fetch start url={} status={} contentType={} contentLength={} maxReceive={}",
                    url,
                    response.statusCode(),
                    contentType,
                    declaredLength,
                    maxReceive);
            try {
                MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, maxReceive);
                if (!FfmpegNativeBootstrap.ensureReady()) {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: video runtime not ready for {}, FFmpeg natives unavailable",
                            url);
                    markFailed(url, entry, VideoEntry.FailureKind.UNSUPPORTED_VIDEO_FORMAT);
                    return;
                }
                byte[] body = payload.body();
                entry.setTransferMetadata(body.length, payload.contentType(), payload.md5Hex());
                entry.setLoadPhase(VideoEntry.LoadPhase.DECODE);
                VideoPlayerService.Prepared meta;
                try {
                    meta = VideoPlayerService.prepare(url, body);
                } catch (Exception ex) {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: unsupported video {}: {}", url, ex.getMessage());
                    markFailed(url, entry, VideoEntry.FailureKind.UNSUPPORTED_VIDEO_FORMAT);
                    return;
                }
                PreviewLayout layout = computePreviewLayout(meta.rawWidth(), meta.rawHeight());
                entry.setLoaded(meta.durationMs(), meta.rawWidth(), meta.rawHeight(), layout.displayW(),
                        layout.displayH());
                notifyChanged(url);
            } catch (MediaFetchSupport.ResponseBodyTooLarge e) {
                ChatUpgrade.LOGGER.warn(
                        "chat-upgrade: video body too large url={} contentLength={} maxReceive={}",
                        url,
                        declaredLength,
                        maxReceive);
                markFailed(url, entry, VideoEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode video {}: {}", url, e.getMessage());
                markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
            }
        }).exceptionally(e -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: video load pipeline failed {}: {}", url, e.toString());
            markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
            return null;
        });
    }

    private static void markFailed(String url, VideoEntry entry, VideoEntry.FailureKind kind) {
        entry.setFailed(kind);
        VideoPlayerService.remove(url);
        notifyChanged(url);
    }

    private static void notifyChanged(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            RichChatViewport.invalidateMedia(url);
            return;
        }
        mc.execute(() -> {
            UpgradePhantomHudLayout.notifyVideoEntryChanged(url);
            RichChatViewport.invalidateMedia(url);
        });
    }

    private static PreviewLayout computePreviewLayout(int rawW, int rawH) {
        if (rawW <= 0 || rawH <= 0) {
            return new PreviewLayout(VIDEO_PREVIEW_HEIGHT, VIDEO_PREVIEW_HEIGHT);
        }
        double scale = (double) VIDEO_PREVIEW_HEIGHT / rawH;
        int displayW = (int) Math.min(rawW * scale, MAX_PREVIEW_WIDTH);
        int displayH = VIDEO_PREVIEW_HEIGHT;

        if ((double) rawW / rawH > (double) MAX_PREVIEW_WIDTH / VIDEO_PREVIEW_HEIGHT) {
            scale = (double) MAX_PREVIEW_WIDTH / rawW;
            displayW = MAX_PREVIEW_WIDTH;
            displayH = (int) (rawH * scale);
        }
        return new PreviewLayout(Math.max(1, displayW), Math.max(1, displayH));
    }

    private record PreviewLayout(int displayW, int displayH) {
    }

}
