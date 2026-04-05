package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImageLoader {
    // Preview region: PHANTOM_COUNT lines × 9px/line
    public static final int PHANTOM_COUNT = 6;
    public static final int PREVIEW_HEIGHT = PHANTOM_COUNT * 9;
    public static final int MAX_PREVIEW_WIDTH = 320;

    /**
     * Extra multiplier on top of {@linkplain #previewTexelsPerGuiPixel(Window) logical→framebuffer density}:
     * texture is still blitted into the same MC-unit rectangle, but holds more texels for sharper minification.
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

    private ImageLoader() {}

    /**
     * How many framebuffer pixels correspond to one horizontal GUI unit (and similarly vertical).
     * Layout stays in MC units; texture resolution tracks this so ~one texel can cover one physical pixel under the quad.
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
     * Releases every registered chat-image texture and clears the URL cache. Call when GUI scale or window size
     * changes so subsequent loads match the new logical→screen mapping.
     */
    public static void invalidateTextureCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
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
            try (InputStream is = response.body()) {
                entry.setLoadPhase(ImageEntry.LoadPhase.DECODE);
                NativeImage img = RasterImageDecoder.decode(is);
                scheduleTextureRegistration(url, entry, img);
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

    private static void markFailed(String url, ImageEntry entry) {
        entry.setFailed();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> UpgradePhantomHudLayout.notifyUrlEntryChanged(url));
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

                // If image is wider than tall relative to our constraints, scale by width instead
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
                            (double) MAX_TEXTURE_DIMENSION / texH
                    );
                    texW = Math.max(1, (int) (texW * shrink));
                    texH = Math.max(1, (int) (texH * shrink));
                }

                // One resize from full source → supersampled texture; blit still uses displayW×displayH on screen.
                NativeImage scaled = new NativeImage(img.format(), texW, texH, false);
                img.resizeSubRectTo(0, 0, rawW, rawH, scaled);
                img.close();

                int id = TEXTURE_COUNTER.getAndIncrement();
                Identifier location = Identifier.fromNamespaceAndPath(
                        ChatUpgrade.MOD_ID, "upgrade_preview_" + id);

                int finalId = id;
                NativeImage texturePixels = scaled;
                DynamicTexture texture = new DynamicTexture(() -> "upgrade_preview_" + finalId, texturePixels);
                Minecraft.getInstance().getTextureManager().register(location, texture);

                entry.setLoaded(location, displayW, displayH, texW, texH);
                UpgradePhantomHudLayout.notifyUrlEntryChanged(url);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to register texture for {}: {}", url, e.getMessage());
                markFailed(url, entry);
            }
        });
    }
}
