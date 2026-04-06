package com.chat.upgrade.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class ImageLoader {
    // Preview region: PHANTOM_COUNT lines × 9px/line
    public static final int PHANTOM_COUNT = 6;
    public static final int PREVIEW_HEIGHT = PHANTOM_COUNT * 9;
    public static final int MAX_PREVIEW_WIDTH = 320;

    /**
     * Extra multiplier on top of {@linkplain #previewTexelsPerGuiPixel(Window)
     * logical→framebuffer density}:
     * texture is still blitted into the same MC-unit rectangle, but holds more
     * texels for sharper minification.
     */
    public static final int PREVIEW_SUPER_SAMPLING = 2;

    /** Hard cap so huge remote images do not blow VRAM (long edge). */
    private static final int MAX_TEXTURE_DIMENSION = 1024;

    private static final ConcurrentHashMap<String, ImageEntry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger(0);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ImageLoader() {
    }

    private static final class ResponseBodyTooLarge extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * How many framebuffer pixels correspond to one horizontal GUI unit (and
     * similarly vertical).
     * Layout stays in MC units; texture resolution tracks this so ~one texel can
     * cover one physical pixel under the quad.
     */
    public static double previewTexelsPerGuiPixelX(Window window) {
        int sw = window.getGuiScaledWidth();
        return sw <= 0 ? 1.0 : (double) window.getWidth() / sw;
    }

    public static double previewTexelsPerGuiPixelY(Window window) {
        int sh = window.getGuiScaledHeight();
        return sh <= 0 ? 1.0 : (double) window.getHeight() / sh;
    }

    /**
     * Releases every registered chat-image texture and clears the URL cache. Call
     * when GUI scale or window size
     * changes so subsequent loads match the new logical→screen mapping.
     */
    public static void invalidateTextureCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
            return;
        var textures = mc.getTextureManager();
        for (ImageEntry e : new ArrayList<>(CACHE.values())) {
            if (e.isLoaded()) {
                Identifier id = e.getTextureId();
                if (id != null) {
                    textures.release(id);
                }
            }
        }
        CACHE.clear();
        UpgradePhantomHudLayout.clearLayoutRegistrations();
    }

    /**
     * Returns the entry for the given URL, starting a load if not already cached.
     * Failed loads are not kept in {@link #CACHE}; the next call starts a new
     * attempt.
     */
    public static ImageEntry getOrLoad(String url) {
        return CACHE.computeIfAbsent(url, u -> {
            ImageEntry entry = new ImageEntry();
            startLoad(u, entry);
            return entry;
        });
    }

    /**
     * Returns the cached entry without starting a load, or null.
     */
    public static ImageEntry getIfPresent(String url) {
        return CACHE.get(url);
    }

    private static void startLoad(String url, ImageEntry entry) {
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                return HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to fetch {}: {}", url, e.getMessage());
                return null;
            }
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailed(url, entry);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            try (InputStream raw = response.body()) {
                OptionalLong clOpt = response.headers().firstValueAsLong("Content-Length");
                if (clOpt.isPresent() && clOpt.getAsLong() > maxReceive) {
                    ChatUpgrade.LOGGER.warn(
                            "chat-upgrade: image too large (Content-Length {} > limit {}) for {}",
                            clOpt.getAsLong(),
                            maxReceive,
                            url);
                    markFailedOversize(url, entry);
                    return;
                }
                byte[] body = readBodyCapped(raw, maxReceive);
                String contentType = response.headers().firstValue("Content-Type").orElse(null);
                int declaredLen = -1;
                try {
                    var lenOpt = response.headers().firstValueAsLong("Content-Length");
                    if (lenOpt.isPresent()) {
                        declaredLen = (int) Math.min(lenOpt.getAsLong(), Integer.MAX_VALUE);
                    }
                } catch (Exception ignored) {
                }
                int byteLen = body.length;
                if (declaredLen >= 0 && declaredLen != byteLen) {
                    ChatUpgrade.LOGGER.debug(
                            "chat-upgrade: Content-Length {} differs from body {} for {}",
                            declaredLen, byteLen, url);
                }
                String md5Hex = md5Hex(body);
                entry.setTransferMetadata(byteLen, contentType, md5Hex);
                entry.setLoadPhase(ImageEntry.LoadPhase.DECODE);
                NativeImage img = RasterImageDecoder.decode(new ByteArrayInputStream(body));
                scheduleTextureRegistration(url, entry, img);
            } catch (ResponseBodyTooLarge e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: image body exceeds limit ({}) for {}", maxReceive, url);
                markFailedOversize(url, entry);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode image {}: {}", url, e.getMessage());
                markFailed(url, entry);
            }
        }).exceptionally(e -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: unexpected error loading {}: {}", url, e.getMessage());
            markFailed(url, entry);
            return null;
        });
    }

    private static byte[] readBodyCapped(InputStream is, int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 65536));
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int n = is.read(buf);
            if (n < 0) {
                break;
            }
            if ((long) total + n > maxBytes) {
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

    private static void markFailed(String url, ImageEntry entry) {
        markFailed(url, entry, ImageEntry.FailureKind.UNKNOWN);
    }

    private static void markFailedOversize(String url, ImageEntry entry) {
        markFailed(url, entry, ImageEntry.FailureKind.RESPONSE_BODY_TOO_LARGE);
    }

    private static void markFailed(String url, ImageEntry entry, ImageEntry.FailureKind kind) {
        entry.setFailed(kind);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            CACHE.remove(url, entry);
            return;
        }
        mc.execute(() -> {
            UpgradePhantomHudLayout.notifyUrlEntryChanged(url);
            CACHE.remove(url, entry);
        });
    }

    private static void scheduleTextureRegistration(String url, ImageEntry entry, NativeImage img) {
        Minecraft.getInstance().execute(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                Window window = mc.getWindow();

                int rawW = img.getWidth();
                int rawH = img.getHeight();

                // Scale to fit within preview area
                int displayW;
                int displayH;
                if (rawH == 0) {
                    markFailed(url, entry);
                    img.close();
                    return;
                }
                double scale = (double) PREVIEW_HEIGHT / rawH;
                displayW = (int) Math.min(rawW * scale, MAX_PREVIEW_WIDTH);
                displayH = PREVIEW_HEIGHT;

                // If image is wider than tall relative to our constraints, scale by width
                // instead
                if (rawW > 0 && (double) rawW / rawH > (double) MAX_PREVIEW_WIDTH / PREVIEW_HEIGHT) {
                    scale = (double) MAX_PREVIEW_WIDTH / rawW;
                    displayW = MAX_PREVIEW_WIDTH;
                    displayH = (int) (rawH * scale);
                }

                double pxX = previewTexelsPerGuiPixelX(window);
                double pxY = previewTexelsPerGuiPixelY(window);
                int texW = (int) Math.ceil(displayW * pxX * PREVIEW_SUPER_SAMPLING);
                int texH = (int) Math.ceil(displayH * pxY * PREVIEW_SUPER_SAMPLING);
                if (texW > MAX_TEXTURE_DIMENSION || texH > MAX_TEXTURE_DIMENSION) {
                    double shrink = Math.min(
                            (double) MAX_TEXTURE_DIMENSION / texW,
                            (double) MAX_TEXTURE_DIMENSION / texH);
                    texW = Math.max(1, (int) (texW * shrink));
                    texH = Math.max(1, (int) (texH * shrink));
                }

                // One resize from full source → supersampled texture; blit still uses
                // displayW×displayH on screen.
                NativeImage scaled = new NativeImage(img.format(), texW, texH, false);
                img.resizeSubRectTo(0, 0, rawW, rawH, scaled);
                entry.setDecodedFormatName(img.format().name());
                img.close();

                int id = TEXTURE_COUNTER.getAndIncrement();
                Identifier location = Identifier.fromNamespaceAndPath(
                        ChatUpgrade.MOD_ID, "upgrade_preview_" + id);

                int finalId = id;
                NativeImage texturePixels = scaled;
                DynamicTexture texture = new DynamicTexture(() -> "upgrade_preview_" + finalId, texturePixels);
                Minecraft.getInstance().getTextureManager().register(location, texture);

                entry.setLoaded(location, displayW, displayH, texW, texH, rawW, rawH);
                UpgradePhantomHudLayout.notifyUrlEntryChanged(url);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to register texture for {}: {}", url, e.getMessage());
                markFailed(url, entry);
            }
        });
    }
}
