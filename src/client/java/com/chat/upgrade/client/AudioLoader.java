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

public final class AudioLoader {
    private static final ConcurrentHashMap<String, AudioEntry> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AudioLoader() {}

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
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                return HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to fetch audio {}: {}", url, e.getMessage());
                return null;
            }
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailed(url, entry, AudioEntry.FailureKind.UNKNOWN);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            try (InputStream raw = response.body()) {
                OptionalLong clOpt = response.headers().firstValueAsLong("Content-Length");
                if (clOpt.isPresent() && clOpt.getAsLong() > maxReceive) {
                    markFailed(url, entry, AudioEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
                    return;
                }
                byte[] body = readBodyCapped(raw, maxReceive);
                String contentType = response.headers().firstValue("Content-Type").orElse(null);
                String md5Hex = md5Hex(body);
                entry.setTransferMetadata(body.length, contentType, md5Hex);
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
            } catch (ResponseBodyTooLarge e) {
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

    private static final class ResponseBodyTooLarge extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
