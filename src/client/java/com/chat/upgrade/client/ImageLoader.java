package com.chat.upgrade.client;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class ImageLoader {
    static {
        ImageIO.scanForPlugins();
    }

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

    private ImageLoader() {
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
                e.forEachRegisteredTexture(textures::release);
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
     * Completes a load from an already-available payload (e.g. resolved via server packets).
     * The cache key is still the provided {@code url}.
     */
    public static void loadFromBytes(String url, byte[] body, String contentType, @Nullable String md5Hex) {
        if (url == null || url.isBlank() || body == null) {
            return;
        }
        ImageEntry entry = CACHE.computeIfAbsent(url, u -> new ImageEntry());
        entry.setTransferMetadata(body.length, contentType == null ? "unknown" : contentType, md5Hex);
        entry.setLoadPhase(ImageEntry.LoadPhase.DECODE);
        CompletableFuture.runAsync(() -> {
            try {
                Optional<AnimatedDecodeResult> animatedOpt = GifAnimatedDecoder.tryDecode(body);
                if (animatedOpt.isEmpty()) {
                    animatedOpt = WebpAnimatedDecoder.tryDecode(body);
                }
                if (animatedOpt.isEmpty()) {
                    animatedOpt = ApngAnimatedDecoder.tryDecode(body);
                }
                if (animatedOpt.isPresent()) {
                    AnimatedDecodeResult r = animatedOpt.get();
                    scheduleAnimatedTextureRegistration(url, entry, r.frames(), r.delayMs());
                    return;
                }
                NativeImage img = RasterImageDecoder.decode(new ByteArrayInputStream(body));
                scheduleTextureRegistration(url, entry, img);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode image {} from bytes: {}", url, e.getMessage());
                markFailed(url, entry);
            }
        });
    }

    /**
     * Returns the cached entry without starting a load, or null.
     */
    public static ImageEntry getIfPresent(String url) {
        return CACHE.get(url);
    }

    public static void forceReload(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (ServerMediaClient.isServerMediaUrl(url)) {
            ServerMediaClient.forgetRequestForUrl(url);
        }
        Minecraft mc = Minecraft.getInstance();
        ImageEntry existing = CACHE.remove(url);
        if (mc != null && existing != null && existing.isLoaded()) {
            var textures = mc.getTextureManager();
            existing.forEachRegisteredTexture(textures::release);
        }
        UpgradePhantomHudLayout.clearLayoutRegistrations();
        getOrLoad(url);
    }

    private static void startLoad(String url, ImageEntry entry) {
        if (ServerMediaClient.isServerMediaUrl(url)) {
            if (!ServerMediaClient.capability().enabled()) {
                markFailed(url, entry);
                return;
            }
            ServerMediaClient.requestIfNeeded(url);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            return MediaFetchSupport.sendGet(url, 15, "image");
        }).thenAccept(response -> {
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailed(url, entry);
                return;
            }
            int maxReceive = ChatUpgradeConfig.get().maxReceiveBytes;
            try {
                MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, maxReceive);
                byte[] body = payload.body();
                String contentType = payload.contentType();
                int declaredLen = payload.declaredLength();
                int byteLen = body.length;
                if (declaredLen >= 0 && declaredLen != byteLen) {
                    ChatUpgrade.LOGGER.debug(
                            "chat-upgrade: Content-Length {} differs from body {} for {}",
                            declaredLen, byteLen, url);
                }
                String md5Hex = payload.md5Hex();
                entry.setTransferMetadata(byteLen, contentType, md5Hex);
                entry.setLoadPhase(ImageEntry.LoadPhase.DECODE);
                Optional<AnimatedDecodeResult> animatedOpt = GifAnimatedDecoder.tryDecode(body);
                if (animatedOpt.isEmpty()) {
                    animatedOpt = WebpAnimatedDecoder.tryDecode(body);
                }
                if (animatedOpt.isEmpty()) {
                    animatedOpt = ApngAnimatedDecoder.tryDecode(body);
                }
                if (animatedOpt.isPresent()) {
                    AnimatedDecodeResult r = animatedOpt.get();
                    ChatUpgrade.LOGGER.info("chat-upgrade: animated decode ok for {} (frames={})", url,
                            r.frames().length);
                    scheduleAnimatedTextureRegistration(url, entry, r.frames(), r.delayMs());
                    return;
                }
                NativeImage img = RasterImageDecoder.decode(new ByteArrayInputStream(body));
                scheduleTextureRegistration(url, entry, img);
            } catch (MediaFetchSupport.ResponseBodyTooLarge e) {
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

    private record PreviewLayout(int displayW, int displayH, int texW, int texH) {
    }

    private static @Nullable PreviewLayout computePreviewLayout(Window window, int rawW, int rawH) {
        if (rawH == 0) {
            return null;
        }
        double scale = (double) PREVIEW_HEIGHT / rawH;
        int displayW = (int) Math.min(rawW * scale, MAX_PREVIEW_WIDTH);
        int displayH = PREVIEW_HEIGHT;

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
        return new PreviewLayout(displayW, displayH, texW, texH);
    }

    private static void closeNativeImages(@Nullable NativeImage[] frames) {
        if (frames == null) {
            return;
        }
        for (NativeImage ni : frames) {
            if (ni != null) {
                try {
                    ni.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void scheduleAnimatedTextureRegistration(
            String url,
            ImageEntry entry,
            NativeImage[] frames,
            int[] delayMs) {
        Minecraft.getInstance().execute(() -> {
            ArrayList<Identifier> registered = new ArrayList<>();
            try {
                Minecraft mc = Minecraft.getInstance();
                Window window = mc.getWindow();
                NativeImage first = frames[0];
                int rawW = first.getWidth();
                int rawH = first.getHeight();
                String formatName = first.format().name();

                PreviewLayout layout = computePreviewLayout(window, rawW, rawH);
                if (layout == null) {
                    markFailed(url, entry);
                    closeNativeImages(frames);
                    return;
                }
                int displayW = layout.displayW();
                int displayH = layout.displayH();
                int texW = layout.texW();
                int texH = layout.texH();

                Identifier[] ids = new Identifier[frames.length];
                for (int i = 0; i < frames.length; i++) {
                    NativeImage img = frames[i];
                    int fw = img.getWidth();
                    int fh = img.getHeight();
                    NativeImage scaled = new NativeImage(img.format(), texW, texH, false);
                    img.resizeSubRectTo(0, 0, fw, fh, scaled);
                    img.close();
                    frames[i] = null;

                    int idNum = TEXTURE_COUNTER.getAndIncrement();
                    Identifier location = Identifier.fromNamespaceAndPath(
                            ChatUpgrade.MOD_ID, "upgrade_preview_" + idNum);
                    int finalId = idNum;
                    DynamicTexture texture = new DynamicTexture(() -> "upgrade_preview_" + finalId, scaled);
                    mc.getTextureManager().register(location, texture);
                    registered.add(location);
                    ids[i] = location;
                }

                entry.setDecodedFormatName(formatName);
                entry.setLoadedAnimated(ids, delayMs, displayW, displayH, texW, texH, rawW, rawH);
                UpgradePhantomHudLayout.notifyUrlEntryChanged(url);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to register animated texture for {}: {}", url,
                        e.getMessage());
                Minecraft mc = Minecraft.getInstance();
                for (Identifier id : registered) {
                    mc.getTextureManager().release(id);
                }
                closeNativeImages(frames);
                markFailed(url, entry);
            }
        });
    }

    private static void scheduleTextureRegistration(String url, ImageEntry entry, NativeImage img) {
        Minecraft.getInstance().execute(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                Window window = mc.getWindow();

                int rawW = img.getWidth();
                int rawH = img.getHeight();

                PreviewLayout layout = computePreviewLayout(window, rawW, rawH);
                if (layout == null) {
                    markFailed(url, entry);
                    img.close();
                    return;
                }
                int displayW = layout.displayW();
                int displayH = layout.displayH();
                int texW = layout.texW();
                int texH = layout.texH();

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
