package com.chat.upgrade.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.ChatUpgrade;

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
    }

    public static VideoEntry getOrLoad(String url) {
        return CACHE.computeIfAbsent(url, u -> {
            VideoEntry e = new VideoEntry();
            startLoad(u, e);
            return e;
        });
    }

    public static VideoEntry getIfPresent(String url) {
        return CACHE.get(url);
    }

    private static void startLoad(String url, VideoEntry entry) {
        CompletableFuture.supplyAsync(() -> {
            return MediaFetchSupport.sendGet(url, 20, "video");
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            try {
                MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, maxReceive);
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
                markFailed(url, entry, VideoEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode video {}: {}", url, e.getMessage());
                markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
            }
        }).exceptionally(e -> {
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
            return;
        }
        mc.execute(() -> UpgradePhantomHudLayout.notifyVideoEntryChanged(url));
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
