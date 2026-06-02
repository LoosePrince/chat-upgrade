package com.chat.upgrade.client.media.image;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.MediaFetchSupport;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.UpgradePhantomHudLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;
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
    /** Full preview texture cap for popup preview screen (static images). */
    private static final int MAX_FULL_TEXTURE_DIMENSION = 4096;

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
        clearCompatLayoutRegistrations();
        RichChatViewport.invalidateAll();
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
        ChatUpgrade.LOGGER.info(
                "chat-upgrade: image loadFromBytes url={} bytes={} contentType={}",
                url, body.length, contentType == null ? "unknown" : contentType);
        entry.setTransferMetadata(body.length, contentType == null ? "unknown" : contentType, md5Hex);
        entry.setLoadPhase(ImageEntry.LoadPhase.DECODE);
        CompletableFuture.runAsync(() -> {
            try {
                decodeAndSchedule(url, entry, body, true);
            } catch (Throwable t) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode image {} from bytes: {} [{}]",
                        url, t.getMessage(), t.getClass().getName());
                markFailed(url, entry);
            }
        }).exceptionally(t -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: async decode pipeline crashed for {}: {}",
                    url, t.getMessage());
            markFailed(url, entry);
            return null;
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
        clearCompatLayoutRegistrations();
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
                decodeAndSchedule(url, entry, body, false);
            } catch (MediaFetchSupport.ResponseBodyTooLarge e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: image body exceeds limit ({}) for {}", maxReceive, url);
                markFailedOversize(url, entry);
            } catch (Throwable t) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to decode image {}: {} [{}]",
                        url, t.getMessage(), t.getClass().getName());
                markFailed(url, entry);
            }
        }).exceptionally(e -> {
            ChatUpgrade.LOGGER.warn("chat-upgrade: unexpected error loading {}: {}", url, e.getMessage());
            markFailed(url, entry);
            return null;
        });
    }

    private static void decodeAndSchedule(String url, ImageEntry entry, byte[] body, boolean logRasterSuccess)
            throws Exception {
        Optional<AnimatedDecodeResult> animatedOpt = GifAnimatedDecoder.tryDecode(body);
        if (animatedOpt.isEmpty()) {
            animatedOpt = WebpAnimatedDecoder.tryDecode(body);
        }
        if (animatedOpt.isEmpty()) {
            animatedOpt = ApngAnimatedDecoder.tryDecode(body);
        }
        if (animatedOpt.isPresent()) {
            AnimatedDecodeResult r = animatedOpt.get();
            ChatUpgrade.LOGGER.info("chat-upgrade: animated decode ok for {} (frames={})", url, r.frames().length);
            scheduleAnimatedTextureRegistration(url, entry, r.frames(), r.delayMs());
            return;
        }
        NativeImage img = RasterImageDecoder.decode(new ByteArrayInputStream(body));
        if (logRasterSuccess) {
            ChatUpgrade.LOGGER.info("chat-upgrade: raster decode ok for {} ({}x{}, format={})",
                    url, img.getWidth(), img.getHeight(), img.format().name());
        }
        scheduleTextureRegistration(url, entry, img);
    }

    private static void notifyCompatLayoutForUrl(String url) {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            UpgradePhantomHudLayout.notifyUrlEntryChanged(url);
        }
    }

    private static void clearCompatLayoutRegistrations() {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            UpgradePhantomHudLayout.clearLayoutRegistrations();
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
            RichChatViewport.invalidateMedia(url);
            CACHE.remove(url, entry);
            return;
        }
        mc.execute(() -> {
            notifyCompatLayoutForUrl(url);
            RichChatViewport.invalidateMedia(url);
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

    private static int[] computeFullTextureSize(int rawW, int rawH) {
        int fullW = Math.max(1, rawW);
        int fullH = Math.max(1, rawH);
        if (fullW <= MAX_FULL_TEXTURE_DIMENSION && fullH <= MAX_FULL_TEXTURE_DIMENSION) {
            return new int[] { fullW, fullH };
        }
        double shrink = Math.min(
                (double) MAX_FULL_TEXTURE_DIMENSION / fullW,
                (double) MAX_FULL_TEXTURE_DIMENSION / fullH);
        fullW = Math.max(1, (int) Math.floor(fullW * shrink));
        fullH = Math.max(1, (int) Math.floor(fullH * shrink));
        return new int[] { fullW, fullH };
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
                notifyCompatLayoutForUrl(url);
                RichChatViewport.invalidateMedia(url);
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
                int[] fullSize = computeFullTextureSize(rawW, rawH);
                int fullTexW = fullSize[0];
                int fullTexH = fullSize[1];

                // Generate two textures for static images:
                // 1) HUD preview texture (small/supersampled)
                // 2) Preview screen texture (much larger, capped)
                NativeImage scaled = new NativeImage(img.format(), texW, texH, false);
                img.resizeSubRectTo(0, 0, rawW, rawH, scaled);
                NativeImage fullPreview = new NativeImage(img.format(), fullTexW, fullTexH, false);
                img.resizeSubRectTo(0, 0, rawW, rawH, fullPreview);
                entry.setDecodedFormatName(img.format().name());
                img.close();

                int id = TEXTURE_COUNTER.getAndIncrement();
                Identifier location = Identifier.fromNamespaceAndPath(
                        ChatUpgrade.MOD_ID, "upgrade_preview_" + id);

                int finalId = id;
                NativeImage texturePixels = scaled;
                DynamicTexture texture = new DynamicTexture(() -> "upgrade_preview_" + finalId, texturePixels);
                Minecraft.getInstance().getTextureManager().register(location, texture);

                int fullId = TEXTURE_COUNTER.getAndIncrement();
                Identifier fullLocation = Identifier.fromNamespaceAndPath(
                        ChatUpgrade.MOD_ID, "upgrade_preview_full_" + fullId);
                int finalFullId = fullId;
                DynamicTexture fullTexture = new DynamicTexture(() -> "upgrade_preview_full_" + finalFullId, fullPreview);
                Minecraft.getInstance().getTextureManager().register(fullLocation, fullTexture);

                entry.setLoaded(location, fullLocation, displayW, displayH, texW, texH, fullTexW, fullTexH, rawW, rawH);
                notifyCompatLayoutForUrl(url);
                RichChatViewport.invalidateMedia(url);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to register texture for {}: {}", url, e.getMessage());
                markFailed(url, entry);
            }
        });
    }
}
