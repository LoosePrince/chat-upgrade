package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class VideoLoader {
    private static final int VIDEO_PREVIEW_HEIGHT = 63;
    private static final int MAX_PREVIEW_WIDTH = 320;
    private static final ConcurrentHashMap<String, VideoEntry> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private VideoLoader() {}

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
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                return HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to fetch video {}: {}", url, e.getMessage());
                return null;
            }
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailed(url, entry, VideoEntry.FailureKind.UNKNOWN);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            try (InputStream raw = response.body()) {
                OptionalLong clOpt = response.headers().firstValueAsLong("Content-Length");
                if (clOpt.isPresent() && clOpt.getAsLong() > maxReceive) {
                    markFailed(url, entry, VideoEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
                    return;
                }
                byte[] body = readBodyCapped(raw, maxReceive);
                String contentType = response.headers().firstValue("Content-Type").orElse(null);
                String md5Hex = md5Hex(body);
                entry.setTransferMetadata(body.length, contentType, md5Hex);
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
                entry.setLoaded(meta.durationMs(), meta.rawWidth(), meta.rawHeight(), layout.displayW(), layout.displayH());
                notifyChanged(url);
            } catch (ResponseBodyTooLarge e) {
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

    private static byte[] readBodyCapped(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 65536));
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int n = is.read(buf);
            if (n < 0) {
                break;
            }
            if (total + n > maxBytes) {
                throw new ResponseBodyTooLarge();
            }
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }

    private static String md5Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static PreviewLayout computePreviewLayout(int rawW, int rawH) {
        if (rawW <= 0 || rawH <= 0) {
            return new PreviewLayout(VIDEO_PREVIEW_HEIGHT, VIDEO_PREVIEW_HEIGHT);
        }
        double scale = (double) VIDEO_PREVIEW_HEIGHT / rawH;
        int displayW = (int) Math.min(rawW * scale, MAX_PREVIEW_WIDTH);
        int displayH = VIDEO_PREVIEW_HEIGHT;

        if (rawW > 0 && (double) rawW / rawH > (double) MAX_PREVIEW_WIDTH / VIDEO_PREVIEW_HEIGHT) {
            scale = (double) MAX_PREVIEW_WIDTH / rawW;
            displayW = MAX_PREVIEW_WIDTH;
            displayH = (int) (rawH * scale);
        }
        return new PreviewLayout(Math.max(1, displayW), Math.max(1, displayH));
    }

    private record PreviewLayout(int displayW, int displayH) {}

    private static final class ResponseBodyTooLarge extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
